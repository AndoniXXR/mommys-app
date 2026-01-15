package com.mommys.app.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.View
import android.widget.EditText
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import com.mommys.app.MommysApplication
import com.mommys.app.R
import com.mommys.app.data.api.ApiClient
import com.mommys.app.data.api.ApiService
import com.mommys.app.data.db.seen.AppSeenDatabase
import com.mommys.app.data.model.Post
import com.mommys.app.data.search.SearchHelper
import com.mommys.app.databinding.ActivityMainBinding
import com.mommys.app.service.FollowingJobService
import com.mommys.app.ui.about.AboutActivity
import com.mommys.app.ui.browse.BrowsePoolsActivity
import com.mommys.app.ui.browse.BrowseTagsActivity
import com.mommys.app.ui.downloads.DownloadManagerActivity
import com.mommys.app.ui.following.FollowingPostActivity
import com.mommys.app.ui.post.PostActivity
import com.mommys.app.ui.popular.PopularActivity
import com.mommys.app.ui.saved.SavedSearchesActivity
import com.mommys.app.ui.settings.SettingsActivity
import com.mommys.app.ui.views.MySearchView
import com.mommys.app.ui.views.SearchSuggestionsAdapter
import com.mommys.app.util.AdManager
import com.mommys.app.util.UpdateManager
import com.mommys.app.util.network.NetworkAwareDispatcher
import com.mommys.app.util.network.NetworkMonitor
import com.mommys.app.util.network.NetworkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedHashSet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * MainActivity - Pantalla principal con ViewPager2 horizontal para páginas
 * Similar a se.zepiwolf.tws.MainActivity
 * 
 * Usa un sistema de PageHandlers donde cada página mantiene su propio estado.
 * Implementa SelectionCallback para manejar selección múltiple de posts.
 * Implementa NetworkAwareDispatcher.PageRefreshCallback para auto-refresh cuando vuelve la red.
 */
