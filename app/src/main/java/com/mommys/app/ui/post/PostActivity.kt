package com.mommys.app.ui.post

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import android.widget.FrameLayout
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxInterstitialAd
import com.mommys.app.R
import com.mommys.app.data.model.Post
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.databinding.ActivityPostBinding
import com.mommys.app.ui.main.MainActivity
import com.mommys.app.ui.notes.NotesActivity
import com.mommys.app.util.AdManager
import com.mommys.app.util.BlacklistHelper

/**
 * PostActivity - Vista del post individual
 * 
 * Implementado exactamente como la app original:
 * - ViewPager2 para swipe horizontal entre posts
 * - Los posts se pasan pre-cargados via companion object (como la app original)
 * - Botones back flotantes (izquierda/derecha configurables)
 * - Mini preview de imagen al scrollear
 * - Barra inferior con 6 botones: upvote, downvote, favorite, comments, download, more
 */
class PostActivity : AppCompatActivity(), MaxAdListener {

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
    
    companion object {
        private const val TAG = "PostActivity"
        const val EXTRA_POST_ID = "post_id"
        const val EXTRA_INITIAL_POSITION = "initial_position"
        
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
        fun createIntent(context: Context, posts: List<Post>, initialPosition: Int): Intent {
            pendingPosts = posts
            return Intent(context, PostActivity::class.java).apply {
                putExtra(EXTRA_INITIAL_POSITION, initialPosition)
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
        
        preferencesManager = PreferencesManager(this)
        
        // Apply FLAG_SECURE if hideInTasks is enabled (matching original app)
        if (preferencesManager.hideInTasks) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        
        binding = ActivityPostBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        Log.d(TAG, "onCreate started")
        
        viewModel = ViewModelProvider(this)[PostViewModel::class.java]
        
        // Obtener datos del intent
        initialPostId = intent.getIntExtra(EXTRA_POST_ID, -1)
        initialPosition = intent.getIntExtra(EXTRA_INITIAL_POSITION, 0)
        
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
        
        setupAds()
        setupBackButtons()
        setupViewPager()
        setupObservers()
        
        // Si tenemos posts pre-cargados, usarlos directamente
        if (posts.isNotEmpty()) {
            Log.d(TAG, "Using pre-loaded posts: ${posts.size}")
            displayPosts(posts)
        } else if (initialPostId != -1) {
            // Solo fetch cuando viene un ID específico
            Log.d(TAG, "Loading single post: $initialPostId")
            binding.loadingLayout.visibility = View.VISIBLE
            viewModel.loadPost(initialPostId)
        }
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
     * Configurar ViewPager2 para swipe horizontal entre posts
     */
    private fun setupViewPager() {
        pagerAdapter = PostPagerAdapter(
            onTagClick = { tag -> searchByTag(tag) },
            onArtistClick = { artist -> searchByTag(artist) },
            onScrollStateChanged = { isScrolled -> 
                // Mostrar mini preview cuando scrollea hacia abajo
                updateMiniPreview(isScrolled)
            },
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
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    // Pausar todos los players cuando se cambia de página
                    // Como hace la app original en di/b1.java método c (onPageSelected)
                    pagerAdapter.pauseAllPlayers()
                    
                    // Si autoplay está habilitado, reproducir el video de la nueva página
                    // Usar post{} para dar tiempo a que el ViewHolder se bindee correctamente
                    binding.viewPager.post {
                        pagerAdapter.playVideoAtPosition(position)
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
        // Observar posts solo para el caso de carga de un solo post por ID
        viewModel.posts.observe(this) { fetchedPosts ->
            if (fetchedPosts.isNotEmpty() && posts.isEmpty()) {
                Log.d(TAG, "Received posts from ViewModel: ${fetchedPosts.size}")
                displayPosts(fetchedPosts)
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
                    Toast.makeText(this, R.string.action_downloaded, Toast.LENGTH_SHORT).show()
                    // Actualizar UI del botón de descarga
                    pagerAdapter.updateDownloadState(it.postId, true, false)
                } else {
                    val message = it.errorMessage ?: getString(R.string.action_download_failed)
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
        Log.d(TAG, "displayPosts called with ${postsToDisplay.size} posts")
        
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
            if (preferencesManager.blacklistEnabled && BlacklistHelper.isPostBlacklisted(post, preferencesManager)) {
                // Get matching blacklist entries to show in dialog
                val matchingEntries = BlacklistHelper.getMatchingBlacklistEntries(post, preferencesManager)
                showBlacklistDialog(post, matchingEntries)
                return
            }
        }
        
        showPostsInViewPager(postsToDisplay)
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
        posts = postsToDisplay
        pagerAdapter.submitList(postsToDisplay)
        
        // Ir a la posición inicial
        val safePosition = initialPosition.coerceIn(0, postsToDisplay.size - 1)
        if (safePosition > 0) {
            binding.viewPager.setCurrentItem(safePosition, false)
        }
        
        binding.loadingLayout.visibility = View.GONE
        onPostChanged(binding.viewPager.currentItem)
        
        Log.d(TAG, "Posts displayed successfully, current position: ${binding.viewPager.currentItem}")
    }

    /**
     * Cuando cambia el post actual (al hacer swipe)
     */
    private fun onPostChanged(position: Int) {
        val post = pagerAdapter.getPost(position) ?: return
        
        // Actualizar estado de favorito
        viewModel.checkFavoriteStatus(post.id)
        
        // Actualizar mini preview
        updateMiniPreviewImage(post)
    }

    /**
     * Mostrar/ocultar mini preview de imagen cuando scrollea
     */
    private fun updateMiniPreview(show: Boolean) {
        if (show) {
            binding.imgMini.visibility = View.VISIBLE
        } else {
            binding.imgMini.visibility = View.GONE
        }
    }

    /**
     * Cargar imagen en mini preview
     */
    private fun updateMiniPreviewImage(post: Post) {
        val previewUrl = post.preview.url
        if (previewUrl != null) {
            Glide.with(this)
                .load(previewUrl)
                .into(binding.imgMini)
        }
    }

    /**
     * Obtener el post actual del ViewPager
     */
    private fun getCurrentPost(): Post? {
        return pagerAdapter.getPost(binding.viewPager.currentItem)
    }

    /**
     * Buscar por tag - abre nueva MainActivity
     */
    private fun searchByTag(tag: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_SEARCH_QUERY, tag)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    /**
     * Abrir comentarios del post
     * Muestra un BottomSheet con los comentarios
     */
    private fun openComments(post: Post) {
        val bottomSheet = CommentsBottomSheet.newInstance(post.id, post.commentCount)
        bottomSheet.show(supportFragmentManager, "comments")
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
        viewModel.downloadPost(this, post, preferencesManager)
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
                // Como la app original: EditPostActivity.O = mVar;
                // postActivity.startActivityForResult(...)
                // La app original no verifica login aquí, pero como no tenemos
                // EditPostActivity, abrimos en navegador (que pedirá login)
                val url = "${getBaseUrl()}/posts/${action.post.id}/edit"
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                startActivity(intent)
            }
            is PostMenuAction.AddToSet -> {
                // Como la app original: abre BrowseSetsActivity con el post ID
                // postActivity.startActivity(new Intent(...).putExtra("p", i10));
                // r2.p.o(postActivity).z(getString(R.string.set_choose_set_to_add_post_to), true);
                // La app original no verifica login, simplemente abre la activity
                val url = "${getBaseUrl()}/post_sets?post_id=${action.post.id}"
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                startActivity(intent)
                Toast.makeText(this, R.string.set_choose_set_to_add_post_to, Toast.LENGTH_LONG).show()
            }
            is PostMenuAction.ReloadPost -> reloadPost(action.post, action.position)
            is PostMenuAction.CheckNotes -> {
                // Como la app original: NotesActivity.D = mVar;
                // postActivity.startActivity(new Intent(postActivity, NotesActivity.class));
                NotesActivity.currentPost = action.post
                val intent = Intent(this, NotesActivity::class.java)
                startActivity(intent)
            }
        }
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
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
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
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
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
        pagerAdapter.pauseAllPlayers()
        // Restaurar orientación cuando la app pasa a segundo plano
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
        
        pagerAdapter.releaseAllPlayers()
        super.onDestroy()
    }
    
    /**
     * Navegar a un post específico por ID
     * Como la app original (di/f1.java case 8):
     * postActivity.startActivity(new Intent(postActivity, PostActivity.class).putExtra("id", parentId))
     */
    private fun navigateToPost(postId: Int) {
        Log.d(TAG, "Navigating to post: $postId")
        val intent = Intent(this, PostActivity::class.java).apply {
            putExtra(EXTRA_POST_ID, postId)
        }
        startActivity(intent)
    }
    
    /**
     * Navegar a un pool específico por ID
     * Como la app original (va.java case 1):
     * postActivity.startActivity(new Intent(postActivity, PoolActivity.class).putExtra("i", poolId))
     * 
     * Por ahora, abrimos una búsqueda con el tag pool:<id>
     * TODO: Implementar PoolActivity cuando tengamos la pantalla de pools
     */
    private fun navigateToPool(poolId: Int) {
        Log.d(TAG, "Navigating to pool: $poolId")
        // Buscar posts del pool usando el tag pool:<id>
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("search_query", "pool:$poolId order:id")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        Toast.makeText(this, getString(R.string.loading_pool, poolId), Toast.LENGTH_SHORT).show()
    }
}
