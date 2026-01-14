package com.mommys.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.mommys.app.R
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.util.CacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fragment de configuración de almacenamiento y descargas
 * Basado en ui/u.java de la app original
 * 
 * Opciones:
 * - storage_custom_folder_enabled: Usar carpeta personalizada o Download Manager de Android
 * - storage_custom_folder: Ruta de la carpeta personalizada
 * - storage_file_name_mask: Formato del nombre de archivo (%artist%-%id%, etc.)
 * - storage_overwrite: Sobrescribir archivos existentes
 * - storage_hide: Ocultar archivos de la galería (añade "." al nombre)
 * - storage_max_cache: Limitar tamaño del cache
 * - storage_max_cache_slider: Tamaño máximo del cache en MB
 * - storage_clear_cache: Limpiar cache manualmente
 */
class StorageSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var preferencesManager: PreferencesManager
    
    // Launcher para selección de carpeta (SAF - Storage Access Framework)
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            // Mantener permisos persistentes
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(selectedUri, takeFlags)
            
            // Guardar URI
            preferencesManager.storageCustomFolder = selectedUri.toString()
            
            // Actualizar summary con el nombre de la carpeta
            val docFile = DocumentFile.fromTreeUri(requireContext(), selectedUri)
            updateFolderSummary(docFile?.name ?: selectedUri.lastPathSegment ?: "Selected folder")
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_storage, rootKey)
        
        preferencesManager = PreferencesManager(requireContext())
        
        setupFolderPreferences()
        setupFileNamePreference()
        setupOptionsPreferences()
        setupCachePreferences()
    }
    
    override fun onResume() {
        super.onResume()
        // Actualizar tamaño de cache al volver al fragment
        updateCacheSummary()
    }

    /**
     * Configurar preferencias de carpeta de descarga
     * Como en ui/u.java líneas 14-27
     */
    private fun setupFolderPreferences() {
        val customFolderEnabledPref = findPreference<SwitchPreferenceCompat>("storage_custom_folder_enabled")
        val customFolderPref = findPreference<Preference>("storage_custom_folder")
        
        // Inicializar estado del switch
        customFolderEnabledPref?.isChecked = preferencesManager.storageCustomFolderEnabled
        customFolderEnabledPref?.setOnPreferenceChangeListener { _, newValue ->
            preferencesManager.storageCustomFolderEnabled = newValue as Boolean
            true
        }
        
        // Inicializar summary de la carpeta
        val savedUri = preferencesManager.storageCustomFolder
        if (savedUri.isNotEmpty()) {
            try {
                val uri = Uri.parse(savedUri)
                val docFile = DocumentFile.fromTreeUri(requireContext(), uri)
                updateFolderSummary(docFile?.name ?: uri.lastPathSegment ?: "Custom folder")
            } catch (e: Exception) {
                updateFolderSummary(getString(R.string.pref_storage_custom_folder_default))
            }
        } else {
            updateFolderSummary(getString(R.string.pref_storage_custom_folder_default))
        }
        
        // Click para abrir folder picker
        customFolderPref?.setOnPreferenceClickListener {
            folderPickerLauncher.launch(null)
            true
        }
    }
    
    private fun updateFolderSummary(folderName: String) {
        findPreference<Preference>("storage_custom_folder")?.summary = folderName
    }

    /**
     * Configurar preferencia de nombre de archivo
     * Como en ui/t.java - muestra un diálogo para editar el formato
     */
    private fun setupFileNamePreference() {
        val fileNameMaskPref = findPreference<Preference>("storage_file_name_mask")
        
        // Mostrar el formato actual como summary
        val currentMask = preferencesManager.storageFileNameMask.ifEmpty { "%artist%-%id%" }
        fileNameMaskPref?.summary = currentMask
        
        fileNameMaskPref?.setOnPreferenceClickListener {
            showFileNameMaskDialog()
            true
        }
    }
    
    /**
     * Muestra el diálogo para editar el formato del nombre de archivo
     * Como en ui/t.java líneas 24-36
     */
    private fun showFileNameMaskDialog() {
        val context = requireContext()
        val currentMask = preferencesManager.storageFileNameMask.ifEmpty { "%artist%-%id%" }
        
        val editText = EditText(context).apply {
            setText(currentMask)
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
            hint = "%artist%-%id%"
            setPadding(50, 30, 50, 30)
        }
        
        // Mensaje con las opciones disponibles (como la app original)
        val message = """
            Customize the file name of downloaded posts.
            
            Available options:
            %artist%
            %id%
            %character%
            %tags%
            %score%
            %favs%
            %timesaved%, %yyyy%, %mm%, %dd%
            
            File extension is added automatically.
            Use "/" for subfolders.
        """.trimIndent()
        
        AlertDialog.Builder(context)
            .setTitle(R.string.pref_storage_file_name_dialog_title)
            .setMessage(message)
            .setView(editText)
            .setPositiveButton(R.string.save) { _, _ ->
                val newMask = editText.text.toString().trim()
                if (newMask.isNotEmpty()) {
                    preferencesManager.storageFileNameMask = newMask
                    findPreference<Preference>("storage_file_name_mask")?.summary = newMask
                    Toast.makeText(context, R.string.saved, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Configurar opciones de sobrescritura y ocultación
     */
    private fun setupOptionsPreferences() {
        val overwritePref = findPreference<SwitchPreferenceCompat>("storage_overwrite")
        val hidePref = findPreference<SwitchPreferenceCompat>("storage_hide")
        
        overwritePref?.isChecked = preferencesManager.storageOverwrite
        overwritePref?.setOnPreferenceChangeListener { _, newValue ->
            preferencesManager.storageOverwrite = newValue as Boolean
            true
        }
        
        hidePref?.isChecked = preferencesManager.storageHide
        hidePref?.setOnPreferenceChangeListener { _, newValue ->
            preferencesManager.storageHide = newValue as Boolean
            true
        }
    }

    /**
     * Configurar preferencias de cache
     * Como en ui/u.java líneas 39-48
     */
    private fun setupCachePreferences() {
        val maxCachePref = findPreference<SwitchPreferenceCompat>("storage_max_cache")
        val maxCacheSliderPref = findPreference<SeekBarPreference>("storage_max_cache_slider")
        val clearCachePref = findPreference<Preference>("storage_clear_cache")
        
        // Max cache enabled
        maxCachePref?.isChecked = preferencesManager.storageMaxCache
        maxCachePref?.setOnPreferenceChangeListener { _, newValue ->
            preferencesManager.storageMaxCache = newValue as Boolean
            true
        }
        
        // Max cache slider (usa la key del XML: storage_max_cache_slider)
        maxCacheSliderPref?.value = preferencesManager.storageMaxCacheSlider
        maxCacheSliderPref?.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue as Int
            preferencesManager.storageMaxCacheSlider = value
            true
        }
        
        // Clear cache button
        updateCacheSummary()
        clearCachePref?.setOnPreferenceClickListener {
            showClearCacheDialog()
            true
        }
    }
    
    /**
     * Actualiza el summary del botón de limpiar cache con el tamaño actual
     */
    private fun updateCacheSummary() {
        val clearCachePref = findPreference<Preference>("storage_clear_cache")
        
        lifecycleScope.launch(Dispatchers.IO) {
            val cacheSize = CacheManager.getCacheSize(requireContext())
            val formattedSize = CacheManager.formatSize(cacheSize)
            
            withContext(Dispatchers.Main) {
                clearCachePref?.summary = getString(R.string.pref_storage_clear_cache_summary_size, formattedSize)
            }
        }
    }
    
    /**
     * Muestra diálogo de confirmación para limpiar cache
     */
    private fun showClearCacheDialog() {
        val context = requireContext()
        val cacheSize = CacheManager.formatSize(CacheManager.getCacheSize(context))
        
        AlertDialog.Builder(context)
            .setTitle(R.string.pref_storage_clear_cache_title)
            .setMessage(getString(R.string.pref_storage_clear_cache_confirm, cacheSize))
            .setPositiveButton(R.string.clear) { _, _ ->
                clearCache()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    /**
     * Limpia todo el cache de la aplicación
     */
    private fun clearCache() {
        lifecycleScope.launch {
            try {
                CacheManager.clearAllCacheComplete(requireContext())
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.pref_storage_cache_cleared, Toast.LENGTH_SHORT).show()
                    updateCacheSummary()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error clearing cache", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
