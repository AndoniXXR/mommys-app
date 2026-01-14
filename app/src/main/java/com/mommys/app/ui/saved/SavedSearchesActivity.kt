package com.mommys.app.ui.saved

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.mommys.app.MommysApplication
import com.mommys.app.R
import com.mommys.app.data.database.SavedSearchEntity
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.databinding.ActivitySavedSearchesBinding
import com.mommys.app.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SavedSearchesActivity - Actividad para gestionar búsquedas guardadas
 * Similar a SavedSearchesActivity de la app original
 * 
 * Opciones disponibles:
 * - Continue: Abrir la búsqueda en la página guardada
 * - Edit: Editar nombre y query
 * - Delete: Eliminar la búsqueda
 * - Follow/Unfollow: Seguir para notificaciones de nuevos posts
 */
class SavedSearchesActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySavedSearchesBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var adapter: SavedSearchesAdapter
    
    private var savedSearches = mutableListOf<SavedSearchEntity>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySavedSearchesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = PreferencesManager(this)
        
        setupToolbar()
        setupRecyclerView()
        setupFab()
        
        loadSavedSearches()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
    
    private fun setupRecyclerView() {
        adapter = SavedSearchesAdapter(
            onItemClick = { search -> showSearchOptions(search) },
            onMoreClick = { search -> showSearchOptions(search) }
        )
        
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }
    
    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            showAddSearchDialog()
        }
    }
    
    private fun loadSavedSearches() {
        binding.progressBar.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = MommysApplication.getInstance()
                val searches = app.database.savedSearchDao().getAllSavedSearchesSync()
                
                // Ordenar según preferencia
                val sorted = sortSearches(searches)
                
                withContext(Dispatchers.Main) {
                    savedSearches.clear()
                    savedSearches.addAll(sorted)
                    adapter.submitList(savedSearches.toList())
                    
                    binding.progressBar.visibility = View.GONE
                    binding.emptyText.visibility = if (savedSearches.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerView.visibility = if (savedSearches.isEmpty()) View.GONE else View.VISIBLE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.emptyText.visibility = View.VISIBLE
                }
            }
        }
    }
    
    private fun sortSearches(searches: List<SavedSearchEntity>): List<SavedSearchEntity> {
        return when (prefs.getSavedOrder()) {
            0 -> searches.sortedByDescending { it.lastUsedAt }  // Date used
            1 -> searches.sortedByDescending { it.createdAt }   // Date created
            2 -> searches.sortedBy { it.name.lowercase() }      // Name
            3 -> searches.sortedBy { it.query.lowercase() }     // Tags
            else -> searches
        }
    }
    
    /**
     * Muestra diálogo con opciones para la búsqueda guardada
     * Similar al array saved_search_item_clicked de la app original
     */
    private fun showSearchOptions(search: SavedSearchEntity) {
        val options = arrayOf(
            getString(R.string.saved_continue),
            getString(R.string.saved_option_edit),
            getString(R.string.saved_option_delete),
            getString(R.string.saved_follow)
            // TODO: Añadir Unfollow si ya está siguiendo
        )
        
        AlertDialog.Builder(this)
            .setTitle(search.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> continueSearch(search)
                    1 -> editSearch(search)
                    2 -> confirmDeleteSearch(search)
                    3 -> followSearch(search)
                }
            }
            .show()
    }
    
    /**
     * Continuar búsqueda - abre MainActivity con los tags y página guardada
     */
    private fun continueSearch(search: SavedSearchEntity) {
        // Actualizar timestamp de último uso
        CoroutineScope(Dispatchers.IO).launch {
            val app = MommysApplication.getInstance()
            app.database.savedSearchDao().incrementUseCount(search.id)
        }
        
        // Abrir MainActivity con los tags y página
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("tags", search.query)
            putExtra("page", search.page)
            putExtra("finish", true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }
    
    /**
     * Editar búsqueda guardada
     */
    private fun editSearch(search: SavedSearchEntity) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        
        val nameInput = EditText(this).apply {
            hint = getString(R.string.saved_search_name_hint)
            setText(search.name)
        }
        val queryInput = EditText(this).apply {
            hint = getString(R.string.main_search_hint)
            setText(search.query)
        }
        val pageInput = EditText(this).apply {
            hint = "Page"
            setText(search.page.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        
        layout.addView(nameInput)
        layout.addView(queryInput)
        layout.addView(pageInput)
        
        AlertDialog.Builder(this)
            .setTitle(R.string.saved_edit_title)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = nameInput.text.toString().ifEmpty { search.name }
                val newQuery = queryInput.text.toString().ifEmpty { search.query }
                val newPage = pageInput.text.toString().toIntOrNull() ?: search.page
                
                CoroutineScope(Dispatchers.IO).launch {
                    val app = MommysApplication.getInstance()
                    val updated = search.copy(
                        name = newName,
                        query = newQuery,
                        page = newPage
                    )
                    app.database.savedSearchDao().update(updated)
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SavedSearchesActivity, R.string.saved_search_updated, Toast.LENGTH_SHORT).show()
                        loadSavedSearches()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    /**
     * Confirmar eliminación
     */
    private fun confirmDeleteSearch(search: SavedSearchEntity) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(R.string.saved_confirm_delete)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteSearch(search)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun deleteSearch(search: SavedSearchEntity) {
        CoroutineScope(Dispatchers.IO).launch {
            val app = MommysApplication.getInstance()
            app.database.savedSearchDao().delete(search.id)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SavedSearchesActivity, R.string.saved_search_deleted, Toast.LENGTH_SHORT).show()
                loadSavedSearches()
            }
        }
    }
    
    /**
     * Seguir búsqueda para notificaciones
     */
    private fun followSearch(search: SavedSearchEntity) {
        // TODO: Implementar sistema de seguimiento con notificaciones
        Toast.makeText(this, R.string.saved_following, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Diálogo para añadir nueva búsqueda
     */
    private fun showAddSearchDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        
        val nameInput = EditText(this).apply {
            hint = getString(R.string.saved_search_name_hint)
        }
        val queryInput = EditText(this).apply {
            hint = getString(R.string.main_search_hint)
        }
        
        layout.addView(nameInput)
        layout.addView(queryInput)
        
        AlertDialog.Builder(this)
            .setTitle(R.string.saved_add_title)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameInput.text.toString()
                val query = queryInput.text.toString()
                
                if (name.isNotEmpty() && query.isNotEmpty()) {
                    saveNewSearch(name, query)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun saveNewSearch(name: String, query: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val app = MommysApplication.getInstance()
            val newSearch = SavedSearchEntity(
                name = name,
                query = query,
                page = 1
            )
            app.database.savedSearchDao().insert(newSearch)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SavedSearchesActivity, R.string.saved_search_saved, Toast.LENGTH_SHORT).show()
                loadSavedSearches()
            }
        }
    }
    
    // ==================== MENU ====================
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_saved_searches, menu)
        
        // Marcar orden actual
        val orderId = when (prefs.getSavedOrder()) {
            0 -> R.id.order_by_date_used
            1 -> R.id.order_by_date_created
            2 -> R.id.order_by_name
            3 -> R.id.order_by_tags
            else -> R.id.order_by_date_used
        }
        menu.findItem(orderId)?.isChecked = true
        
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add -> {
                showAddSearchDialog()
                true
            }
            R.id.action_clear -> {
                confirmClearAll()
                true
            }
            R.id.order_by_date_used -> {
                prefs.setSavedOrder(0)
                item.isChecked = true
                loadSavedSearches()
                true
            }
            R.id.order_by_date_created -> {
                prefs.setSavedOrder(1)
                item.isChecked = true
                loadSavedSearches()
                true
            }
            R.id.order_by_name -> {
                prefs.setSavedOrder(2)
                item.isChecked = true
                loadSavedSearches()
                true
            }
            R.id.order_by_tags -> {
                prefs.setSavedOrder(3)
                item.isChecked = true
                loadSavedSearches()
                true
            }
            R.id.action_import -> {
                // TODO: Implementar importación
                Toast.makeText(this, "Import not implemented yet", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_export -> {
                // TODO: Implementar exportación
                Toast.makeText(this, "Export not implemented yet", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle(R.string.saved_clear_all)
            .setMessage(R.string.saved_confirm_clear)
            .setPositiveButton(R.string.delete) { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    val app = MommysApplication.getInstance()
                    app.database.savedSearchDao().deleteAll()
                    
                    withContext(Dispatchers.Main) {
                        loadSavedSearches()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