class MainActivity : AppCompatActivity(), SwipeRefreshLayout.OnRefreshListener, SelectionCallback,
    NetworkAwareDispatcher.PageRefreshCallback {
    
    companion object {
        const val EXTRA_SEARCH_QUERY = "search_query"
    }
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var pagesAdapter: PagesAdapter
    private lateinit var api: ApiService
    private lateinit var searchHelper: SearchHelper
    private lateinit var suggestionsAdapter: SearchSuggestionsAdapter
    
    private val prefs by lazy { MommysApplication.getInstance().preferencesManager }
    
    // Lista de PageHandlers - cada uno maneja una página
    private val pageHandlers = mutableListOf<PageHandler>()
    
    // Tags de búsqueda actual
    private var currentTags: List<String> = emptyList()
    
    // Página actual (1-indexed, para mostrar al usuario)
    private var currentPageDisplay = 1
    
    // Total de páginas conocidas
    private var totalPages: AtomicInteger = AtomicInteger(0)
    
    // Control de páginas preparadas
    private var pagesCreated: AtomicInteger = AtomicInteger(0)
    
    // Si estamos añadiendo páginas
    private val isAddingPages: AtomicBoolean = AtomicBoolean(false)
    
    // Página a la que ir después de cargar
    private var pendingPageJump = 0
    
    // Posts por página (de preferencias, default 75)
    private val postsPerPage: Int get() = prefs.postsPerPage
    
    // Máximo de páginas a crear
    private val maxPages = 750
    
    // Job para debounce de sugerencias
    private var suggestionJob: Job? = null
    private val suggestionDebounceMs = 200L
    
    // Control para "presiona otra vez para salir" (como la app original)
    private var backPressedOnce = false
    
    // AdManager para mostrar anuncios banner (como vi.b en la app original)
    private lateinit var adManager: AdManager
    
    // Contenedor del banner de ads
    private var adContainer: FrameLayout? = null
    
    // ===== SELECCIÓN MÚLTIPLE (como this.T, this.U en app original) =====
    // LinkedHashSet de posts seleccionados (como this.T = new LinkedHashSet())
    private val selectedPosts = LinkedHashSet<Post>()
    // Lock para sincronización (como synchronized(this.U) en app original)
    private val selectionLock = Any()
    // Tipo de menú actual: 0=normal, 1=selección
    private var currentMenuType = 0
    
    // Control de si los ads están habilitados actualmente
    private var adsEnabled = true
    
    // Permission launcher for notification permission (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, schedule Following job if enabled
            if (prefs.followingEnabled) {
                FollowingJobService.schedule(this)
            }
        }
        // Mark that we've asked for permission
        prefs.notificationPermissionAsked = true
    }
    
    // Host actual para detectar cambios (como f31254n0 en app original)
    // Cuando cambia el host en Settings, necesitamos recargar los posts
    private var currentHost: String = ""
    
    // ===== NETWORK MONITORING (como ff/b.java en app original) =====
    // NetworkMonitor para detectar cambios de conectividad
    private val networkMonitor by lazy { MommysApplication.getInstance().networkMonitor }
    // NetworkAwareDispatcher para retry de acciones fallidas
    private val networkDispatcher by lazy { MommysApplication.getInstance().networkDispatcher }
    // Flag para detectar reconexión (si estaba offline y ahora online)
    private var wasOffline = false
    // Job para cancelar la observación cuando se destruye la activity
    private var networkObserverJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply FLAG_SECURE if hideInTasks is enabled (matching original app)
        if (prefs.hideInTasks) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        api = ApiClient.getApiService()
        searchHelper = SearchHelper(this)
        
        // Guardar el host actual para detectar cambios en onResume
        currentHost = prefs.getHost()
        
        setupViews()
        setupViewPager()
        setupSearchView()
        setupBottomNavigation()
        setupBackPressHandler()
        
        // Inicializar y configurar ads (como la app original en líneas 867, 947-953)
        setupAds()
        
        // Cargar filtros guardados desde preferencias (como la app original)
        loadSavedFilters()
        
        // Verificar si venimos de SavedSearchesActivity con tags
        handleIncomingIntent(intent)
        
        // Request notification permission on first launch (Android 13+)
        requestNotificationPermissionIfNeeded()
        
        // Check for updates on startup (once per day)
        checkForUpdatesOnStartup()
        
        // Configurar monitoreo de red (como ff/b.java en app original)
        setupNetworkMonitoring()
    }
    
    /**
     * Request notification permission on Android 13+ if not already asked
     * This is needed for Following notifications to work
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Check if we've already asked
            if (!prefs.notificationPermissionAsked) {
                // Check if permission is not granted
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // Request permission
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // Permission already granted, schedule job if enabled
                    prefs.notificationPermissionAsked = true
                    if (prefs.followingEnabled) {
                        FollowingJobService.schedule(this)
                    }
                }
            } else {
                // Already asked, just schedule if enabled and permission granted
                if (prefs.followingEnabled && ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    FollowingJobService.schedule(this)
                }
            }
        } else {
            // Pre-Android 13, no permission needed
            if (prefs.followingEnabled) {
                FollowingJobService.schedule(this)
            }
        }
    }
    
    /**
     * Configura el manejo del botón back como la app original:
     * - Si el SearchView tiene foco, quitarlo primero
     * - Si hay activities debajo (no es raíz), volver a la anterior
     * - Si es la activity raíz: "presiona otra vez para salir"
     */
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Si el popup de info está visible, cerrarlo primero
                val fLInfo = findViewById<FrameLayout>(R.id.fLInfo)
                if (fLInfo != null && fLInfo.visibility == View.VISIBLE) {
                    hidePostInfo()
                    return
                }
                
                // Si el SearchView tiene foco, quitarlo primero
                if (binding.searchView.hasFocus()) {
                    binding.searchView.clearFocus()
                    // Ocultar teclado
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(binding.searchView.windowToken, 0)
                    return
                }
                
                // Si NO es la actividad raíz, simplemente cerrar y volver a la anterior
                // (como la app original - cada búsqueda es una nueva activity)
                if (!isTaskRoot) {
                    prefs.setLastSearch(currentTags.joinToString(" "))
                    prefs.setLastSearchPage(binding.viewPager.currentItem + 1)
                    finish()
                    return
                }
                
                // Es la activity raíz - comportamiento: presiona otra vez para salir
                if (backPressedOnce) {
                    // Segunda vez - guardar estado y salir completamente
                    prefs.setLastSearch(currentTags.joinToString(" "))
                    prefs.setLastSearchPage(binding.viewPager.currentItem + 1)
                    finishAffinity() // Cierra toda la app
                } else {
                    // Primera vez - mostrar mensaje
                    backPressedOnce = true
                    Toast.makeText(this@MainActivity, R.string.main_back_again_to_exit, Toast.LENGTH_SHORT).show()
                    
                    // Resetear después de 2 segundos
                    binding.root.postDelayed({
                        backPressedOnce = false
                    }, 2000)
                }
            }
        })
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }
    
    /**
     * Maneja intent entrante (desde SavedSearchesActivity, PostActivity u otro lugar)
     */
    private fun handleIncomingIntent(intent: Intent?) {
        // Handle deep links first (e.g., https://e621.net/posts?tags=wolf)
        val deepLinkTags = handleDeepLink(intent)
        
        // Primero verificar search_query (usado desde PostActivity para pools)
        val searchQuery = intent?.getStringExtra(EXTRA_SEARCH_QUERY)
        val tags = deepLinkTags ?: intent?.getStringExtra("tags")
        val page = intent?.getIntExtra("page", 1) ?: 1
        
        when {
            !searchQuery.isNullOrEmpty() -> {
                // Búsqueda directa (pools, etc.)
                binding.searchView.setQuery(searchQuery, false)
                pendingPageJump = 1
                performSearch(searchQuery)
            }
            !tags.isNullOrEmpty() -> {
                // Ejecutar búsqueda con los tags recibidos
                binding.searchView.setQuery(tags, false)
                pendingPageJump = page
                performSearch(tags)
            }
            pageHandlers.isEmpty() -> {
                // Si no hay intent y no hay páginas, iniciar búsqueda normal
                startNewSearch(emptyList())
            }
        }
    }
    
    /**
     * Handle deep links like https://e621.net/posts?tags=wolf
     * Returns the tags query parameter if present
     */
    private fun handleDeepLink(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        
        // Get tags from query parameter
        return data.getQueryParameter("tags")
    }
    
    /**
     * Carga los filtros guardados en SharedPreferences
     * Similar a como la app original persiste filtros entre sesiones
     */
    private fun loadSavedFilters() {
        val ratingBits = prefs.getFilterRating()  // bitwise: 1=e, 2=q, 4=s, 7=all
        val orderType = prefs.getFilterOrder()    // 0=newest, 1=oldest, 2=score, 3=favs
        val typeFilter = prefs.getFilterType()    // 0=all, 1=images, 2=videos, 3=gifs
        
        // Convertir rating bits a nuestro formato
        // Si es 7 (all), usar 0 (sin filtro)
        // Si es 4 (s), usar 1
        // Si es 2 (q), usar 2
        // Si es 1 (e), usar 3
        currentRatingFilter = when {
            ratingBits == 7 -> 0 // all
            (ratingBits and 4) != 0 && (ratingBits and 3) == 0 -> 1 // solo s
            (ratingBits and 2) != 0 && (ratingBits and 5) == 0 -> 2 // solo q
            (ratingBits and 1) != 0 && (ratingBits and 6) == 0 -> 3 // solo e
            else -> 0 // combinación, tratar como all
        }
        
        currentOrderType = orderType
        currentTypeFilter = typeFilter
    }
    
    /**
     * Configura los anuncios banner en el grid principal
     * Exactamente como la app original en MainActivity líneas 867, 947-953:
     * - Crea el AdManager (como k0Var2.f13792c = new vi.b(this))
     * - Obtiene el adContainer
     * - Verifica preferencia ads_banner_grid3
     * - Si está habilitado: inicializa SDK y muestra banner
     * - Si está deshabilitado: oculta el contenedor
     * 
     * EXACTAMENTE como la app original en onCreate (líneas 947-955):
     * k0Var.f13793d = (FrameLayout) findViewById(R.id.adContainer);
     * if (k0Var.f23503a.f24892f0.v().getBoolean("ads_banner_grid3", true)) {
     *     k0Var.f13792c.b();
     *     k0Var.f13792c.c(k0Var.f13793d, k0Var.f23503a);
     *     k0Var.f13794e = false;
     * } else {
     *     k0Var.f13794e = true;
     *     k0Var.f13792c.a(k0Var.f13793d);
     * }
     */
    private fun setupAds() {
        // Crear el AdManager (como k0Var2.f13792c = new vi.b(this))
        adManager = AdManager(this)
        
        // Obtener el contenedor del banner (como k0Var.f13793d = findViewById(R.id.adContainer))
        adContainer = findViewById(R.id.adContainer)
        
        // Verificar preferencia ads_banner_grid3 (exactamente como la app original)
        if (prefs.adsBannerGrid) {
            // k0Var.f13792c.b() + k0Var.f13792c.c(...)
            adManager.initializeSdk()
            adContainer?.let { adManager.showBannerAd(it, this) }
            adsEnabled = true
        } else {
            // k0Var.f13792c.a(k0Var.f13793d)
            adContainer?.let { adManager.hideBannerAd(it) }
            adsEnabled = false
        }
    }
    
    /**
     * Actualiza la visibilidad del banner de ads cuando cambia la preferencia
     * Llamar desde onResume() para sincronizar después de cambios en Settings
     * 
     * EXACTAMENTE como la app original en onResume (líneas 1040-1048):
     * if (k0Var.f23503a.f24892f0.v().getBoolean("ads_banner_grid3", true)) {
     *     k0Var.f13792c.b();
     *     k0Var.f13792c.c(k0Var.f13793d, k0Var.f23503a);
     *     k0Var.f13794e = false;
     * } else {
     *     k0Var.f13794e = true;
     *     k0Var.f13792c.a(k0Var.f13793d);
     * }
     */
    private fun updateAdsVisibility() {
        if (!::adManager.isInitialized) return
        
        adContainer?.let { container ->
            if (prefs.adsBannerGrid) {
                // Ads habilitados - inicializar y mostrar (como la app original)
                adManager.initializeSdk()
                adManager.showBannerAd(container, this)
                adsEnabled = true
            } else {
                // Ads deshabilitados - ocultar (como la app original)
                adManager.hideBannerAd(container)
                adsEnabled = false
            }
        }
    }
    
    private fun setupViews() {
        // SwipeRefreshLayout - habilitar basado en preferencia grid_refresh
        binding.swipeRefreshLayout.isEnabled = prefs.gridRefresh
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.colorAccent,
            R.color.colorPrimary
        )
        
        // Menú button - mostrar PopupMenu con todas las opciones
        binding.btnMenu.setOnClickListener {
            showMainMenu()
        }
        
        // Botón de búsqueda (lupa) - mismo comportamiento que Enter en el teclado
        binding.btnSearch.setOnClickListener {
            executeSearch()
        }
        
        // Click en número de página para ir a página específica
        binding.txtPageNr.setOnClickListener {
            showGoToPageDialog()
        }
    }
    
    /**
     * Ejecuta la búsqueda con el texto actual del SearchView
     * Si está vacío, va a la página 1
     */
    private fun executeSearch() {
        val query = binding.searchView.query?.toString()?.trim() ?: ""
        binding.searchView.clearFocus()
        
        // Ocultar teclado
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchView.windowToken, 0)
        
        if (query.isNotEmpty()) {
            // Nueva búsqueda = nueva Activity
            openNewSearch(query)
        } else {
            // Query vacío = ir a página 1
            performSearch("")
        }
    }
    
    private fun setupViewPager() {
        val gridColumns = prefs.getGridColumns()
        
        pagesAdapter = PagesAdapter(pageHandlers, binding.viewPager)
        
        binding.viewPager.apply {
            adapter = pagesAdapter
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 2
            
            // Deshabilitar swipe si grid_navigate está activado (como app original)
            isUserInputEnabled = !prefs.gridNavigate
            
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    currentPageDisplay = position + 1
                    updatePageNumber()
                    
                    // Añadir más páginas si llegamos cerca del final
                    // Similar a di/i0.java de la app original
                    if (position >= pageHandlers.size - 2 && !isAddingPages.get()) {
                        // Verificar la última página para saber si debemos añadir más
                        // Solo añadir si la última página:
                        // 1. Ya terminó de cargar (!isLoading)
                        // 2. No es la última página (tiene posts completos)
                        val lastHandler = pageHandlers.lastOrNull()
                        if (lastHandler != null && !lastHandler.isLoading && !lastHandler.isLastPage) {
                            addMorePages(pageHandlers.size + 1, 3)
                        }
                    }
                }
            })
        }
    }
    
    private fun setupSearchView() {
        // Crear adaptador de sugerencias
        suggestionsAdapter = SearchSuggestionsAdapter(
            context = this,
            onSuggestionClick = { query ->
                // Ejecutar búsqueda con el query completo
                binding.searchView.setQuery(query, true)
            },
            onInsertClick = { text ->
                // Insertar texto en el SearchView sin ejecutar
                val currentQuery = binding.searchView.query?.toString() ?: ""
                val words = currentQuery.trim().split("\\s+".toRegex()).toMutableList()
                if (words.isNotEmpty() && words.last().isNotEmpty()) {
                    words[words.lastIndex] = text
                } else {
                    words.add(text)
                }
                val newQuery = words.joinToString(" ") + " "
                binding.searchView.setQuery(newQuery, false)
            }
        )
        
        // Configurar SearchView
        binding.searchView.apply {
            // Configurar listeners
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    val trimmedQuery = query?.trim() ?: ""
                    clearFocus()
                    
                    // Comportamiento como app original: nueva búsqueda = nueva Activity
                    // Esto permite usar Back para volver a la búsqueda anterior
                    if (trimmedQuery.isNotEmpty()) {
                        openNewSearch(trimmedQuery)
                    } else {
                        // Query vacío = recargar página actual
                        performSearch("")
                    }
                    return true
                }
                
                override fun onQueryTextChange(newText: String?): Boolean {
                    // Cancelar job anterior
                    suggestionJob?.cancel()
                    
                    // Debounce para no hacer demasiadas queries
                    suggestionJob = CoroutineScope(Dispatchers.Main).launch {
                        delay(suggestionDebounceMs)
                        updateSuggestions(newText ?: "")
                    }
                    return true
                }
            })
            
            // Cuando el SearchView obtiene foco, mostrar sugerencias
            setOnQueryTextFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    CoroutineScope(Dispatchers.Main).launch {
                        updateSuggestions(query?.toString() ?: "")
                    }
                }
            }
            
            // Configurar para que muestre sugerencias
            setIconifiedByDefault(false)
            isSubmitButtonEnabled = false
        }
        
        // Configurar el AutoCompleteTextView interno del SearchView
        try {
            val searchAutoComplete = binding.searchView.findViewById<androidx.appcompat.widget.SearchView.SearchAutoComplete>(
                androidx.appcompat.R.id.search_src_text
            )
            searchAutoComplete?.apply {
                setAdapter(suggestionsAdapter)
                threshold = 0 // Mostrar sugerencias desde el primer caracter
                
                setOnItemClickListener { _, _, position, _ ->
                    suggestionsAdapter.getSuggestionQuery(position)?.let { query ->
                        binding.searchView.setQuery(query, true)
                    }
                }
                
                // Como la app original: cuando el usuario presiona "Ir/Search" en el teclado,
                // simular click en el botón de la lupa. Esto asegura que el comportamiento
                // sea idéntico para query vacío (Android no llama onQueryTextSubmit si está vacío)
                setOnEditorActionListener { _, _, _ ->
                    binding.btnSearch.performClick()
                    true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Actualiza las sugerencias del SearchView
     */
    private fun updateSuggestions(query: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cursor = searchHelper.getSuggestionsCursor(query)
                withContext(Dispatchers.Main) {
                    suggestionsAdapter.changeCursor(cursor)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun setupBottomNavigation() {
        // No tener ningún item seleccionado por defecto
        binding.bottomNav.menu.setGroupCheckable(0, true, false)
        for (i in 0 until binding.bottomNav.menu.size()) {
            binding.bottomNav.menu.getItem(i).isChecked = false
        }
        
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_saved_searches -> {
                    // Abrir actividad de búsquedas guardadas (como la app original)
                    startActivity(Intent(this, SavedSearchesActivity::class.java))
                    // Deseleccionar el item inmediatamente
                    item.isChecked = false
                    false
                }
                R.id.nav_filter -> {
                    // Mostrar menú de filtros anclado al btnMenu (esquina superior derecha)
                    showFilterMenu()
                    // Deseleccionar el item inmediatamente
                    item.isChecked = false
                    false
                }
                R.id.nav_favourites -> {
                    // Ir a favoritos del usuario - requiere login (abre nueva activity)
                    if (prefs.isLoggedIn()) {
                        openNewSearch("fav:${prefs.getUsername()}")
                    } else {
                        // Mostrar mensaje que requiere login
                        Toast.makeText(this, R.string.favourites_login_required, Toast.LENGTH_SHORT).show()
                    }
                    // Deseleccionar el item inmediatamente
                    item.isChecked = false
                    false
                }
                else -> false
            }
        }
    }
    
    // Estado actual de filtros (ordenamiento LOCAL por página)
    private var currentRatingFilter = 0    // 0=all, 1=s, 2=q, 3=e
    private var currentTypeFilter = 0      // 0=all, 1=images, 2=videos, 3=gifs
    private var currentOrderType = 0       // 0=newest, 1=oldest, 2=score, 3=favcount
    
    /**
     * Abre una nueva MainActivity con los tags especificados.
     * Comportamiento como la app original: cada búsqueda abre una nueva activity
     * para poder usar Back y volver a la búsqueda anterior.
     */
    private fun openNewSearch(tags: String, page: Int = 1) {
        // Guardar en historial
        if (tags.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                searchHelper.saveToHistory(tags)
            }
        }
        
        // Abrir nueva MainActivity con los tags
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("tags", tags)
            putExtra("page", page)
        }
        startActivity(intent)
    }
    
    /**
     * Ejecuta una búsqueda con el texto dado EN ESTA ACTIVITY.
     * Usado cuando se recibe un intent con tags, no para nuevas búsquedas del usuario.
     */
    private fun performSearch(query: String) {
        val tags = query.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        
        // Guardar en historial de base de datos si hay tags
        if (tags.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                searchHelper.saveToHistory(query)
            }
        }
        
        startNewSearch(tags)
    }
    
    /**
     * Muestra el menú de filtros y ordenación anclado al btnMenu (esquina superior derecha)
     */
    private fun showFilterMenu() {
        // Anclar al btnMenu del toolbar (esquina superior derecha) como la app original
        val popup = android.widget.PopupMenu(this, binding.btnMenu)
        popup.menuInflater.inflate(R.menu.menu_filter, popup.menu)
        
        // Configurar estados actuales de los checkmarks
        updateFilterMenuState(popup.menu)
        
        popup.setOnMenuItemClickListener { item ->
            handleFilterMenuItem(item.itemId)
        }
        
        popup.show()
    }
    
    private fun updateFilterMenuState(menu: android.view.Menu) {
        // Marcar los items según el estado actual de filtros
        
        // Rating
        menu.findItem(R.id.filter_rating_s)?.isChecked = (currentRatingFilter == 1)
        menu.findItem(R.id.filter_rating_q)?.isChecked = (currentRatingFilter == 2)
        menu.findItem(R.id.filter_rating_e)?.isChecked = (currentRatingFilter == 3)
        
        // Tipo
        menu.findItem(R.id.filter_type_all)?.isChecked = (currentTypeFilter == 0)
        menu.findItem(R.id.filter_type_images)?.isChecked = (currentTypeFilter == 1)
        menu.findItem(R.id.filter_type_videos)?.isChecked = (currentTypeFilter == 2)
        menu.findItem(R.id.filter_type_gifs)?.isChecked = (currentTypeFilter == 3)
        
        // Ordenación
        menu.findItem(R.id.filter_order_by_newest)?.isChecked = (currentOrderType == 0)
        menu.findItem(R.id.filter_order_by_oldest)?.isChecked = (currentOrderType == 1)
        menu.findItem(R.id.filter_order_by_score)?.isChecked = (currentOrderType == 2)
        menu.findItem(R.id.filter_order_by_favs)?.isChecked = (currentOrderType == 3)
    }
    
    private fun handleFilterMenuItem(itemId: Int): Boolean {
        when (itemId) {
            // Rating - filtrado LOCAL y guardar en preferencias
            R.id.filter_rating_s -> { 
                currentRatingFilter = 1
                prefs.setFilterRating(showSafe = true, showQuestionable = false, showExplicit = false)
                applyLocalFiltersToAllPages()
            }
            R.id.filter_rating_q -> { 
                currentRatingFilter = 2
                prefs.setFilterRating(showSafe = false, showQuestionable = true, showExplicit = false)
                applyLocalFiltersToAllPages()
            }
            R.id.filter_rating_e -> { 
                currentRatingFilter = 3
                prefs.setFilterRating(showSafe = false, showQuestionable = false, showExplicit = true)
                applyLocalFiltersToAllPages()
            }
            
            // Tipo - filtrado LOCAL y guardar en preferencias
            R.id.filter_type_all -> { 
                currentTypeFilter = 0
                prefs.setFilterType(0)
                applyLocalFiltersToAllPages()
            }
            R.id.filter_type_images -> { 
                currentTypeFilter = 1
                prefs.setFilterType(1)
                applyLocalFiltersToAllPages()
            }
            R.id.filter_type_videos -> { 
                currentTypeFilter = 2
                prefs.setFilterType(2)
                applyLocalFiltersToAllPages()
            }
            R.id.filter_type_gifs -> { 
                currentTypeFilter = 3
                prefs.setFilterType(3)
                applyLocalFiltersToAllPages()
            }
            
            // Ordenación LOCAL por página y guardar en preferencias
            R.id.filter_order_by_newest -> { 
                currentOrderType = 0
                prefs.setFilterOrder(0)
                applyLocalFiltersToAllPages()
            }
            R.id.filter_order_by_oldest -> { 
                currentOrderType = 1
                prefs.setFilterOrder(1)
                applyLocalFiltersToAllPages()
            }
            R.id.filter_order_by_score -> { 
                currentOrderType = 2
                prefs.setFilterOrder(2)
                applyLocalFiltersToAllPages()
            }
            R.id.filter_order_by_favs -> { 
                currentOrderType = 3
                prefs.setFilterOrder(3)
                applyLocalFiltersToAllPages()
            }
            
            else -> return false
        }
        return true
    }
    
    /**
     * Aplica filtros y ordenamiento LOCAL a TODAS las páginas cargadas
     * Como la app original que aplica a todos los PageHandlers (k1.java línea ~420)
     */
    private fun applyLocalFiltersToAllPages() {
        for (handler in pageHandlers) {
            handler.applyFiltersAndSort(currentRatingFilter, currentTypeFilter, currentOrderType)
        }
    }
    
    /**
     * Aplica filtros y ordenamiento LOCAL a la página actual
     * El ordenamiento es POR PÁGINA, no global (como la app original)
     */
    private fun applyLocalFilters() {
        val currentPosition = binding.viewPager.currentItem
        if (currentPosition < pageHandlers.size) {
            val handler = pageHandlers[currentPosition]
            handler.applyFiltersAndSort(currentRatingFilter, currentTypeFilter, currentOrderType)
        }
    }
    
    /**
     * Inicia una nueva búsqueda - limpia todo y empieza desde la página 1
     */
    private fun startNewSearch(tags: List<String>) {
        currentTags = tags
        
        // Limpiar páginas existentes
        val oldSize = pageHandlers.size
        pageHandlers.clear()
        if (oldSize > 0) {
            pagesAdapter.notifyPagesChanged()
        }
        
        pagesCreated.set(0)
        totalPages.set(0)
        currentPageDisplay = 1
        pendingPageJump = 0
        
        binding.progressBar.visibility = View.VISIBLE
        
        // Crear solo la primera página
        // Se añadirán más dinámicamente después de que esta cargue
        addMorePages(1, 1)
    }
    
    /**
     * Añade más páginas a partir de startPage
     * Similar a F() de la app original
     */
    private fun addMorePages(startPage: Int, count: Int) {
        if (count <= 0 || isAddingPages.get()) return
        if (startPage > maxPages) return
        
        // Verificar si la última página ya indica que no hay más resultados
        // Similar a la verificación !hVar.c() en di/i0.java de la app original
        if (pageHandlers.isNotEmpty()) {
            val lastHandler = pageHandlers.last()
            
            // c() en la app original: posts.isEmpty() && isLastPage
            // No añadir más si la última página indica que no hay más posts
            if (lastHandler.isEmptyAndLast()) {
                return
            }
            
            // Si la última página es la última (menos posts que el límite), no añadir más
            if (lastHandler.isLastPage) {
                return
            }
        }
        
        // Verificar que no excedamos el máximo
        val actualCount = minOf(count, maxPages - startPage + 1)
        if (actualCount <= 0) return
        
        if (!isAddingPages.compareAndSet(false, true)) return
        
        val gridColumns = prefs.getGridColumns()
        val startPosition = pageHandlers.size
        
        // Crear los PageHandlers
        for (i in 0 until actualCount) {
            val pageNumber = startPage + i
            if (pageNumber > maxPages) break
            
            val handler = PageHandler(
                context = this,
                pageNumber = pageNumber,
                gridColumns = gridColumns,
                prefs = prefs,
                onPostClick = { post ->
                    openPost(post)
                },
                onLoadPage = { pageNum ->
                    loadPageData(pageNum)
                },
                selectionCallback = this,  // Pasar this como SelectionCallback
                onInfoClick = { post ->
                    showPostInfo(post)
                }
            )
            pageHandlers.add(handler)
        }
        
        val insertedCount = pageHandlers.size - startPosition
        if (insertedCount > 0) {
            runOnUiThread {
                pagesAdapter.notifyPagesInserted(startPosition, insertedCount)
                
                // Si hay una página pendiente para navegar
                if (pendingPageJump > 0 && pendingPageJump <= pageHandlers.size) {
                    binding.viewPager.setCurrentItem(pendingPageJump - 1, false)
                    pendingPageJump = 0
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
        
        isAddingPages.set(false)
    }
    
    /**
     * Carga los datos de una página desde la API
     */
    private fun loadPageData(pageNumber: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tagsString = currentTags.joinToString(" ")
                
                // Detectar si es búsqueda de favoritos propios
                // Si es "fav:username" del usuario actual y gridFavOrder está activo,
                // usar el endpoint /favorites.json que ordena por fecha de favorito
                val username = prefs.getUsername()
                val isOwnFavorites = username != null && 
                    currentTags.size == 1 && 
                    currentTags[0].equals("fav:$username", ignoreCase = true) &&
                    prefs.gridFavOrder
                
                val response = if (isOwnFavorites) {
                    // Usar endpoint de favoritos que ordena por fecha de favorito (más recientes primero)
                    api.getFavorites(
                        page = pageNumber,
                        limit = postsPerPage
                    )
                } else {
                    api.getPosts(
                        tags = tagsString.ifEmpty { null },
                        page = pageNumber,
                        limit = postsPerPage
                    )
                }
                
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                    
                    // Encontrar el PageHandler correspondiente
                    val handler = pageHandlers.find { it.pageNumber == pageNumber }
                    if (handler != null) {
                        if (response.isSuccessful) {
                            val posts = response.body()?.posts ?: emptyList()
                            // Pasar el límite para detectar si es la última página
                            handler.onPostsReceived(posts, postsPerPage)
                            // Aplicar filtros guardados a la página recién cargada
                            handler.applyFiltersAndSort(currentRatingFilter, currentTypeFilter, currentOrderType)
                            
                            // IMPORTANTE: Después de cargar, añadir más páginas si NO es la última
                            // Esto es similar a cómo la app original añade páginas en di/i0.java
                            // Solo añadir si esta página tiene posts y no es la última
                            if (!handler.isLastPage && posts.isNotEmpty()) {
                                // Añadir páginas para offscreenPageLimit + buffer
                                val neededPages = (binding.viewPager.offscreenPageLimit + 2) - (pageHandlers.size - pageNumber)
                                if (neededPages > 0) {
                                    addMorePages(pageHandlers.size + 1, neededPages)
                                }
                            }
                        } else {
                            handler.onError("Error: ${response.code()}")
                        }
                        updatePageNumber()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                    
                    val handler = pageHandlers.find { it.pageNumber == pageNumber }
                    handler?.onError(e.message ?: getString(R.string.error_generic))
                }
            }
        }
    }
    
    private fun openPost(post: Post) {
        // NO marcar aquí como visto - ahora se hace en PostActivity.onPostChanged()
        // cuando el usuario realmente VE el post (como la app original di/b1.java)
        
        // Obtener todos los posts de la página actual para permitir swipe entre ellos
        // (como la app original que pasa los posts via variable estática)
        val currentPage = binding.viewPager.currentItem
        val currentHandler = pageHandlers.getOrNull(currentPage)
        val currentPosts = currentHandler?.getCurrentPosts() ?: emptyList()
        
        if (currentPosts.isNotEmpty()) {
            // Encontrar la posición del post clickeado
            val position = currentPosts.indexOfFirst { it.id == post.id }.coerceAtLeast(0)
            
            // Pasar posts pre-cargados como la app original
            val intent = PostActivity.createIntent(this, currentPosts, position)
            startActivity(intent)
        } else {
            // Fallback: abrir solo ese post
            val intent = PostActivity.createIntent(this, post.id)
            startActivity(intent)
        }
    }
    
    private fun updatePageNumber() {
        val total = totalPages.get()
        val totalStr = if (total > 0) total.toString() else "?"
        
        // Obtener datos de la página actual
        val currentIndex = binding.viewPager.currentItem
        val currentHandler = if (currentIndex in pageHandlers.indices) pageHandlers[currentIndex] else null
        val hiddenCount = currentHandler?.hiddenPostsCount ?: 0
        val blacklistedCount = currentHandler?.blacklistedPostsCount ?: 0
        val postsCount = currentHandler?.posts?.size ?: 0
        
        // Calcular rango de posts (ej: 1-75, 76-150, etc.)
        // Similar a como la app original lo muestra: p1/?: 1-75
        val startPost = (currentPageDisplay - 1) * postsPerPage + 1
        val endPost = startPost + postsCount - 1
        
        // Formato como la app original:
        // Sin ocultos: "p1/?: 1-75"
        // Con blacklisted: "p1/?: 1-75 (5 blacklisted)"
        // Con hidden: "p1/?: 1-75 (5H)"
        // Con ambos: "p1/?: 1-75 (5 blacklisted, 3H)"
        val totalHiddenOrBlacklisted = hiddenCount + blacklistedCount
        
        if (totalHiddenOrBlacklisted > 0) {
            // Construir mensaje de estado
            val statusParts = mutableListOf<String>()
            if (blacklistedCount > 0) {
                statusParts.add("$blacklistedCount blacklisted")
            }
            if (hiddenCount > 0) {
                statusParts.add("${hiddenCount}H")
            }
            val statusStr = statusParts.joinToString(", ")
            
            // Mostrar con contador de posts ocultos/blacklisted
            binding.txtPageNr.text = getString(
                R.string.main_page_nr_range_hidden,
                currentPageDisplay,
                totalStr,
                startPost,
                if (postsCount > 0) endPost else startPost,
                totalHiddenOrBlacklisted
            )
            
            // Mostrar también el TextView con mensaje completo como la app original
            binding.txtHiddenPosts.visibility = View.VISIBLE
            binding.txtHiddenPosts.text = when {
                blacklistedCount > 0 && hiddenCount > 0 -> 
                    "$blacklistedCount blacklisted, $hiddenCount restricted"
                blacklistedCount > 0 -> 
                    getString(R.string.main_blacklisted_posts, blacklistedCount)
                else -> 
                    getString(R.string.main_hidden_posts_restricted, hiddenCount)
            }
        } else {
            // Sin posts ocultos
            binding.txtPageNr.text = getString(
                R.string.main_page_nr_range,
                currentPageDisplay,
                totalStr,
                startPost,
                if (postsCount > 0) endPost else startPost
            )
            binding.txtHiddenPosts.visibility = View.GONE
        }
    }
    
    private fun showGoToPageDialog() {
        // Obtener página actual y última conocida
        val currentPage = binding.viewPager.currentItem + 1
        val lastPage = if (totalPages.get() > 0) totalPages.get() else maxPages
        
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.go_to_page_hint)
        }
        
        // Mensaje con página actual y última como la app original
        val message = getString(R.string.go_to_page_message, currentPage, lastPage)
        
        AlertDialog.Builder(this)
            .setTitle(R.string.go_to_page_title)
            .setMessage(message)
            .setView(input)
            .setPositiveButton(R.string.go) { _, _ ->
                val pageStr = input.text.toString()
                val page = pageStr.toIntOrNull()
                if (page != null && page in 1..lastPage) {
                    goToPage(page)
                } else {
                    Toast.makeText(
                        this, 
                        getString(R.string.go_to_page_error_range, lastPage), 
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun goToPage(page: Int) {
        if (page <= pageHandlers.size) {
            // La página ya existe, navegar directamente
            binding.viewPager.setCurrentItem(page - 1, false)
        } else {
            // Necesitamos crear más páginas
            binding.progressBar.visibility = View.VISIBLE
            pendingPageJump = page
            
            // Añadir páginas hasta llegar a la deseada
            val neededPages = page - pageHandlers.size + 5
            addMorePages(pageHandlers.size + 1, neededPages)
        }
    }
    
    /**
     * Muestra el menú principal (hamburguesa) con todas las opciones
     * Similar a menu_main.xml de la app original
     */
    private fun showMainMenu() {
        val popup = android.widget.PopupMenu(this, binding.btnMenu)
        popup.menuInflater.inflate(R.menu.menu_main, popup.menu)
        
        // Actualizar visibilidad de grupos según si está logueado
        val isLoggedIn = prefs.isLoggedIn()
        popup.menu.setGroupVisible(R.id.groupNotLoggedIn, !isLoggedIn)
        popup.menu.setGroupVisible(R.id.groupLoggedIn, isLoggedIn)
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                // Login
                R.id.login -> {
                    startActivity(Intent(this, com.mommys.app.ui.login.LoginActivity::class.java))
                    true
                }
                
                // My Account submenu
                R.id.my_profile -> {
                    // Abrir ProfileActivity con el usuario logueado
                    startActivity(Intent(this, com.mommys.app.ui.profile.ProfileActivity::class.java))
                    true
                }
                R.id.my_favourites -> {
                    // Abrir nueva activity con favoritos (como app original)
                    val username = prefs.getUsername()
                    if (username != null) {
                        openNewSearch("fav:$username")
                    }
                    true
                }
                R.id.my_upvotes -> {
                    // Abrir nueva activity con upvotes (como app original)
                    val username = prefs.getUsername()
                    if (username != null) {
                        openNewSearch("votedup:$username")
                    }
                    true
                }
                R.id.my_posts -> {
                    // Abrir nueva activity con posts del usuario (como app original)
                    val username = prefs.getUsername()
                    if (username != null) {
                        openNewSearch("user:$username")
                    }
                    true
                }
                R.id.logout -> {
                    prefs.logout()
                    Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.my_comments -> {
                    // Open user's comments - search using commenter:username
                    val username = prefs.getUsername()
                    if (username != null) {
                        // Open WebViewActivity pointing to user's comments page
                        val host = prefs.getHost()
                        val url = "https://$host/comments?group_by=comment&search[creator_name]=$username"
                        val intent = Intent(this, com.mommys.app.ui.webview.WebViewActivity::class.java)
                        intent.putExtra(com.mommys.app.ui.webview.WebViewActivity.EXTRA_URL, url)
                        startActivity(intent)
                    }
                    true
                }
                R.id.my_dmail -> {
                    // Open user's DMail
                    val host = prefs.getHost()
                    val url = "https://$host/dmails"
                    val intent = Intent(this, com.mommys.app.ui.webview.WebViewActivity::class.java)
                    intent.putExtra(com.mommys.app.ui.webview.WebViewActivity.EXTRA_URL, url)
                    startActivity(intent)
                    true
                }
                R.id.my_sets -> {
                    // Open user's sets using BrowseSetsActivity in my_sets mode
                    val intent = Intent(this, com.mommys.app.ui.sets.BrowseSetsActivity::class.java)
                    intent.putExtra(com.mommys.app.ui.sets.BrowseSetsActivity.EXTRA_MY_SETS, true)
                    startActivity(intent)
                    true
                }
                R.id.my_following -> {
                    // Open user's followed tags (settings following page)
                    startActivity(Intent(this, SettingsActivity::class.java).apply {
                        putExtra("open_following", true)
                    })
                    true
                }
                
                // Go To submenu
                R.id.go_to_page -> {
                    showGoToPageDialog()
                    true
                }
                R.id.go_to_random_post -> {
                    goToRandomPost()
                    true
                }
                R.id.go_to_user -> {
                    showGoToUserDialog()
                    true
                }
                
                // Popular submenu - Abre PopularActivity como la app original
                R.id.popular_by_day -> {
                    openPopularActivity(PopularActivity.TYPE_DAY)
                    true
                }
                R.id.popular_by_week -> {
                    openPopularActivity(PopularActivity.TYPE_WEEK)
                    true
                }
                R.id.popular_by_month -> {
                    openPopularActivity(PopularActivity.TYPE_MONTH)
                    true
                }
                
                // Browse submenu
                R.id.browse_following -> {
                    // Open FollowingPostActivity (posts from followed tags)
                    startActivity(Intent(this, FollowingPostActivity::class.java))
                    true
                }
                R.id.browse_tags -> {
                    startActivity(Intent(this, BrowseTagsActivity::class.java))
                    true
                }
                R.id.browse_pools -> {
                    startActivity(Intent(this, BrowsePoolsActivity::class.java))
                    true
                }
                R.id.browse_sets -> {
                    startActivity(Intent(this, com.mommys.app.ui.sets.BrowseSetsActivity::class.java))
                    true
                }
                R.id.browse_wiki -> {
                    showWikiSearchDialog()
                    true
                }
                R.id.browse_artists -> {
                    // Open WebView with artists page
                    val intent = Intent(this, com.mommys.app.ui.webview.WebViewActivity::class.java)
                    intent.putExtra(com.mommys.app.ui.webview.WebViewActivity.EXTRA_URL, 
                        "https://${prefs.getHost()}/artists")
                    startActivity(intent)
                    true
                }
                R.id.browse_comments -> {
                    // Open WebView with comments page
                    val intent = Intent(this, com.mommys.app.ui.webview.WebViewActivity::class.java)
                    intent.putExtra(com.mommys.app.ui.webview.WebViewActivity.EXTRA_URL, 
                        "https://${prefs.getHost()}/comments")
                    startActivity(intent)
                    true
                }
                R.id.browse_blips -> {
                    // Open WebView with blips page
                    val intent = Intent(this, com.mommys.app.ui.webview.WebViewActivity::class.java)
                    intent.putExtra(com.mommys.app.ui.webview.WebViewActivity.EXTRA_URL, 
                        "https://${prefs.getHost()}/blips")
                    startActivity(intent)
                    true
                }
                R.id.browse_users -> {
                    // Open WebView with users page
                    val intent = Intent(this, com.mommys.app.ui.webview.WebViewActivity::class.java)
                    intent.putExtra(com.mommys.app.ui.webview.WebViewActivity.EXTRA_URL, 
                        "https://${prefs.getHost()}/users")
                    startActivity(intent)
                    true
                }
                
                // Queued Downloads
                R.id.downloads -> {
                    startActivity(Intent(this, DownloadManagerActivity::class.java))
                    true
                }
                
                // Following Posts
                R.id.following_posts -> {
                    startActivity(Intent(this, FollowingPostActivity::class.java))
                    true
                }
                
                // About submenu
                R.id.about -> {
                    startActivity(Intent(this, AboutActivity::class.java))
                    true
                }
                R.id.help -> {
                    showHelpDialog()
                    true
                }
                R.id.changelog -> {
                    startActivity(Intent(this, com.mommys.app.ui.changelog.ChangelogActivity::class.java))
                    true
                }
                R.id.news -> {
                    startActivity(Intent(this, com.mommys.app.ui.news.NewsActivity::class.java))
                    true
                }
                R.id.donate -> {
                    startActivity(Intent(this, com.mommys.app.ui.donate.DonateActivity::class.java))
                    true
                }
                R.id.licenses -> {
                    // Open licenses using ScrollViewTextActivity
                    val intent = Intent(this, com.mommys.app.ui.webview.ScrollViewTextActivity::class.java)
                    intent.putExtra(com.mommys.app.ui.webview.ScrollViewTextActivity.EXTRA_TYPE, 
                        com.mommys.app.ui.webview.ScrollViewTextActivity.TYPE_LICENSES)
                    startActivity(intent)
                    true
                }
                R.id.translate -> {
                    // Open Crowdin or translation URL
                    val url = "https://crowdin.com/project/mommys"
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.discord -> {
                    // Open Discord invite
                    val url = "https://discord.gg/mommys"
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.send_feedback -> {
                    // Open email intent for feedback
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("feedback@mommys.app"))
                        putExtra(Intent.EXTRA_SUBJECT, "Mommys App Feedback")
                    }
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, R.string.no_email_app, Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                
                // Saved Searches
                R.id.saved_searches -> {
                    startActivity(Intent(this, SavedSearchesActivity::class.java))
                    true
                }
                
                // Check for Updates
                R.id.check_updates -> {
                    checkForUpdates()
                    true
                }
                
                // Settings
                R.id.settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                
                else -> false
            }
        }
        
        popup.show()
    }
    
    /**
     * Ir a un post aleatorio
     * Usa /posts/random.json como la app original
     * Abre PostActivity directamente con el post obtenido
     */
    private fun goToRandomPost() {
        // Mostrar diálogo de loading como la app original
        val loadingDialog = AlertDialog.Builder(this)
            .setTitle(R.string.loading)
            .setMessage(getString(R.string.loading_message, getString(R.string.loading_random_post)))
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Usar el endpoint normal con order:random para obtener un formato consistente
                val currentTags = binding.searchView.query?.toString()?.trim()?.ifEmpty { null }
                val tags = if (currentTags != null) "$currentTags order:random" else "order:random"
                val response = api.getPosts(tags = tags, limit = 1)
                
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    if (response.isSuccessful) {
                        val post = response.body()?.posts?.firstOrNull()
                        if (post != null) {
                            val intent = PostActivity.createIntent(
                                this@MainActivity, 
                                listOf(post),
                                0
                            )
                            startActivity(intent)
                        } else {
                            Toast.makeText(this@MainActivity, R.string.no_results, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Error: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    Toast.makeText(this@MainActivity, e.message ?: "Error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Abre PopularActivity con el tipo especificado (day, week, month)
     * Como la app original que tiene una activity dedicada para Popular
     */
    private fun openPopularActivity(type: Int) {
        val intent = Intent(this, PopularActivity::class.java).apply {
            putExtra(PopularActivity.EXTRA_TYPE, type)
        }
        startActivity(intent)
    }
    
    /**
     * Muestra diálogo para ir a un usuario
     * Como la app original: acepta username o user ID
     */
    private fun showGoToUserDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.go_to_user_hint)
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.go_to_user_title)
            .setMessage(R.string.go_to_user_message)
            .setView(input)
            .setPositiveButton(R.string.go) { _, _ ->
                val userInput = input.text.toString().trim()
                if (userInput.isNotEmpty()) {
                    goToUser(userInput)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    /**
     * Navega al perfil de un usuario
     * Abre ProfileActivity como la app original
     * Normaliza el input: trim y replace spaces con underscores
     */
    private fun goToUser(userInput: String) {
        val normalized = userInput.trim().replace(" ", "_")
        
        if (normalized.isEmpty()) {
            return
        }
        
        // Abrir ProfileActivity con el username
        startActivity(Intent(this, com.mommys.app.ui.profile.ProfileActivity::class.java).apply {
            putExtra(com.mommys.app.ui.profile.ProfileActivity.EXTRA_USERNAME, normalized)
        })
    }
    
    /**
     * Mostrar diálogo de Help
     */
    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_about_help)
            .setMessage("Search tips:\n\n• Use spaces to search multiple tags\n• Use - prefix to exclude tags\n• Use order:score to sort by score\n• Use order:favcount to sort by favorites\n• Use rating:s, rating:q, rating:e for ratings")
            .setPositiveButton(R.string.ok, null)
            .show()
    }
    
    /**
     * Mostrar diálogo de búsqueda de Wiki
     */
    private fun showWikiSearchDialog() {
        val editText = android.widget.EditText(this).apply {
            hint = getString(R.string.wiki_search_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setPadding(48, 32, 48, 32)
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.wiki_search_title)
            .setView(editText)
            .setPositiveButton(R.string.search) { _, _ ->
                val query = editText.text.toString().trim()
                if (query.isNotEmpty()) {
                    val intent = Intent(this, com.mommys.app.ui.wiki.WikiShowActivity::class.java)
                    intent.putExtra(com.mommys.app.ui.wiki.WikiShowActivity.EXTRA_WIKI_TITLE, query.replace(" ", "_"))
                    startActivity(intent)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onRefresh() {
        // Recargar la página actual
        val currentPosition = binding.viewPager.currentItem
        if (currentPosition < pageHandlers.size) {
            pageHandlers[currentPosition].retry()
        } else {
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }
    
    /**
     * Procesa los tags pendientes agregados desde PostActivity
     * Como la app original en MainActivity.onStart (líneas 1050-1062)
     * Revisa si hay tags pendientes y los agrega a la búsqueda actual
     */
    override fun onResume() {
        super.onResume()
        
        // Detectar cambio de host (como f31254n0 en app original)
        // Cuando el usuario cambia el host en Settings, debemos recargar los posts
        val newHost = prefs.getHost()
        if (currentHost.isNotEmpty() && currentHost != newHost) {
            android.util.Log.d("MainActivity", "Host changed from $currentHost to $newHost, reloading...")
            currentHost = newHost
            // El host cambió, recargar la búsqueda actual con el nuevo host
            reloadCurrentSearch()
            return // No continuar con el resto del onResume ya que estamos recargando
        }
        currentHost = newHost
        
        // Actualizar preferencias del grid (puede haber cambiado en Settings)
        updateGridPreferences()
        
        // Actualizar visibilidad del banner de ads (si cambió en Settings)
        updateAdsVisibility()
        
        processPendingTags()
        
        // Verificar y limpiar cache si excede el límite (como di/x.java en la app original)
        checkAndCleanCacheIfNeeded()
    }
    
    /**
     * Recarga la búsqueda actual con los tags actuales
     * Usado cuando el host cambia para actualizar los posts
     */
    private fun reloadCurrentSearch() {
        val query = binding.searchView.query?.toString()?.trim() ?: ""
        
        // Sincronizar ApiClient con el nuevo host
        val useE621 = prefs.useE621()
        ApiClient.setUseE621(useE621)
        api = ApiClient.getApiService()
        
        android.util.Log.d("MainActivity", "Reloading search with host: ${if (useE621) "e621.net" else "e926.net"}")
        
        // Limpiar el RecyclerView del ViewPager2 para evitar inconsistencias
        binding.viewPager.adapter = null
        
        // Ahora limpiar los datos
        pageHandlers.clear()
        pagesCreated.set(0)
        totalPages.set(0)
        
        // Recrear el adapter y asignarlo de nuevo
        pagesAdapter = PagesAdapter(pageHandlers, binding.viewPager)
        binding.viewPager.adapter = pagesAdapter
        
        // Recargar con el mismo query
        performSearch(query)
    }
    
    /**
     * Verifica si el cache excede el límite configurado y lo limpia
     * Como en di/x.java: new Thread(new x(this, 3)).start()
     */
    private fun checkAndCleanCacheIfNeeded() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.mommys.app.util.CacheManager.checkAndCleanCacheIfNeeded(
                    applicationContext,
                    prefs
                )
            } catch (e: Exception) {
                // Ignorar errores silenciosamente
            }
        }
    }
    
    /**
     * Actualiza las preferencias del grid y refresca los adapters
     */
    private fun updateGridPreferences() {
        // SwipeRefreshLayout habilitado por grid_refresh
        binding.swipeRefreshLayout.isEnabled = prefs.gridRefresh
        
        // Actualizar swipe del ViewPager basado en grid_navigate
        binding.viewPager.isUserInputEnabled = !prefs.gridNavigate
        
        // Obtener las nuevas columnas y seenIds
        val newColumns = prefs.getGridColumns()
        val seenIds = AppSeenDatabase.getAllSeenIdsSync(this)
        
        // Actualizar cada PageHandler con las nuevas preferencias
        pageHandlers.forEach { handler ->
            handler.updateGridSettings(newColumns, seenIds)
        }
    }
    
    /**
     * Procesa los tags pendientes para agregar/quitar de la búsqueda actual
     * Como MainActivity.f24886p0 en la app original
     */
    private fun processPendingTags() {
        val prefs = getSharedPreferences("current_search", MODE_PRIVATE)
        val pendingAdd = prefs.getStringSet("pending_add", emptySet()) ?: emptySet()
        val pendingRemove = prefs.getStringSet("pending_remove", emptySet()) ?: emptySet()
        
        if (pendingAdd.isEmpty() && pendingRemove.isEmpty()) {
            return
        }
        
        // Construir la nueva query agregando/quitando tags
        val currentQuery = binding.searchView.query?.toString() ?: ""
        val newQuery = StringBuilder(currentQuery)
        
        // Agregar tags (opción 5: Add to current search)
        for (tag in pendingAdd) {
            if (tag.isNotEmpty() && !currentQuery.contains(tag)) {
                if (newQuery.isNotEmpty()) {
                    newQuery.append(" ")
                }
                newQuery.append(tag)
            }
        }
        
        // Agregar tags con negación (opción 6: Remove from current search)
        for (tag in pendingRemove) {
            if (tag.isNotEmpty() && !currentQuery.contains("-$tag")) {
                if (newQuery.isNotEmpty()) {
                    newQuery.append(" ")
                }
                newQuery.append("-")
                newQuery.append(tag)
            }
        }
        
        // Limpiar los pendientes
        prefs.edit()
            .remove("pending_add")
            .remove("pending_remove")
            .apply()
        
        // Actualizar la barra de búsqueda con los nuevos tags
        if (newQuery.toString() != currentQuery) {
            binding.searchView.setQuery(newQuery.toString(), false)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        suggestionJob?.cancel()
        networkObserverJob?.cancel() // Cancelar observación de red
        searchHelper.cleanup()
        suggestionsAdapter.changeCursor(null)
        UpdateManager.cleanup(this) // Limpiar recursos del UpdateManager
    }
    
    // ==================== IMPLEMENTACIÓN DE SelectionCallback ====================
    // Similar a qi.k en la app original
    
    /**
     * Llamado cuando un post es seleccionado
     * Similar a MainActivity.p(ii.m) en app original (líneas 1093-1103)
     */
    override fun onPostSelected(post: Post) {
        synchronized(selectionLock) {
            selectedPosts.add(post)
            if (selectedPosts.size == 1) {
                // Primera selección - cambiar a menú de selección
                updateBottomMenu()
            }
            // Actualizar badge con cantidad
            updateSelectionBadge()
        }
    }
    
    /**
     * Llamado cuando un post es deseleccionado
     * Similar a MainActivity.n(ii.m) en app original (líneas 635-648)
     */
    override fun onPostDeselected(post: Post) {
        synchronized(selectionLock) {
            selectedPosts.remove(post)
            if (selectedPosts.isEmpty()) {
                // Sin selecciones - volver a menú normal
                updateBottomMenu()
            } else {
                // Actualizar badge
                updateSelectionBadge()
            }
        }
    }
    
    /**
     * Devuelve la cantidad de posts seleccionados
     * Similar a qi.k.r() en app original
     */
    override fun getSelectedCount(): Int {
        return selectedPosts.size
    }
    
    /**
     * Cambia el menú del BottomNavigationView según el modo
     * Similar a MainActivity.O() en app original (líneas 463-480)
     */
    private fun updateBottomMenu() {
        val newMenuType = if (selectedPosts.isNotEmpty()) 1 else 0
        
        if (currentMenuType != newMenuType) {
            binding.bottomNav.menu.clear()
            
            if (newMenuType == 1) {
                // Menú de selección
                binding.bottomNav.inflateMenu(R.menu.menu_bottom_selected)
                setupSelectionMenuListeners()
            } else {
                // Menú normal
                binding.bottomNav.inflateMenu(R.menu.bottom_nav_menu)
                setupBottomNavigation()
            }
            
            currentMenuType = newMenuType
        }
        
        // Habilitar/deshabilitar swipe del ViewPager según el modo
        // En modo selección, permitir swipe (como app original: z6 = !prefs.i())
        binding.viewPager.isUserInputEnabled = !prefs.gridNavigate
    }
    
    /**
     * Actualiza el badge del botón de descarga con la cantidad seleccionada
     * Similar a this.L.a(R.id.selected_download).j(this.T.size()) en app original
     */
    private fun updateSelectionBadge() {
        val badge = binding.bottomNav.getOrCreateBadge(R.id.selected_download)
        badge.isVisible = true
        badge.number = selectedPosts.size
    }
    
    /**
     * Configura los listeners del menú de selección
     */
    private fun setupSelectionMenuListeners() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.selected_clear -> {
                    // Limpiar todas las selecciones
                    clearAllSelections()
                    false
                }
                R.id.selected_download -> {
                    // Descargar todos los posts seleccionados
                    downloadSelectedPosts()
                    false
                }
                R.id.selected_select_all -> {
                    // Seleccionar todos los posts de la página actual
                    selectAllCurrentPage()
                    false
                }
                else -> false
            }
        }
    }
    
    /**
     * Limpia todas las selecciones
     * Similar a MainActivity.H() en app original (líneas 307-325)
     */
    private fun clearAllSelections() {
        synchronized(selectionLock) {
            // Limpiar selecciones en cada PageHandler
            pageHandlers.forEach { handler ->
                handler.clearSelections()
            }
            
            // Limpiar el set principal
            selectedPosts.clear()
            
            // Volver al menú normal
            updateBottomMenu()
        }
    }
    
    /**
     * Descarga todos los posts seleccionados
     * Similar a MainActivity.I() y di/x.java en app original
     * Usa BatchDownloader con notificación de progreso
     */
    private fun downloadSelectedPosts() {
        if (selectedPosts.isEmpty()) {
            Toast.makeText(this, R.string.no_posts_selected, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Verificar permisos si es necesario (Android < 10)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
                return
            }
        }
        
        // Copiar posts seleccionados antes de limpiar
        val postsToDownload: List<Post>
        synchronized(selectionLock) {
            postsToDownload = selectedPosts.toList()
            
            // Limpiar selecciones después de copiar
            clearAllSelections()
        }
        
        // Iniciar descarga batch con notificación de progreso
        // Como la app original: toast inicial, notificación con progreso, toast final
        com.mommys.app.util.BatchDownloader.startBatchDownload(
            context = this,
            posts = postsToDownload,
            prefsManager = prefs
        )
    }
    
    /**
     * Selecciona todos los posts de la página actual
     * Similar a selected_select_all handler en di/w.java (líneas 98-105)
     */
    private fun selectAllCurrentPage() {
        val currentPageIndex = binding.viewPager.currentItem
        val handler = pageHandlers.getOrNull(currentPageIndex)
        
        if (handler != null) {
            handler.selectAll(this)
            updateSelectionBadge()
        }
    }
    
    /**
     * Obtiene el set de IDs de posts seleccionados
     * Para sincronizar con los adapters de cada página
     */
    fun getSelectedPostIds(): Set<Int> {
        return selectedPosts.map { it.id }.toSet()
    }
    
    /**
     * Verifica si hay actualizaciones disponibles
     */
    private fun checkForUpdates() {
        // Mostrar diálogo de "Checking..."
        val checkingDialog = AlertDialog.Builder(this)
            .setMessage(R.string.update_checking)
            .setCancelable(false)
            .create()
        checkingDialog.show()
        
        lifecycleScope.launch {
            val result = UpdateManager.checkForUpdates(this@MainActivity, forceCheck = true)
            checkingDialog.dismiss()
            
            when (result) {
                is UpdateManager.UpdateResult.UpdateAvailable -> {
                    showUpdateDialog(result.release, result.currentVersion)
                }
                is UpdateManager.UpdateResult.NoUpdateAvailable -> {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.update_no_update,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is UpdateManager.UpdateResult.Error -> {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.update_error, result.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * Muestra el diálogo de actualización disponible
     */
    private fun showUpdateDialog(release: UpdateManager.ReleaseInfo, currentVersion: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_update, null)
        val txtNewVersion = dialogView.findViewById<android.widget.TextView>(R.id.txtNewVersion)
        val txtCurrentVersion = dialogView.findViewById<android.widget.TextView>(R.id.txtCurrentVersion)
        val txtReleaseNotes = dialogView.findViewById<android.widget.TextView>(R.id.txtReleaseNotes)
        val scrollReleaseNotes = dialogView.findViewById<android.widget.ScrollView>(R.id.scrollReleaseNotes)
        val progressBar = dialogView.findViewById<android.widget.ProgressBar>(R.id.progressBar)
        val txtDownloadStatus = dialogView.findViewById<android.widget.TextView>(R.id.txtDownloadStatus)
        
        txtNewVersion.text = getString(R.string.update_new_version, release.versionName)
        txtCurrentVersion.text = getString(R.string.update_current_version, currentVersion)
        
        // Mostrar notas de release si existen
        if (release.releaseNotes.isNotBlank()) {
            scrollReleaseNotes.visibility = View.VISIBLE
            txtReleaseNotes.text = release.releaseNotes
        }
        
        // Variable para trackear si estamos descargando
        var isDownloading = false
        
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.update_available_title)
            .setView(dialogView)
            .setPositiveButton(R.string.update_download, null) // Setear null para manejar manualmente
            .setNegativeButton(R.string.update_later, null)
            .setCancelable(true) // Permitir cancelar inicialmente
            .create()
        
        // Prevenir que se cierre con back button mientras descarga
        dialog.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && isDownloading) {
                true // Consumir el evento, no cerrar
            } else {
                false
            }
        }
        
        dialog.show()
        
        // Manejar el botón de descarga manualmente para no cerrar el diálogo
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            // Verificar permiso de instalar apps desconocidas
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!packageManager.canRequestPackageInstalls()) {
                    // Pedir permiso
                    Toast.makeText(this, R.string.update_install_permission, Toast.LENGTH_LONG).show()
                    val intent = Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        android.net.Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                    return@setOnClickListener
                }
            }
            
            // Marcar que estamos descargando
            isDownloading = true
            dialog.setCancelable(false) // No permitir cancelar mientras descarga
            
            // Deshabilitar botones y mostrar progreso
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
            progressBar.visibility = View.VISIBLE
            progressBar.isIndeterminate = true // DownloadManager no reporta progreso granular
            txtDownloadStatus.visibility = View.VISIBLE
            txtDownloadStatus.text = getString(R.string.update_downloading)
            
            // Iniciar descarga
            UpdateManager.downloadAndInstallUpdate(
                context = this,
                releaseInfo = release,
                onProgress = { _ ->
                    // DownloadManager no proporciona progreso granular
                    // El progressBar ya está en modo indeterminate
                },
                onComplete = {
                    isDownloading = false
                    if (!isFinishing && !isDestroyed) {
                        runOnUiThread {
                            txtDownloadStatus.text = getString(R.string.update_download_complete)
                            // Cerrar el dialogo después de un breve delay
                            dialog.window?.decorView?.postDelayed({
                                if (dialog.isShowing) {
                                    dialog.dismiss()
                                }
                            }, 300)
                        }
                    }
                },
                onError = { error ->
                    isDownloading = false
                    dialog.setCancelable(true)
                    if (!isFinishing && !isDestroyed) {
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            txtDownloadStatus.text = error
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                        }
                    }
                }
            )
        }
    }
    
    /**
     * Verifica actualizaciones automáticamente al iniciar (una vez al día)
     */
    private fun checkForUpdatesOnStartup() {
        lifecycleScope.launch {
            val result = UpdateManager.checkForUpdates(this@MainActivity, forceCheck = false)
            
            if (result is UpdateManager.UpdateResult.UpdateAvailable) {
                showUpdateDialog(result.release, result.currentVersion)
            }
        }
    }
    
    // ==================== NETWORK MONITORING ====================
    // Similar a ff/b.java (NetworkConnectivityListener) en la app original
    
    /**
     * Configura el monitoreo de red usando StateFlow para observar cambios
     * Similar a cómo la app original registra NetworkCallback en ff/b.java
     * 
     * Observa el networkState del NetworkMonitor y:
     * - Actualiza el banner de estado de red (txtNetworkStatus)
     * - Detecta reconexiones para mostrar Snackbar
     * - Marca wasOffline para saber si debemos hacer auto-refresh
     */
    private fun setupNetworkMonitoring() {
        networkObserverJob = lifecycleScope.launch {
            networkMonitor.networkState.collectLatest { state ->
                updateNetworkStatusUI(state)
            }
        }
    }
    
    /**
     * Actualiza la UI del banner de estado de red
     * Similar a cómo la app original muestra/oculta el banner en MainActivity
     * 
     * Comportamiento:
     * - Sin conexión: Mostrar banner rojo con icono wifi_off
     * - Conexión lenta (2G/3G): Mostrar banner amarillo de advertencia
     * - Reconexión después de estar offline: Mostrar Snackbar verde
     * - Conexión normal: Ocultar banner
     * 
     * @param state Estado actual de la red desde NetworkMonitor
     */
    private fun updateNetworkStatusUI(state: NetworkState) {
        val txtNetworkStatus = binding.root.findViewById<android.widget.TextView>(R.id.txtNetworkStatus)
            ?: return
        
        when {
            // Sin conexión - mostrar banner rojo
            !state.isOnline -> {
                txtNetworkStatus.visibility = View.VISIBLE
                txtNetworkStatus.text = getString(R.string.network_offline)
                txtNetworkStatus.setBackgroundColor(
                    ContextCompat.getColor(this, R.color.error_background)
                )
                txtNetworkStatus.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_wifi_off, 0, 0, 0
                )
                txtNetworkStatus.compoundDrawablePadding = 16
                
                // Marcar que estamos offline para detectar reconexión
                wasOffline = true
            }
            
            // Conexión lenta (2G/3G) - mostrar advertencia amarilla
            state.isSlow -> {
                txtNetworkStatus.visibility = View.VISIBLE
                txtNetworkStatus.text = getString(R.string.network_slow)
                txtNetworkStatus.setBackgroundColor(
                    ContextCompat.getColor(this, R.color.warning_background)
                )
                txtNetworkStatus.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                
                // Si estábamos offline y ahora tenemos conexión (aunque lenta)
                if (wasOffline) {
                    wasOffline = false
                    showReconnectedSnackbar()
                }
            }
            
            // Conexión normal - ocultar banner
            else -> {
                txtNetworkStatus.visibility = View.GONE
                
                // Si estábamos offline, mostrar Snackbar de reconexión
                if (wasOffline) {
                    wasOffline = false
                    showReconnectedSnackbar()
                }
            }
        }
    }
    
    /**
     * Muestra un Snackbar indicando que la conexión se ha restablecido
     * Similar a cómo la app original muestra feedback visual al reconectar
     * 
     * El Snackbar incluye acción para refrescar la página actual
     */
    private fun showReconnectedSnackbar() {
        Snackbar.make(
            binding.root,
            R.string.network_reconnected,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.refresh) {
            // Refrescar la página actual
            onRefresh()
        }.setBackgroundTint(
            ContextCompat.getColor(this, R.color.success_background)
        ).show()
        
        // Flush acciones pendientes del dispatcher
        // Esto reintenta automáticamente las cargas fallidas
        networkDispatcher.flushFailedActions()
    }
    
    /**
     * Implementación de NetworkAwareDispatcher.PageRefreshCallback
     * Llamado cuando el dispatcher detecta que hay acciones pendientes
     * y la conexión se ha restablecido
     * 
     * Refresca la página actual para recargar contenido fallido
     */
    override fun onRefreshRequested() {
        runOnUiThread {
            // Refrescar la página actual como si el usuario hubiera hecho pull-to-refresh
            val currentPosition = binding.viewPager.currentItem
            if (currentPosition < pageHandlers.size) {
                val handler = pageHandlers[currentPosition]
                // Solo refrescar si la página tuvo errores o está vacía
                if (handler.hasError || (handler.posts.isEmpty() && !handler.isLoading)) {
                    handler.retry()
                }
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
    }
    
    // ===== POST INFO POPUP (como MainActivity.K() en app original) =====
    
    /**
     * Muestra el popup de información del post
     * Similar a MainActivity.K(j jVar, ii.m mVar, FrameLayout frameLayout) en la app original
     * 
     * Se llama cuando el usuario toca el botón de info (!) en el grid
     */
    fun showPostInfo(post: Post) {
        val fLInfo = findViewById<FrameLayout>(R.id.fLInfo) ?: return
        
        // Limpiar views anteriores
        fLInfo.removeAllViews()
        
        // Inflar el contenido
        val contentView = layoutInflater.inflate(R.layout.content_post_info, fLInfo, false)
        
        // Obtener referencias a los views
        val txtPost = contentView.findViewById<android.widget.TextView>(R.id.txtPost)
        val txtTags = contentView.findViewById<android.widget.TextView>(R.id.txtTags)
        val txtArtist = contentView.findViewById<android.widget.TextView>(R.id.txtArtist)
        val txtScore = contentView.findViewById<android.widget.TextView>(R.id.txtScore)
        val txtFavourites = contentView.findViewById<android.widget.TextView>(R.id.txtFavourites)
        val txtRating = contentView.findViewById<android.widget.TextView>(R.id.txtRating)
        val txtComments = contentView.findViewById<android.widget.TextView>(R.id.txtComments)
        val txtPool = contentView.findViewById<android.widget.TextView>(R.id.txtPool)
        val txtParent = contentView.findViewById<android.widget.TextView>(R.id.txtParent)
        val txtChildren = contentView.findViewById<android.widget.TextView>(R.id.txtChildren)
        val imgPreview = contentView.findViewById<com.github.chrisbanes.photoview.PhotoView>(R.id.imgPreview)
        val imgClose = contentView.findViewById<android.widget.ImageView>(R.id.imgClose)
        
        // Configurar título: Post #12345
        txtPost.text = getString(R.string.post_info_title, post.id)
        
        // Contar total de tags
        val totalTags = post.tags.general.size + post.tags.species.size + 
                        post.tags.character.size + post.tags.artist.size + 
                        post.tags.copyright.size + post.tags.meta.size + 
                        post.tags.lore.size + post.tags.invalid.size
        txtTags.text = getString(R.string.post_info_tags, totalTags)
        
        // Artista (puede haber múltiples)
        val artistName = if (post.tags.artist.isNotEmpty()) {
            post.tags.artist.joinToString(", ")
        } else {
            getString(R.string.unknown_artist)
        }
        txtArtist.text = getString(R.string.post_info_artist, artistName)
        
        // Score con desglose
        txtScore.text = getString(R.string.post_info_score, post.score.total, post.score.up, kotlin.math.abs(post.score.down))
        
        // Favoritos
        txtFavourites.text = getString(R.string.post_info_favourites, post.favCount)
        
        // Rating
        txtRating.text = getString(R.string.post_info_rating, post.rating.uppercase())
        
        // Comentarios (solo si hay)
        if (post.commentCount > 0) {
            txtComments.visibility = View.VISIBLE
            txtComments.text = getString(R.string.post_info_comments, post.commentCount)
        } else {
            txtComments.visibility = View.GONE
        }
        
        // Pool (solo si hay)
        if (post.pools.isNotEmpty()) {
            txtPool.visibility = View.VISIBLE
            txtPool.text = getString(R.string.post_info_pool)
        } else {
            txtPool.visibility = View.GONE
        }
        
        // Parent (solo si hay)
        val relationships = post.relationships
        if (relationships.parentId != null && relationships.parentId > 0) {
            txtParent.visibility = View.VISIBLE
            txtParent.text = getString(R.string.post_info_parent)
        } else {
            txtParent.visibility = View.GONE
        }
        
        // Children (solo si hay)
        if (relationships.hasChildren) {
            txtChildren.visibility = View.VISIBLE
            txtChildren.text = getString(R.string.post_info_children)
        } else {
            txtChildren.visibility = View.GONE
        }
        
        // Cargar imagen preview
        val previewUrl = post.preview.url ?: post.sample?.url ?: post.file.url
        com.bumptech.glide.Glide.with(this)
            .load(previewUrl)
            .into(imgPreview)
        
        // Click en imagen cierra el popup
        imgPreview.setOnClickListener {
            hidePostInfo()
        }
        
        // Botón cerrar
        imgClose.setOnClickListener {
            hidePostInfo()
        }
        
        // Click en el fondo también cierra
        fLInfo.setOnClickListener {
            hidePostInfo()
        }
        
        // Agregar contenido al frame y mostrar
        fLInfo.addView(contentView)
        fLInfo.visibility = View.VISIBLE
    }
    
    /**
     * Oculta el popup de información del post
     * Similar a MainActivity.g() en la app original (qi.e interface)
     */
    fun hidePostInfo() {
        val fLInfo = findViewById<FrameLayout>(R.id.fLInfo) ?: return
        fLInfo.visibility = View.GONE
        fLInfo.removeAllViews()
    }
}
