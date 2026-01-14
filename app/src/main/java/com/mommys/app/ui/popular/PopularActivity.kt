package com.mommys.app.ui.popular

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.mommys.app.R
import com.mommys.app.data.api.ApiClient
import com.mommys.app.data.api.ApiService
import com.mommys.app.data.model.Post
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.databinding.ActivityPopularBinding
import com.mommys.app.ui.main.PostsAdapter
import com.mommys.app.ui.post.PostActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity para mostrar posts populares por día, semana o mes
 * Basada en la implementación original de PopularActivity
 */
class PopularActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PopularActivity"
        const val EXTRA_TYPE = "type"
        const val TYPE_DAY = 0
        const val TYPE_WEEK = 1
        const val TYPE_MONTH = 2
    }

    private lateinit var binding: ActivityPopularBinding
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var adapter: PostsAdapter
    
    // Usar siempre e621 para Popular ya que los posts populares son mayormente explícitos
    // y e926 devuelve URLs null para ese contenido
    private val api = ApiClient.getApiService(ApiService.BASE_URL_E621)
    private var posts = mutableListOf<Post>()
    
    private var popularType = TYPE_DAY
    private var currentDate = Calendar.getInstance(TimeZone.getTimeZone("GMT-4"))
    private var initialDate = Calendar.getInstance(TimeZone.getTimeZone("GMT-4"))
    
    private lateinit var datePickerDialog: DatePickerDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPopularBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        preferencesManager = PreferencesManager(this)
        popularType = intent.getIntExtra(EXTRA_TYPE, TYPE_DAY)
        
        setupToolbar()
        setupRecyclerView()
        setupDatePicker()
        setupNavigation()
        
        // Cargar datos iniciales
        updateUI()
        loadPopular()
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationIcon(R.drawable.ic_back)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        
        val title = when (popularType) {
            TYPE_DAY -> getString(R.string.popular_by_day)
            TYPE_WEEK -> getString(R.string.popular_by_week)
            TYPE_MONTH -> getString(R.string.popular_by_month)
            else -> getString(R.string.popular_by_day)
        }
        binding.toolbar.title = title
    }
    
    private fun setupRecyclerView() {
        val columns = preferencesManager.gridColumns
        adapter = PostsAdapter(
            onPostClick = { post ->
                // Abrir PostActivity
                val position = posts.indexOf(post)
                val intent = PostActivity.createIntent(this, posts, position)
                startActivity(intent)
            }
        )
        
        binding.recyclerView.layoutManager = GridLayoutManager(this, columns)
        binding.recyclerView.adapter = adapter
    }
    
    private fun setupDatePicker() {
        // Fecha mínima: 10 Feb 2007 (inicio de e621)
        val minDate = Calendar.getInstance().apply {
            set(2007, 1, 10)
        }
        
        datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                currentDate.set(year, month, dayOfMonth)
                updateUI()
                loadPopular()
            },
            currentDate.get(Calendar.YEAR),
            currentDate.get(Calendar.MONTH),
            currentDate.get(Calendar.DAY_OF_MONTH)
        )
        
        datePickerDialog.datePicker.minDate = minDate.timeInMillis
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.datePicker.firstDayOfWeek = Calendar.MONDAY
    }
    
    private fun setupNavigation() {
        // Botón anterior
        binding.btnPrev.setOnClickListener {
            navigatePrevious()
        }
        
        // Botón seleccionar fecha
        binding.btnSelect.setOnClickListener {
            datePickerDialog.show()
        }
        
        // Botón siguiente
        binding.btnNext.setOnClickListener {
            navigateNext()
        }
        
        // Actualizar texto del botón central según el tipo
        updateSelectButtonText()
    }
    
    private fun updateSelectButtonText() {
        val text = when (popularType) {
            TYPE_DAY -> getString(R.string.popular_choose_day)
            TYPE_WEEK -> getString(R.string.popular_choose_week)
            TYPE_MONTH -> getString(R.string.popular_choose_month)
            else -> getString(R.string.popular_choose_day)
        }
        binding.btnSelect.text = text
    }
    
    private fun navigatePrevious() {
        when (popularType) {
            TYPE_DAY -> currentDate.add(Calendar.DAY_OF_MONTH, -1)
            TYPE_WEEK -> currentDate.add(Calendar.WEEK_OF_YEAR, -1)
            TYPE_MONTH -> currentDate.add(Calendar.MONTH, -1)
        }
        updateDatePicker()
        updateUI()
        loadPopular()
    }
    
    private fun navigateNext() {
        when (popularType) {
            TYPE_DAY -> currentDate.add(Calendar.DAY_OF_MONTH, 1)
            TYPE_WEEK -> currentDate.add(Calendar.WEEK_OF_YEAR, 1)
            TYPE_MONTH -> currentDate.add(Calendar.MONTH, 1)
        }
        updateDatePicker()
        updateUI()
        loadPopular()
    }
    
    private fun updateDatePicker() {
        datePickerDialog.datePicker.updateDate(
            currentDate.get(Calendar.YEAR),
            currentDate.get(Calendar.MONTH),
            currentDate.get(Calendar.DAY_OF_MONTH)
        )
    }
    
    private fun updateUI() {
        // Actualizar texto de fecha
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        binding.txtDate.text = dateFormat.format(currentDate.time)
        
        // Habilitar/deshabilitar botones
        val today = Calendar.getInstance(TimeZone.getTimeZone("GMT-4"))
        val isToday = currentDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                      currentDate.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                      currentDate.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)
        
        binding.btnNext.isEnabled = !isToday
        
        // Verificar fecha mínima (10 Feb 2007)
        val isMinDate = currentDate.get(Calendar.YEAR) == 2007 &&
                        currentDate.get(Calendar.MONTH) == 1 &&
                        currentDate.get(Calendar.DAY_OF_MONTH) == 10
        
        binding.btnPrev.isEnabled = !isMinDate
    }
    
    private fun loadPopular() {
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.txtError.visibility = View.GONE
        binding.btnRefresh.visibility = View.GONE
        
        val scale = when (popularType) {
            TYPE_DAY -> "day"
            TYPE_WEEK -> "week"
            TYPE_MONTH -> "month"
            else -> "day"
        }
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = dateFormat.format(currentDate.time)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = api.getPopular(scale = scale, date = date)
                
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    
                    if (response.isSuccessful) {
                        // La API devuelve PostsResponse con {"posts": [...]}
                        val newPosts = response.body()?.posts ?: emptyList()
                        
                        Log.d(TAG, "Received ${newPosts.size} posts")
                        newPosts.take(5).forEach { post ->
                            Log.d(TAG, "Post ${post.id}: preview.url=${post.preview.url}, file.url=${post.file.url}")
                        }
                        
                        // Filtrar posts con URLs nulas (contenido restringido)
                        // Similar a PageHandler - posts que la API devuelve sin acceso
                        // La API puede devolver null, "" o literalmente "null" como string
                        val filteredPosts = newPosts.filter { post ->
                            val previewUrl = post.preview.url
                            val sampleUrl = post.sample?.url
                            val fileUrl = post.file.url
                            // URL válida: no null, no vacía, no "null", empieza con http
                            isValidUrl(previewUrl) || isValidUrl(sampleUrl) || isValidUrl(fileUrl)
                        }
                        
                        posts.clear()
                        posts.addAll(filteredPosts)
                        adapter.submitList(posts.toList())
                        
                        if (posts.isEmpty()) {
                            binding.txtError.text = getString(R.string.no_results)
                            binding.txtError.visibility = View.VISIBLE
                        } else {
                            binding.recyclerView.visibility = View.VISIBLE
                        }
                    } else {
                        Log.e(TAG, "API error: ${response.code()} - ${response.errorBody()?.string()}")
                        showError("Error: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    showError(e.message ?: "Unknown error")
                }
            }
        }
    }
    
    private fun showError(message: String) {
        binding.txtError.text = message
        binding.txtError.visibility = View.VISIBLE
        binding.btnRefresh.visibility = View.VISIBLE
        binding.btnRefresh.setOnClickListener {
            loadPopular()
        }
    }
    
    /**
     * Verifica si una URL es válida para mostrar contenido.
     * La API puede devolver null, cadena vacía "", o literalmente "null".
     */
    private fun isValidUrl(url: String?): Boolean {
        return url != null && url.isNotEmpty() && url != "null" && url.startsWith("http")
    }
}
