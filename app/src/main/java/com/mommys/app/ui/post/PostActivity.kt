package com.mommys.app.ui.post

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.customview.widget.ViewDragHelper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import android.widget.FrameLayout
import com.google.android.material.snackbar.Snackbar
import com.mommys.app.ui.views.SlidingPanelLayout
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxInterstitialAd
import com.mommys.app.MommysApplication
import com.mommys.app.R
import com.mommys.app.data.model.Post
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.databinding.ActivityPostBinding
import com.mommys.app.ui.main.MainActivity
import com.mommys.app.ui.notes.NotesActivity
import com.mommys.app.ui.pool.PoolActivity
import com.mommys.app.data.db.seen.AppSeenDatabase
import com.mommys.app.util.AdManager
import com.mommys.app.util.BlacklistHelper
import com.mommys.app.util.ProgressDownloader
import com.mommys.app.util.observeCloudflareBlocks
import com.mommys.app.util.network.NetworkAwareDispatcher
import com.mommys.app.util.network.NetworkEvent
import com.mommys.app.util.network.NetworkState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * PostActivity - Vista del post individual
 * 
 * Implementado exactamente como la app original:
 * - ViewPager2 para swipe horizontal entre posts
 * - Los posts se pasan pre-cargados via companion object (como la app original)
 * - Botones back flotantes (izquierda/derecha configurables)
 * - Mini preview de imagen al scrollear
 * - Barra inferior con 6 botones: upvote, downvote, favorite, comments, download, more
 * - Retry automático de cargas cuando vuelve la red (NetworkAwareDispatcher.PageRefreshCallback)
 */
class PostActivity : AppCompatActivity(), MaxAdListener, NetworkAwareDispatcher.PageRefreshCallback {

    private lateinit var binding: ActivityPostBinding
    private lateinit var viewModel: PostViewModel
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var pagerAdapter: PostPagerAdapter
    
    private var initialPostId: Int = -1
    private var initialPosition: Int = 0
    private var posts: List<Post> = emptyList()
    
    // Ads - exactamente como di/e1.java en la app original
    private lateinit var adManager: AdManager
    private var adContainer: FrameLayout? = null
    private var bannerEnabled = false
    private var interstitialEnabled = false
    private var interstitialAd: MaxInterstitialAd? = null
    private var retryAttempt = 0
    
    // Post pendiente de descargar (después de obtener permiso)
    private var pendingDownloadPost: Post? = null
    
    // Pull-to-close sliding panel (como la app original PostActivity.java líneas 360-376)
    private var slidingPanel: SlidingPanelLayout? = null
    
    // ===== NETWORK MONITORING =====
    // NetworkMonitor para detectar cambios de conectividad
    private val networkMonitor by lazy { MommysApplication.getInstance().networkMonitor }
    // NetworkAwareDispatcher para retry de acciones fallidas
    private val networkDispatcher by lazy { MommysApplication.getInstance().networkDispatcher }
    // Job para observar el estado de red (banner rojo de offline)
    private var networkStateJob: Job? = null
    // Job para observar eventos de transición real (auto-refresh silencioso)
    private var networkEventsJob: Job? = null
    // Posición del último post que falló por error de red (para retry)
    private var lastFailedPosition: Int = -1
    // Flags de paginación: si hay más páginas disponibles
    private var hasNextPage = true
    private var hasPrevPage = true
    
    companion object {
        private const val TAG = "PostActivity"
        const val EXTRA_POST_ID = "post_id"
        const val EXTRA_INITIAL_POSITION = "initial_position"
        const val EXTRA_HAS_NEXT_PAGE = "has_next_page"
        const val EXTRA_HAS_PREV_PAGE = "has_prev_page"
        
        // Códigos de resultado para paginación (como app original 999/-999)
        const val RESULT_NEXT_PAGE = 999
        const val RESULT_PREV_PAGE = -999
        
        // Código para request de permiso de almacenamiento
        private const val PERMISSION_REQUEST_STORAGE = 100
        private const val PERMISSION_REQUEST_NOTIFICATIONS = 101
        
        // Ad Unit ID para interstitial (exacto como la app original)
        private const val INTERSTITIAL_AD_UNIT_ID = "e5f924dd42848132"
        
        // Tiempo mínimo entre interstitials (2 minutos = 120000ms)
        private const val INTERSTITIAL_COOLDOWN_MS = 120000L
        
        // Key para SharedPreferences del último ad mostrado
        private const val KEY_LAST_AD = "last_ad"
        
        // Posts pre-cargados pasados desde MainActivity (como la app original)
        // Esto evita hacer fetch de cada post individualmente
        @JvmStatic
        var pendingPosts: List<Post>? = null
        
        /**
         * Crear intent con posts pre-cargados (método preferido)
         * Los posts se pasan via companion object como la app original
         */
        fun createIntent(context: Context, posts: List<Post>, initialPosition: Int, hasNextPage: Boolean = true, hasPrevPage: Boolean = true): Intent {
            pendingPosts = posts
            return Intent(context, PostActivity::class.java).apply {
                putExtra(EXTRA_INITIAL_POSITION, initialPosition)
                putExtra(EXTRA_HAS_NEXT_PAGE, hasNextPage)
                putExtra(EXTRA_HAS_PREV_PAGE, hasPrevPage)
            }
        }
        
        /**
         * Crear intent para un solo post por ID
         * Solo se usa cuando se necesita cargar un post específico
         */
        fun createIntent(context: Context, postId: Int): Intent {
            pendingPosts = null
            return Intent(context, PostActivity::class.java).apply {
                putExtra(EXTRA_POST_ID, postId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "=== onCreate START ===")
        Log.d(TAG, "Activity instance: ${this.hashCode()}")
        Log.d(TAG, "savedInstanceState: ${savedInstanceState != null}")
        
        preferencesManager = PreferencesManager(this)

        // Inicializar caché de imágenes para ProgressDownloader
        ProgressDownloader.init(this)
        
        // Apply FLAG_SECURE if hideInTasks is enabled (matching original app)
        if (preferencesManager.hideInTasks) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        
        binding = ActivityPostBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup pull-to-close functionality (como PostActivity.java líneas 360-376)
        setupPullToClose()
        
        Log.d(TAG, "onCreate started")
        
        viewModel = ViewModelProvider(this)[PostViewModel::class.java]
        Log.d(TAG, "ViewModel created: ${viewModel.hashCode()}")
        
        // Handle deep links (e.g., https://e621.net/posts/12345)
        val deepLinkPostId = handleDeepLink()
        Log.d(TAG, "deepLinkPostId: $deepLinkPostId")
        
        // Obtener datos del intent
        initialPostId = deepLinkPostId ?: intent.getIntExtra(EXTRA_POST_ID, -1)
        initialPosition = intent.getIntExtra(EXTRA_INITIAL_POSITION, 0)
        hasNextPage = intent.getBooleanExtra(EXTRA_HAS_NEXT_PAGE, true)
        hasPrevPage = intent.getBooleanExtra(EXTRA_HAS_PREV_PAGE, true)
        
        Log.d(TAG, "initialPostId from intent: $initialPostId")
        Log.d(TAG, "initialPosition from intent: $initialPosition")
        Log.d(TAG, "pendingPosts at read time: ${pendingPosts?.size ?: "null"}")
        
        // Obtener posts pre-cargados (como la app original)
        posts = pendingPosts ?: emptyList()
        pendingPosts = null // Limpiar referencia
        
        Log.d(TAG, "Posts from companion: ${posts.size}, initialPosition: $initialPosition, initialPostId: $initialPostId")
        
        // Si no hay posts y tampoco un ID, cerrar
        if (posts.isEmpty() && initialPostId == -1) {
            Log.e(TAG, "No posts and no postId - finishing")
            finish()
            return
        }
        
        Log.d(TAG, "Setting up ads...")
        setupAds()
        Log.d(TAG, "Setting up back buttons...")
        setupBackButtons()
        Log.d(TAG, "Setting up ViewPager...")
        setupViewPager()
        connectPanelToAdapter()
        Log.d(TAG, "Setting up observers...")
        setupObservers()
        Log.d(TAG, "Setting up network monitoring...")
        setupNetworkMonitoring()
        
        // Mostrar diálogo automático si Cloudflare bloquea la API (HTTP 403/503)
        observeCloudflareBlocks()

        // Si tenemos posts pre-cargados, usarlos directamente
        if (posts.isNotEmpty()) {
            Log.d(TAG, "Using pre-loaded posts: ${posts.size}")
            displayPosts(posts)
        } else if (initialPostId != -1) {
            // Solo fetch cuando viene un ID específico
            Log.d(TAG, "Loading single post by ID: $initialPostId")
            binding.loadingLayout.visibility = View.VISIBLE
            viewModel.loadPost(initialPostId)
        }
        
        Log.d(TAG, "=== onCreate END ===")
    }
    
    /**
     * Handle deep links from URLs like https://e621.net/posts/12345
     * Returns the post ID if found, null otherwise
     */
    private fun handleDeepLink(): Int? {
        val data = intent.data ?: return null
        
        if (intent.action != Intent.ACTION_VIEW) return null
        
        // Parse post ID from path like /posts/12345
        val path = data.path ?: return null
        val postIdMatch = Regex("/posts/(\\d+)").find(path)
        return postIdMatch?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Configurar ads - banner y interstitial
     * Exactamente como di/e1 en la app original
     */
    private fun setupAds() {
        adManager = AdManager(this)
        adContainer = findViewById(R.id.adContainer)
        
        // Banner ads above posts (ads_banner_post3)
        if (preferencesManager.adsBannerPost) {
            adManager.initializeSdk()
            adContainer?.let { adManager.showBannerAd(it, this) }
            bannerEnabled = true
        } else {
            adContainer?.let { adManager.hideBannerAd(it) }
            binding.viewPager.invalidate()
            bannerEnabled = false
        }
        
        // Interstitial ads (ads_interstitial3)
        if (preferencesManager.adsInterstitial) {
            interstitialEnabled = true
            interstitialAd = MaxInterstitialAd(INTERSTITIAL_AD_UNIT_ID, this)
            interstitialAd?.setListener(this)
            interstitialAd?.loadAd()
        }
    }
    
    // MaxAdListener para interstitial - exactamente como di/e1.java
    override fun onAdLoaded(ad: MaxAd) {
        retryAttempt = 0
    }
    
    override fun onAdDisplayed(ad: MaxAd) {}
    
    override fun onAdHidden(ad: MaxAd) {
        // Cargar siguiente ad después de cerrar
        interstitialAd?.loadAd()
    }
    
    override fun onAdClicked(ad: MaxAd) {}
    
    override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
        // Retry con exponential backoff (como la app original)
        retryAttempt++
        val delayMs = java.util.concurrent.TimeUnit.SECONDS.toMillis(
            Math.pow(2.0, Math.min(6, retryAttempt).toDouble()).toLong()
        )
        android.os.Handler(mainLooper).postDelayed({
            interstitialAd?.loadAd()
        }, delayMs)
    }
    
    override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
        // Cargar siguiente ad si falla
        interstitialAd?.loadAd()
    }

    /**
     * Configurar botones back flotantes (como la app original)
     * Posición configurable en preferencias: izquierda o derecha
     */
    private fun setupBackButtons() {
        val backButtonPosition = preferencesManager.backButtonPosition // "left" o "right"
        
        if (backButtonPosition == "right") {
            binding.btnBackLeft.visibility = View.GONE
            binding.btnBackRight.visibility = View.VISIBLE
        } else {
            binding.btnBackLeft.visibility = View.VISIBLE
            binding.btnBackRight.visibility = View.GONE
        }
        
        binding.btnBackLeft.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        
        binding.btnBackRight.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }
    
    /**
     * Configurar pull-to-close - Como PostActivity.java líneas 360-376
     * Permite cerrar el post arrastrando hacia abajo en el 45% superior de la pantalla
     */
    private fun setupPullToClose() {
        // Solo habilitar si la preferencia está activada
        if (!preferencesManager.postPullToClose) {
            return
        }
        
        // Crear configuración del panel (como bf.a en la app original)
        // f2333c = 3 (DIRECTION_BOTTOM)
        // f2331a = true (touchEnabled)
        // f2332b = 0.45f (threshold 45%)
        val config = SlidingPanelLayout.PanelConfig(
            touchEnabled = true,
            touchThreshold = 0.45f,
            direction = SlidingPanelLayout.DIRECTION_BOTTOM
        )
        
        // Obtener el decorView y su primer hijo (el contenido)
        val decorView = window.decorView as ViewGroup
        val contentView = decorView.getChildAt(0)
        
        // Remover el contenido del decorView
        decorView.removeViewAt(0)
        
        // Crear el SlidingPanelLayout y añadir el contenido
        val panel = SlidingPanelLayout(this)
        panel.id = R.id.slidable_panel
        panel.configure(config)
        
        // Establecer ID al contenido
        contentView.id = R.id.slidable_content
        
        // Añadir el contenido al panel
        panel.addView(contentView)
        panel.setContentView(contentView)
        
        // Añadir el panel al decorView
        decorView.addView(panel, 0)
        
        // Configurar listener para eventos del panel
        panel.setOnPanelSlideListener(object : SlidingPanelLayout.OnPanelSlideListener {
            override fun onPanelSlide(slideOffset: Float) {
                // slideOffset: 1.0 = cerrado, 0.0 = completamente abierto
                // Como si.c.n() en la app original - no hace nada especial
            }
            
            override fun onPanelOpened() {
                // Panel volvió a posición original
                // Como si.c.m() en la app original
            }
            
            override fun onPanelClosed() {
                // Panel cerrado - finalizar activity
                // Como si.c.l() en la app original (líneas 278-287)
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
            
            override fun onDragStateChanged(state: Int) {
                // Como si.c.o() en la app original (líneas 300-305)
                // state == 1 significa que está siendo arrastrado
                pagerAdapter.setDragging(state == ViewDragHelper.STATE_DRAGGING)
            }
        })
        
        // Guardar referencia
        slidingPanel = panel
    }

    /**
     * Pasa la referencia del SlidingPanel al adapter para coordinación scroll↔drag
     */
    private fun connectPanelToAdapter() {
        pagerAdapter.setSlidingPanel(slidingPanel)
    }

    /**
     * Configurar ViewPager2 para swipe horizontal entre posts
     */
    private fun setupViewPager() {
        pagerAdapter = PostPagerAdapter(
            onTagClick = { tag -> searchByTag(tag) },
            onArtistClick = { artist -> searchByTag(artist) },

            onVoteUp = { post -> 
                // Votar arriba - como la app original ei/n.java case 1
                viewModel.voteUp(post.id)
            },
            onVoteDown = { post -> 
                // Votar abajo - como la app original ei/n.java case 2
                viewModel.voteDown(post.id)
            },
            onFavorite = { post ->
                // Toggle favorito
                viewModel.toggleFavorite(post.id)
            },
            onComment = { post ->
                // Abrir comentarios
                openComments(post)
            },
            onDownload = { post ->
                // Verificar permisos y descargar
                startDownload(post)
            },
            onMenuAction = { action ->
                // Manejar acciones del menú del post
                handleMenuAction(action)
            },
            onNavigateToPost = { postId ->
                // Navegar a post por ID (parent/children)
                navigateToPost(postId)
            },
            onNavigateToPool = { poolId ->
                // Navegar a pool por ID
                navigateToPool(poolId)
            },
            onNetworkError = { position ->
                // Marcar post como fallido para retry automático cuando vuelva la red
                markPostAsFailed(position)
            }
        )
        
        // Configurar preferencias de video (como app original ei/e0.java)
        pagerAdapter.setVideoPreferences(
            autoPlay = preferencesManager.autoPlayVideos,
            mute = preferencesManager.postMuteVideos,
            fullscreen = preferencesManager.postFullscreenVideos,
            landscape = preferencesManager.postLandscapeVideos,
            quality = preferencesManager.postVideoQuality,
            format = preferencesManager.postVideoFormat
        )
        
        // Configurar preferencias de acciones automáticas
        pagerAdapter.setActionPreferences(
            upvoteOnFav = preferencesManager.postUpvoteOnFav,
            upvoteOnDl = preferencesManager.postUpvoteOnDownload,
            favOnDl = preferencesManager.postFavOnDownload
        )
        
        // Configurar preferencias de compartir
        pagerAdapter.setSharePreferences(
            disableShareOption = preferencesManager.postDisableShare,
            useE621Host = preferencesManager.useE621()
        )
        
        binding.viewPager.apply {
            adapter = pagerAdapter
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 1
            
            // OnPageChangeCallback como en la app original (di/b1.java)
            // Cuando cambia de página, pausar todos los videos
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                // Para detección de overscroll
                private var lastOffsetPixels = -1
                private var isDragging = false
                
                override fun onPageScrollStateChanged(state: Int) {
                    super.onPageScrollStateChanged(state)
                    isDragging = state == ViewPager2.SCROLL_STATE_DRAGGING
                }

                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                    super.onPageScrolled(position, positionOffset, positionOffsetPixels)
                    
                    // Lógica de detección de "swipe past end" (overscroll)
                    // Basado en di/b1.java de la app original
                    if (isDragging && positionOffset == 0f && positionOffsetPixels == 0 && lastOffsetPixels == 0) {
                        if (position == 0 && hasPrevPage) {
                            // Intento de ir a la página anterior
                            setResult(RESULT_PREV_PAGE)
                            finish()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
                            } else {
                                @Suppress("DEPRECATION")
                                overridePendingTransition(0, 0)
                            }
                        } else if (position == 0 && !hasPrevPage) {
                            Toast.makeText(this@PostActivity, R.string.no_previous_page, Toast.LENGTH_SHORT).show()
                            isDragging = false
                        } else if (position == (adapter?.itemCount ?: 0) - 1 && hasNextPage) {
                            // Intento de ir a la página siguiente
                            setResult(RESULT_NEXT_PAGE)
                            finish()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
                            } else {
                                @Suppress("DEPRECATION")
                                overridePendingTransition(0, 0)
                            }
                        } else if (position == (adapter?.itemCount ?: 0) - 1 && !hasNextPage) {
                            Toast.makeText(this@PostActivity, R.string.no_more_pages, Toast.LENGTH_SHORT).show()
                            isDragging = false
                        }
                    }
                    lastOffsetPixels = positionOffsetPixels
                }

                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    // Pausar todos los players cuando se cambia de página
                    // Como hace la app original en di/b1.java método c (onPageSelected)
                    pagerAdapter.pauseAllPlayers()
                    
                    // Buscar el ViewHolder directamente como en m16068J de PostActivity.java (línea 250)
                    // Esto evita desincronización durante swipes rápidos o mientras está cargando
                    val recyclerView = binding.viewPager.getChildAt(0) as? RecyclerView
                    val viewHolder = recyclerView?.findViewHolderForAdapterPosition(position) as? PostPagerAdapter.PostViewHolder
                    
                    // Si autoplay está habilitado y el ViewHolder existe, reproducir video
                    if (viewHolder != null) {
                        pagerAdapter.playVideoInViewHolder(viewHolder)
                    }
                    
                    // Restaurar orientación a portrait cuando se cambia de página
                    // (como hace la app original ei/e0.java método n cuando se sale del video)
                    requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    
                    onPostChanged(position)
                }
            })
        }
    }

    private fun setupObservers() {
        Log.d(TAG, "=== setupObservers START ===")
        
        // Observar posts solo para el caso de carga de un solo post por ID
        viewModel.posts.observe(this) { fetchedPosts ->
            Log.d(TAG, "=== viewModel.posts OBSERVER TRIGGERED ===")
            Log.d(TAG, "fetchedPosts size: ${fetchedPosts.size}")
            Log.d(TAG, "posts (instance variable) size: ${posts.size}")
            Log.d(TAG, "posts.isEmpty(): ${posts.isEmpty()}")
            Log.d(TAG, "fetchedPosts.isNotEmpty(): ${fetchedPosts.isNotEmpty()}")
            Log.d(TAG, "Condition (fetchedPosts.isNotEmpty() && posts.isEmpty()): ${fetchedPosts.isNotEmpty() && posts.isEmpty()}")
            
            if (fetchedPosts.isNotEmpty() && posts.isEmpty()) {
                Log.d(TAG, "Condition passed - calling displayPosts")
                try {
                    displayPosts(fetchedPosts)
                } catch (e: Exception) {
                    Log.e(TAG, "ERROR in displayPosts from observer: ${e.message}", e)
                    e.printStackTrace()
                }
            } else {
                Log.d(TAG, "Condition NOT passed - NOT calling displayPosts")
            }
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            binding.loadingLayout.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        viewModel.isFavorite.observe(this) { isFavorite ->
            // El icono de favorito ahora se maneja en el item del ViewPager
            // TODO: Comunicar el cambio de estado al adapter si es necesario
        }
        
        viewModel.favoriteMessage.observe(this) { messageRes ->
            messageRes?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.clearFavoriteMessage()
            }
        }
        
        // Observer para mensajes de votos
        viewModel.voteMessage.observe(this) { messageRes ->
            messageRes?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.clearVoteMessage()
            }
        }
        
        // Observer para resultados de voto - actualiza el adaptador
        viewModel.voteResult.observe(this) { result ->
            result?.let {
                pagerAdapter.updateVoteState(it.postId, it.success, it.newState)
                viewModel.clearVoteResult()
            }
        }
        
        // Observer para resultados de favorito - actualiza el adaptador
        viewModel.favoriteResult.observe(this) { result ->
            result?.let {
                pagerAdapter.updateFavoriteState(it.postId, it.success, it.newState == 1)
                viewModel.clearFavoriteResult()
            }
        }
        
        // Observer para resultados de descarga
        viewModel.downloadResult.observe(this) { result ->
            result?.let {
                if (it.success) {
                    Snackbar.make(binding.root, R.string.action_downloaded, Snackbar.LENGTH_SHORT).show()
                    // Actualizar UI del botón de descarga
                    pagerAdapter.updateDownloadState(it.postId, true, false)
                } else {
                    val message = it.errorMessage ?: getString(R.string.action_download_failed)
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                    // Restaurar botón
                    pagerAdapter.updateDownloadState(it.postId, false, false)
                }
                viewModel.clearDownloadResult()
            }
        }
        
        // Observer para progreso de descarga
        viewModel.downloadProgress.observe(this) { progress ->
            progress?.let {
                pagerAdapter.updateDownloadProgress(it.postId, it.progress, it.isDownloading)
            }
        }
        
        // Observer para errores
        viewModel.error.observe(this) { errorMsg ->
            errorMsg?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Mostrar los posts en el ViewPager
     * Like di/w0.java in the original app - checks blacklist before showing
     */
    private fun displayPosts(postsToDisplay: List<Post>) {
        Log.d(TAG, "=== displayPosts START ===")
        Log.d(TAG, "displayPosts called with ${postsToDisplay.size} posts")
        Log.d(TAG, "pagerAdapter initialized: ${::pagerAdapter.isInitialized}")
        
        if (postsToDisplay.isEmpty()) {
            Log.e(TAG, "No posts to display!")
            Toast.makeText(this, R.string.no_results, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Check if this is a single post that might be blacklisted
        // Like di/w0.java - if opening a single post by ID, check blacklist and show dialog
        if (postsToDisplay.size == 1 && initialPostId != -1) {
            val post = postsToDisplay[0]
            Log.d(TAG, "Single post mode, checking blacklist for post ${post.id}")
            if (preferencesManager.blacklistEnabled && BlacklistHelper.isPostBlacklisted(post, preferencesManager)) {
                // Get matching blacklist entries to show in dialog
                val matchingEntries = BlacklistHelper.getMatchingBlacklistEntries(post, preferencesManager)
                Log.d(TAG, "Post is blacklisted, showing dialog")
                showBlacklistDialog(post, matchingEntries)
                return
            }
        }
        
        Log.d(TAG, "Calling showPostsInViewPager")
        try {
            showPostsInViewPager(postsToDisplay)
        } catch (e: Exception) {
            Log.e(TAG, "ERROR in showPostsInViewPager: ${e.message}", e)
            e.printStackTrace()
        }
        Log.d(TAG, "=== displayPosts END ===")
    }
    
    /**
     * Show dialog when a post is blacklisted
     * Like di/w0.java and wa/b (AlertDialog builder) in the original app
     */
    private fun showBlacklistDialog(post: Post, matchingEntries: List<String>) {
        val entriesText = matchingEntries.joinToString("\n")
        val message = getString(R.string.post_blacklisted_dialog_message, entriesText)
        
        AlertDialog.Builder(this)
            .setTitle(R.string.post_blacklisted_dialog_title)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { _, _ ->
                // User wants to see the post anyway
                showPostsInViewPager(listOf(post))
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                // User cancelled, close activity
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Actually show posts in the ViewPager after any checks
     */
    private fun showPostsInViewPager(postsToDisplay: List<Post>) {
        Log.d(TAG, "=== showPostsInViewPager START ===")
        Log.d(TAG, "postsToDisplay size: ${postsToDisplay.size}")
        Log.d(TAG, "pagerAdapter initialized: ${::pagerAdapter.isInitialized}")
        
        try {
            posts = postsToDisplay
            Log.d(TAG, "posts assigned, size: ${posts.size}")
            
            pagerAdapter.submitList(postsToDisplay)
            Log.d(TAG, "submitList called on pagerAdapter")
            
            // Ir a la posición inicial
            val safePosition = initialPosition.coerceIn(0, postsToDisplay.size - 1)
            Log.d(TAG, "safePosition: $safePosition (from initialPosition: $initialPosition)")
            
            if (safePosition > 0) {
                binding.viewPager.setCurrentItem(safePosition, false)
                Log.d(TAG, "setCurrentItem called with position: $safePosition")
            }
            
            binding.loadingLayout.visibility = View.GONE
            Log.d(TAG, "loadingLayout hidden")
            
            onPostChanged(binding.viewPager.currentItem)
            Log.d(TAG, "onPostChanged called")
            
            Log.d(TAG, "Posts displayed successfully, current position: ${binding.viewPager.currentItem}")
        } catch (e: Exception) {
            Log.e(TAG, "ERROR in showPostsInViewPager: ${e.message}", e)
            e.printStackTrace()
        }
        
        Log.d(TAG, "=== showPostsInViewPager END ===")
    }

    /**
     * Cuando cambia el post actual (al hacer swipe)
     * Como di/b1.java método c (onPageSelected) en la app original:
     * - Marca el post como visto (post.isSeen = true)
     * - Guarda en la base de datos para persistencia
     */
    private fun onPostChanged(position: Int) {
        val post = pagerAdapter.getPost(position) ?: return
        
        // Marcar el post como visto en el objeto (como mVar.f18022c = true en la app original)
        // Esto es lo que permite que al volver al grid, el icono de "nuevo" desaparezca
        // porque el objeto Post es el MISMO que está en el grid de MainActivity
        post.isSeen = true
        
        // También guardar en la base de datos para persistencia entre sesiones
        // (como new Thread(new w0(postActivity, mVar, 1)).start() en la app original)
        AppSeenDatabase.markSeen(this, post.id)
        
        // Actualizar estado de favorito
        viewModel.checkFavoriteStatus(post.id)
        
    }

    /**
     * Obtener el post actual del ViewPager
     */
    private fun getCurrentPost(): Post? {
        return pagerAdapter.getPost(binding.viewPager.currentItem)
    }

    /**
     * Buscar por tag - abre nueva MainActivity.
     *
     * Replica el comportamiento de la app original (op0.java:74-78):
     * si la preferencia `search_in_new_task` está activa (default true),
     * abre la nueva búsqueda en una TASK separada del multitarea, para que
     * el Back en ella no recorra el histórico de la task original.
     * Los flags usados son:
     *  - FLAG_ACTIVITY_NEW_TASK (0x08000000 = 134217728)
     *  - FLAG_ACTIVITY_NEW_DOCUMENT (0x00080000 = 524288, era CLEAR_WHEN_TASK_RESET)
     */
    private fun searchByTag(tag: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_SEARCH_QUERY, tag)
        }
        if (MommysApplication.getInstance().preferencesManager.searchInNewTask) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        }
        startActivity(intent)
    }

    /**
     * Abrir comentarios del post
     * Abre CommentsActivity con los comentarios del post
     */
    private fun openComments(post: Post) {
        val intent = com.mommys.app.ui.comments.CommentsActivity.newIntent(this, post)
        startActivity(intent)
    }

    /**
     * Iniciar descarga del post
     * Verifica permisos primero (Android < 10 necesita WRITE_EXTERNAL_STORAGE)
     * Como la app original ei/p.java case 0
     */
    private fun startDownload(post: Post) {
        // Primero verificar permiso de notificaciones en Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Solicitar permiso de notificaciones
                pendingDownloadPost = post
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_REQUEST_NOTIFICATIONS
                )
                return
            }
        }
        
        // Android 10+ no necesita permisos de storage (usa MediaStore)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            performDownload(post)
            return
        }
        
        // Android 9 y menor: verificar permiso WRITE_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            performDownload(post)
        } else {
            // Guardar post pendiente y solicitar permiso
            pendingDownloadPost = post
            requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_STORAGE
            )
        }
    }
    
    /**
     * Realizar la descarga después de verificar permisos
     */
    private fun performDownload(post: Post) {
        Toast.makeText(this, R.string.action_downloading, Toast.LENGTH_SHORT).show()
        
        // Usar ForegroundService para que la descarga sobreviva en segundo plano
        com.mommys.app.service.DownloadForegroundService.enqueueDownload(this, post)
        
        // Auto-acciones inmediatas (no dependen de que termine la descarga)
        if (preferencesManager.postUpvoteOnDownload) {
            viewModel.voteUp(post.id)
        }
        if (preferencesManager.postFavOnDownload) {
            viewModel.toggleFavorite(post.id)
        }
    }
    
    /**
     * Callback cuando el usuario responde al diálogo de permisos
     */
    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            PERMISSION_REQUEST_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permiso concedido, descargar el post pendiente
                    pendingDownloadPost?.let { post ->
                        performDownload(post)
                    }
                } else {
                    // Permiso denegado
                    Toast.makeText(
                        this,
                        R.string.permission_required_storage,
                        Toast.LENGTH_LONG
                    ).show()
                }
                pendingDownloadPost = null
            }
            PERMISSION_REQUEST_NOTIFICATIONS -> {
                // Continuar con la descarga independientemente del resultado
                // Las notificaciones son opcionales
                pendingDownloadPost?.let { post ->
                    // Verificar permiso de storage si es necesario
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        if (ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            requestPermissions(
                                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                PERMISSION_REQUEST_STORAGE
                            )
                            return
                        }
                    }
                    performDownload(post)
                }
                pendingDownloadPost = null
            }
        }
    }

    /**
     * Mostrar menú de más opciones
     */
    private fun showMoreOptions(post: Post) {
        val items = arrayOf(
            getString(R.string.share),
            getString(R.string.copy) + " ID",
            getString(R.string.copy) + " URL"
        )
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> sharePost(post)
                    1 -> copyToClipboard("Post ID", post.id.toString())
                    2 -> copyToClipboard("Post URL", "https://e926.net/posts/${post.id}")
                }
            }
            .show()
    }

    /**
     * Compartir post
     */
    private fun sharePost(post: Post) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://e926.net/posts/${post.id}")
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_post)))
    }

    /**
     * Copiar texto al portapapeles
     */
    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    // ==================== MENU ACTIONS ====================
    
    /**
     * Manejar acciones del menú de post
     * Como la app original com/applovin/impl/s9.java
     */
    private fun handleMenuAction(action: PostMenuAction) {
        when (action) {
            is PostMenuAction.Slideshow -> showSlideshowDialog()
            is PostMenuAction.EditPost -> {
                // Verificar si está logueado
                if (!preferencesManager.isLoggedIn()) {
                    Toast.makeText(this, R.string.post_menu_edit_post_not_logged_in, Toast.LENGTH_SHORT).show()
                    return
                }
                // Set current post and open EditPostActivity
                com.mommys.app.ui.edit.EditPostActivity.currentPost = action.post
                val intent = Intent(this, com.mommys.app.ui.edit.EditPostActivity::class.java)
                intent.putExtra(com.mommys.app.ui.edit.EditPostActivity.EXTRA_POST_ID, action.post.id)
                startActivity(intent)
            }
            is PostMenuAction.AddToSet -> {
                // Verificar si está logueado
                if (!preferencesManager.isLoggedIn()) {
                    Toast.makeText(this, R.string.set_not_logged_in, Toast.LENGTH_SHORT).show()
                    return
                }
                // Abrir BrowseSetsActivity en modo selección
                val intent = Intent(this, com.mommys.app.ui.sets.BrowseSetsActivity::class.java)
                intent.putExtra(com.mommys.app.ui.sets.BrowseSetsActivity.EXTRA_SELECT_MODE, true)
                intent.putExtra(com.mommys.app.ui.sets.BrowseSetsActivity.EXTRA_POST_ID, action.post.id)
                startActivity(intent)
                Toast.makeText(this, R.string.set_choose_set_to_add_post_to, Toast.LENGTH_SHORT).show()
            }
            is PostMenuAction.ReloadPost -> reloadPost(action.post, action.position)
            is PostMenuAction.CheckNotes -> {
                // Como la app original: NotesActivity.D = mVar;
                // postActivity.startActivity(new Intent(postActivity, NotesActivity.class));
                NotesActivity.currentPost = action.post
                val intent = Intent(this, NotesActivity::class.java)
                startActivity(intent)
            }
            is PostMenuAction.ViewWiki -> {
                // Show dialog to select which tag to view wiki for
                showWikiTagSelectionDialog(action.post)
            }
            is PostMenuAction.FlagPost -> {
                // Verificar si está logueado
                if (!preferencesManager.isLoggedIn()) {
                    Toast.makeText(this, R.string.action_not_logged_in, Toast.LENGTH_SHORT).show()
                    return
                }
                // Open flag page in WebView
                val host = preferencesManager.getHost()
                val url = "https://$host/posts/${action.post.id}/flag"
                val intent = Intent(this, com.mommys.app.ui.webview.WebViewActivity::class.java)
                intent.putExtra(com.mommys.app.ui.webview.WebViewActivity.EXTRA_URL, url)
                startActivity(intent)
            }
        }
    }
    
    /**
     * Muestra diálogo para seleccionar tag y ver su wiki
     */
    private fun showWikiTagSelectionDialog(post: Post) {
        val allTags = mutableListOf<String>()
        post.tags?.let { tags ->
            tags.artist?.let { allTags.addAll(it) }
            tags.character?.let { allTags.addAll(it) }
            tags.copyright?.let { allTags.addAll(it) }
            tags.species?.let { allTags.addAll(it) }
            tags.general?.let { allTags.addAll(it) }
            tags.lore?.let { allTags.addAll(it) }
            tags.meta?.let { allTags.addAll(it) }
        }
        
        if (allTags.isEmpty()) {
            Toast.makeText(this, "No tags available", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Sort alphabetically
        allTags.sort()
        
        AlertDialog.Builder(this)
            .setTitle(R.string.post_menu_view_wiki)
            .setItems(allTags.toTypedArray()) { _, which ->
                val selectedTag = allTags[which]
                // Open WikiShowActivity
                val intent = Intent(this, com.mommys.app.ui.wiki.WikiShowActivity::class.java)
                intent.putExtra(com.mommys.app.ui.wiki.WikiShowActivity.EXTRA_TAG, selectedTag)
                startActivity(intent)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    /**
     * Obtiene la URL base según la preferencia del usuario
     * Como la app original: dVar.z() retorna si usa e621
     */
    private fun getBaseUrl(): String {
        return if (preferencesManager.useE621()) "https://e621.net" else "https://e926.net"
    }
    
    // ==================== SLIDESHOW ====================
    
    private var slideshowRunnable: Runnable? = null
    private var slideshowHandler: android.os.Handler? = null
    private var slideshowActive = false
    private var slideshowSettings: SlideshowSettings? = null
    
    data class SlideshowSettings(
        val durationSeconds: Int,
        val fullscreen: Boolean,
        val backwards: Boolean,
        val repeatPage: Boolean
    )
    
    /**
     * Muestra el diálogo de configuración del slideshow
     * Como la app original com/applovin/impl/s9.java líneas 204-248
     */
    private fun showSlideshowDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_slideshow, null)
        val txtSeekbarText = dialogView.findViewById<android.widget.TextView>(R.id.txtSeekbarText)
        val seekBar = dialogView.findViewById<android.widget.SeekBar>(R.id.seekbarDuration)
        val cbFullscreen = dialogView.findViewById<android.widget.CheckBox>(R.id.cbFullscreen)
        val cbBackwards = dialogView.findViewById<android.widget.CheckBox>(R.id.cbBackwards)
        val cbRepeatPage = dialogView.findViewById<android.widget.CheckBox>(R.id.cbRepeatPage)
        
        // Cargar valores guardados
        val prefs = preferencesManager.getSharedPreferences()
        val savedDuration = prefs.getInt("ss_dur", 5).coerceIn(1, 15)
        seekBar.progress = savedDuration
        cbFullscreen.isChecked = prefs.getBoolean("ss_ful", true)
        cbBackwards.isChecked = prefs.getBoolean("ss_bac", false)
        cbRepeatPage.isChecked = prefs.getBoolean("ss_rep", false)
        
        // Actualizar texto inicial
        txtSeekbarText.text = getString(R.string.ss_slider_text, savedDuration)
        
        // Listener para el seekbar
        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val value = if (progress < 1) 1 else progress
                txtSeekbarText.text = getString(R.string.ss_slider_text, value)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.ss_start) { _, _ ->
                val duration = seekBar.progress.coerceIn(1, 15)
                val fullscreen = cbFullscreen.isChecked
                val backwards = cbBackwards.isChecked
                val repeat = cbRepeatPage.isChecked
                
                // Guardar preferencias
                prefs.edit()
                    .putInt("ss_dur", duration)
                    .putBoolean("ss_ful", fullscreen)
                    .putBoolean("ss_bac", backwards)
                    .putBoolean("ss_rep", repeat)
                    .apply()
                
                // Iniciar slideshow
                startSlideshow(SlideshowSettings(duration, fullscreen, backwards, repeat))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    /**
     * Iniciar el slideshow
     * Como la app original PostActivity.L()
     */
    private fun startSlideshow(settings: SlideshowSettings) {
        slideshowSettings = settings
        slideshowActive = true
        slideshowHandler = android.os.Handler(mainLooper)
        
        // Entrar en fullscreen si está habilitado
        if (settings.fullscreen) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.hide(android.view.WindowInsets.Type.statusBars())
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
            binding.btnBackLeft.visibility = View.GONE
            binding.btnBackRight.visibility = View.GONE
        }
        
        Toast.makeText(this, R.string.ss_toast_started, Toast.LENGTH_SHORT).show()
        
        // Programar cambio de slide
        scheduleNextSlide()
    }
    
    /**
     * Programar el siguiente slide
     */
    private fun scheduleNextSlide() {
        val settings = slideshowSettings ?: return
        if (!slideshowActive) return
        
        slideshowRunnable = Runnable {
            if (!slideshowActive) return@Runnable
            
            val currentPos = binding.viewPager.currentItem
            val totalPosts = posts.size
            
            if (totalPosts <= 1) {
                stopSlideshow()
                return@Runnable
            }
            
            val nextPos = if (settings.backwards) {
                if (currentPos <= 0) {
                    if (settings.repeatPage) totalPosts - 1 else {
                        stopSlideshow()
                        return@Runnable
                    }
                } else currentPos - 1
            } else {
                if (currentPos >= totalPosts - 1) {
                    if (settings.repeatPage) 0 else {
                        stopSlideshow()
                        return@Runnable
                    }
                } else currentPos + 1
            }
            
            binding.viewPager.setCurrentItem(nextPos, true)
            scheduleNextSlide()
        }
        
        slideshowHandler?.postDelayed(slideshowRunnable!!, settings.durationSeconds * 1000L)
    }
    
    /**
     * Detener el slideshow
     */
    private fun stopSlideshow() {
        if (!slideshowActive) return
        
        slideshowActive = false
        slideshowRunnable?.let { slideshowHandler?.removeCallbacks(it) }
        slideshowRunnable = null
        slideshowHandler = null
        slideshowSettings = null
        
        // Restaurar UI
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(android.view.WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        setupBackButtons()
        
        Toast.makeText(this, R.string.ss_toast_stopped, Toast.LENGTH_SHORT).show()
    }
    
    // ==================== RELOAD POST ====================
    
    /**
     * Recargar un post específico
     * Como la app original com/applovin/impl/s9.java reload_post
     */
    private fun reloadPost(post: Post, position: Int) {
        Toast.makeText(this, getString(R.string.post_menu_reload_post) + "...", Toast.LENGTH_SHORT).show()
        
        // Recargar el post desde el API
        viewModel.reloadPost(post.id) { reloadedPost ->
            if (reloadedPost != null) {
                // Actualizar en la lista (crear nueva lista con el post actualizado)
                posts = posts.toMutableList().apply {
                    if (position in indices) {
                        this[position] = reloadedPost
                    }
                }
                pagerAdapter.notifyItemChanged(position)
                Toast.makeText(this, "Post reloaded", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to reload post", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Pausar todos los videos cuando la activity entra en pausa
     * Como hace la app original en PostActivity.java línea 618 (onPause)
     */
    override fun onPause() {
        super.onPause()
        if (::pagerAdapter.isInitialized) {
            pagerAdapter.pauseAllPlayers()
        }
        // Restaurar orientación cuando la app pasa a segundo plano
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    
    /**
     * Reinicializar el video cuando la activity vuelve a primer plano
     * Esto soluciona el error de MediaCodec que ocurre cuando Android libera
     * recursos del codec mientras la app está en segundo plano
     */
    override fun onResume() {
        super.onResume()
        // Solo reinicializar si el pagerAdapter está inicializado
        if (::pagerAdapter.isInitialized) {
            val currentPosition = binding.viewPager.currentItem
            // Reinicializar el video en la posición actual si es necesario
            pagerAdapter.reinitializeVideoAtPosition(currentPosition)
        }
    }
    
    /**
     * Override onBackPressed para detener el slideshow
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (slideshowActive) {
            stopSlideshow()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    /**
     * Liberar todos los players cuando la activity se destruye
     * y mostrar interstitial si está listo (como la app original)
     */
    override fun onDestroy() {
        // Cancelar observación de red
        networkStateJob?.cancel()    // Banner de estado (offline)
        networkEventsJob?.cancel()   // Eventos de transición (auto-refresh)
        
        // Mostrar interstitial si está habilitado y listo
        // Exactamente como PostActivity.java líneas 560-580
        if (interstitialEnabled && interstitialAd != null) {
            try {
                if (interstitialAd!!.isReady) {
                    val currentTime = System.currentTimeMillis()
                    val lastAdTime = preferencesManager.getSharedPreferences()
                        .getLong(KEY_LAST_AD, 0L)
                    
                    // Solo mostrar si han pasado más de 2 minutos desde el último ad
                    if (currentTime - lastAdTime > INTERSTITIAL_COOLDOWN_MS) {
                        preferencesManager.getSharedPreferences().edit()
                            .putLong(KEY_LAST_AD, currentTime)
                            .apply()
                        interstitialAd!!.showAd(this)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Verificar si pagerAdapter está inicializado antes de acceder
        // Esto previene crash si la Activity hace finish() antes de inicializar el adapter
        if (::pagerAdapter.isInitialized) {
            pagerAdapter.releaseAllPlayers()
        }
        super.onDestroy()
    }
    
    /**
     * Navegar a un post específico por ID
     * Como la app original (di/f1.java case 8):
     * postActivity.startActivity(new Intent(postActivity, PostActivity.class).putExtra("id", parentId))
     */
    private fun navigateToPost(postId: Int) {
        Log.d(TAG, "=== NAVIGATE TO POST START ===")
        Log.d(TAG, "Navigating to post: $postId")
        Log.d(TAG, "Current activity: ${this.hashCode()}")
        Log.d(TAG, "Current posts count: ${posts.size}")
        Log.d(TAG, "pendingPosts before clear: ${pendingPosts?.size ?: "null"}")
        
        try {
            // Limpiar pendingPosts explícitamente para evitar que la nueva Activity los use
            pendingPosts = null
            Log.d(TAG, "pendingPosts cleared to null")
            
            val intent = Intent(this, PostActivity::class.java).apply {
                putExtra(EXTRA_POST_ID, postId)
            }
            Log.d(TAG, "Intent created with postId: $postId")
            startActivity(intent)
            Log.d(TAG, "startActivity called successfully")
        } catch (e: Exception) {
            Log.e(TAG, "ERROR in navigateToPost: ${e.message}", e)
            e.printStackTrace()
        }
        Log.d(TAG, "=== NAVIGATE TO POST END ===")
    }
    
    /**
     * Navegar a un pool específico por ID
     * Como la app original:
     * postActivity.startActivity(new Intent(postActivity, PoolActivity.class).putExtra("i", poolId))
     */
    private fun navigateToPool(poolId: Int) {
        Log.d(TAG, "Navigating to pool: $poolId")
        startActivity(PoolActivity.newIntent(this, poolId))
    }
    
    // ==================== NETWORK MONITORING ====================
    // Mismo fix que MainActivity: dos suscripciones independientes.
    //
    // 1) networkStateJob: observa el estado actual (para el banner rojo de offline).
    //    StateFlow reemite al volver de background, pero eso es lo que queremos
    //    para el banner (que refleje siempre la verdad).
    //
    // 2) networkEventsJob: observa EVENTOS de transición real via SharedFlow(replay=0).
    //    NO reemite al volver de background. Solo dispara auto-refresh silencioso
    //    (reintentar post fallido) cuando hubo una desconexión REAL.
    //
    // Sin snackbar verde (la app original no lo hace y saturaba al usuario).

    private fun setupNetworkMonitoring() {
        // 1) Banner de estado: rojo solo cuando realmente no hay red
        networkStateJob = lifecycleScope.launch {
            networkMonitor.networkState.collectLatest { state ->
                updateNetworkStatusUI(state)
            }
        }
        // 2) Reintento silencioso de post fallido en transiciones reales offline->online
        networkEventsJob = lifecycleScope.launch {
            networkMonitor.networkEvents.collect { event ->
                when (event) {
                    NetworkEvent.BecameOnline -> {
                        // Reintentar posts individuales marcados como fallidos
                        networkDispatcher.flushFailedActions()
                    }
                    NetworkEvent.BecameOffline -> {
                        // Nada: el banner ya lo pinta networkStateJob.
                    }
                }
            }
        }
    }

    /**
     * Actualiza la UI del banner de estado de red.
     * Solo muestra/oculta el banner rojo de "sin conexión".
     * Sin banner de conexión lenta (no aporta).
     */
    private fun updateNetworkStatusUI(state: NetworkState) {
        val txtNetworkStatus = binding.root.findViewById<android.widget.TextView>(
            com.mommys.app.R.id.txtNetworkStatus
        ) ?: return

        if (!state.isOnline) {
            txtNetworkStatus.visibility = android.view.View.VISIBLE
            txtNetworkStatus.text = getString(com.mommys.app.R.string.network_offline)
            txtNetworkStatus.setBackgroundColor(
                ContextCompat.getColor(this, com.mommys.app.R.color.error_background)
            )
            txtNetworkStatus.setCompoundDrawablesWithIntrinsicBounds(
                com.mommys.app.R.drawable.ic_wifi_off, 0, 0, 0
            )
            txtNetworkStatus.compoundDrawablePadding = 16
        } else {
            txtNetworkStatus.visibility = android.view.View.GONE
        }
    }

    /**
     * Reintenta la carga del post actual
     * Notifica al adapter que debe recargar la vista en la posición actual
     */
    private fun retryCurrentPost() {
        val currentPosition = binding.viewPager.currentItem
        if (currentPosition >= 0 && currentPosition < posts.size) {
            // Notificar al adapter que recargue esta posición
            pagerAdapter.notifyItemChanged(currentPosition)
            Log.d(TAG, "Retrying load for position: $currentPosition")
        }
    }
    
    /**
     * Marca que el post en la posición dada falló por error de red
     * Para ser llamado desde PostPagerAdapter cuando detecta error de red
     */
    fun markPostAsFailed(position: Int) {
        lastFailedPosition = position
        // Marcar para replay en el dispatcher
        val post = posts.getOrNull(position)
        if (post != null) {
            networkDispatcher.markForReplay(
                id = "post_${post.id}",
                action = { retryCurrentPost() },
                supportsReplay = true
            )
        }
    }
    
    /**
     * Implementación de NetworkAwareDispatcher.PageRefreshCallback
     * Llamado cuando el dispatcher detecta que hay acciones pendientes
     * y la conexión se ha restablecido
     */
    override fun onRefreshRequested() {
        runOnUiThread {
            // Si hay un post que falló, reintentarlo
            if (lastFailedPosition >= 0) {
                retryCurrentPost()
                lastFailedPosition = -1
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        // Registrar este Activity como callback para auto-refresh cuando vuelve la red
        networkDispatcher.registerPageRefreshCallback(this)
    }
    
    override fun onStop() {
        super.onStop()
        // Desregistrar callback para evitar leaks y refreshes cuando no está visible
        networkDispatcher.unregisterPageRefreshCallback(this)
        
        // Liberar TODOS los ExoPlayers cuando la Activity deja de ser visible
        // Esto es CRÍTICO para evitar agotamiento de MediaCodec cuando el usuario
        // abre múltiples Activities via searchByTag() con FLAG_ACTIVITY_NEW_TASK.
        // onDestroy() NO se llama para Activities en el back stack, así que si solo
        // liberamos ahí, los codecs se acumulan (límite sistema ~16-32 instancias)
        // y eventualmente aparece "video not supported on this device".
        if (::pagerAdapter.isInitialized) {
            pagerAdapter.releaseAllPlayers()
        }
    }
}
