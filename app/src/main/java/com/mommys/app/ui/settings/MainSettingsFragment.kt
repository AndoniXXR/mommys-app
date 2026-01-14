package com.mommys.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.mommys.app.R
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.util.ExportImportManager

/**
 * Fragment principal de Settings
 * Basado en ui/c.java de la app original
 */
class MainSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var preferencesManager: PreferencesManager
    
    // Si el export debe ser encriptado
    private var exportEncrypted = true
    
    // Datos pendientes para export
    private var pendingExportData: org.json.JSONObject? = null
    
    // Launcher para crear archivo de export
    private val exportFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/*")
    ) { uri: Uri? ->
        uri?.let { writeExportFile(it) }
    }
    
    // Launcher para seleccionar archivo de import
    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { importFromFile(it) }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey)
        
        preferencesManager = PreferencesManager(requireContext())
        
        setupNavigationPreferences()
        setupExportPreferences()
        setupCloudflarePreferences()
        setupAdsPreferences()
        setupPrivacyPreferences()
        setupNetworkPreferences()
    }

    private fun setupNavigationPreferences() {
        findPreference<Preference>("menu_general")?.setOnPreferenceClickListener {
            (activity as? SettingsActivity)?.navigateToSection(SettingsActivity.SECTION_GENERAL)
            true
        }

        findPreference<Preference>("menu_follow_tags")?.setOnPreferenceClickListener {
            (activity as? SettingsActivity)?.navigateToSection(SettingsActivity.SECTION_FOLLOWING)
            true
        }

        findPreference<Preference>("menu_search_and_tags")?.setOnPreferenceClickListener {
            (activity as? SettingsActivity)?.navigateToSection(SettingsActivity.SECTION_SEARCHING)
            true
        }

        findPreference<Preference>("menu_grid_view")?.setOnPreferenceClickListener {
            (activity as? SettingsActivity)?.navigateToSection(SettingsActivity.SECTION_GRID)
            true
        }

        findPreference<Preference>("menu_post_view")?.setOnPreferenceClickListener {
            (activity as? SettingsActivity)?.navigateToSection(SettingsActivity.SECTION_POST)
            true
        }

        findPreference<Preference>("menu_storage_and_downloads")?.setOnPreferenceClickListener {
            (activity as? SettingsActivity)?.navigateToSection(SettingsActivity.SECTION_STORAGE)
            true
        }
    }

    /**
     * Configura Export & Import
     * Como ui/c.java cases 6 y 7
     */
    private fun setupExportPreferences() {
        // Export - muestra diálogo preguntando si encriptar
        findPreference<Preference>("export_export")?.setOnPreferenceClickListener {
            showExportDialog()
            true
        }

        // Import - abre file picker
        findPreference<Preference>("export_import")?.setOnPreferenceClickListener {
            importFileLauncher.launch(arrayOf("*/*"))
            true
        }
    }
    
    /**
     * Muestra el diálogo de export preguntando si encriptar
     * Como ui/a.java case 6
     */
    private fun showExportDialog() {
        val context = requireContext()
        
        AlertDialog.Builder(context)
            .setTitle(R.string.pref_export_encrypt_dialog_title)
            .setMessage(R.string.pref_export_encrypt_dialog_message)
            .setPositiveButton(R.string.encrypt) { _, _ ->
                startExport(encrypted = true)
            }
            .setNegativeButton(R.string.no) { _, _ ->
                startExport(encrypted = false)
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }
    
    /**
     * Inicia el proceso de export
     */
    private fun startExport(encrypted: Boolean) {
        exportEncrypted = encrypted
        
        // Generar datos de export
        pendingExportData = ExportImportManager.generateExportData(requireContext())
        
        if (pendingExportData == null) {
            Toast.makeText(context, R.string.export_error, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Abrir file picker para guardar
        val fileName = ExportImportManager.generateBackupFileName(encrypted)
        exportFileLauncher.launch(fileName)
    }
    
    /**
     * Escribe los datos de export al archivo seleccionado
     */
    private fun writeExportFile(uri: Uri) {
        val data = pendingExportData ?: return
        
        val result = ExportImportManager.writeExportToUri(
            requireContext(),
            uri,
            data,
            exportEncrypted
        )
        
        when (result) {
            is ExportImportManager.Result.Success -> {
                val fileName = ExportImportManager.getFileNameFromUri(requireContext(), uri) ?: "backup"
                Toast.makeText(context, getString(R.string.export_success, fileName), Toast.LENGTH_LONG).show()
            }
            is ExportImportManager.Result.Error -> {
                Toast.makeText(context, getString(R.string.export_error, result.message), Toast.LENGTH_LONG).show()
            }
        }
        
        pendingExportData = null
    }
    
    /**
     * Importa datos desde un archivo seleccionado
     * Como ui/c.java z() cases 34 y 35
     */
    private fun importFromFile(uri: Uri) {
        val fileName = ExportImportManager.getFileNameFromUri(requireContext(), uri) ?: ""
        
        // Determinar si está encriptado basado en la extensión
        val encrypted = ExportImportManager.isEncryptedFile(fileName)
        
        if (encrypted == null) {
            Toast.makeText(context, R.string.import_invalid_file, Toast.LENGTH_SHORT).show()
            return
        }
        
        val result = ExportImportManager.importFromUri(requireContext(), uri, encrypted)
        
        when (result) {
            is ExportImportManager.Result.Success -> {
                Toast.makeText(context, R.string.import_success, Toast.LENGTH_LONG).show()
                // Reiniciar la app para aplicar cambios
                restartApp()
            }
            is ExportImportManager.Result.Error -> {
                Toast.makeText(context, getString(R.string.import_error, result.message), Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Reinicia la app para aplicar los cambios importados
     */
    private fun restartApp() {
        val intent = requireActivity().packageManager.getLaunchIntentForPackage(requireActivity().packageName)
        intent?.let { 
            it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(it)
        }
        requireActivity().finish()
    }

    /**
     * Configura Cookies & Cloudflare
     * Como ui/a.java case 8
     */
    private fun setupCloudflarePreferences() {
        findPreference<Preference>("cloudflare")?.setOnPreferenceClickListener {
            // Abre WebViewActivity para obtener cookies
            val intent = Intent(context, CookieWebViewActivity::class.java)
            startActivity(intent)
            true
        }
    }

    /**
     * Configura Optional Ads
     * Como ui/c.java - keys con "3" al final
     * Default: banner_grid=true, banner_post=true, interstitial=false
     * persistent="false" en el XML, se manejan manualmente aquí
     */
    private fun setupAdsPreferences() {
        // ads_banner_grid3 - default true
        findPreference<SwitchPreferenceCompat>("ads_banner_grid3")?.apply {
            isChecked = preferencesManager.adsBannerGrid
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.adsBannerGrid = newValue as Boolean
                true
            }
        }

        // ads_banner_post3 - default true
        findPreference<SwitchPreferenceCompat>("ads_banner_post3")?.apply {
            isChecked = preferencesManager.adsBannerPost
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.adsBannerPost = newValue as Boolean
                true
            }
        }

        // ads_interstitial3 - default false
        findPreference<SwitchPreferenceCompat>("ads_interstitial3")?.apply {
            isChecked = preferencesManager.adsInterstitial
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.adsInterstitial = newValue as Boolean
                true
            }
        }

        findPreference<Preference>("ads_form")?.setOnPreferenceClickListener {
            // Como la app original: usa AppLovin CMP service
            // Por ahora mostramos un mensaje
            Toast.makeText(context, "Ad consent form not available", Toast.LENGTH_SHORT).show()
            true
        }
    }

    /**
     * Configura Privacy
     * Como ui/a.java cases 13 y 14
     * Muestra toast "Restart app for changes to take effect"
     * persistent="false" en el XML, se manejan manualmente aquí
     */
    private fun setupPrivacyPreferences() {
        findPreference<SwitchPreferenceCompat>("privacy_crash_reports")?.apply {
            isChecked = preferencesManager.privacyCrashReports
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.privacyCrashReports = newValue as Boolean
                Toast.makeText(context, R.string.restart_app_for_changes, Toast.LENGTH_LONG).show()
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("privacy_analytics")?.apply {
            isChecked = preferencesManager.privacyAnalytics
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.privacyAnalytics = newValue as Boolean
                Toast.makeText(context, R.string.restart_app_for_changes, Toast.LENGTH_LONG).show()
                true
            }
        }
    }

    /**
     * Configura Network/Proxy
     * Como com/applovin/impl/sdk/ad/f.java case 17
     * Muestra diálogo con host, port, username, password
     */
    private fun setupNetworkPreferences() {
        val proxyPref = findPreference<SwitchPreferenceCompat>("proxy")
        
        proxyPref?.apply {
            // Actualizar estado basado en si hay proxy configurado
            val proxyConfig = preferencesManager.getProxyConfig()
            isChecked = proxyConfig != null
            
            // Actualizar summary si hay proxy
            if (proxyConfig != null) {
                summary = getString(R.string.pref_network_proxy_summary_, proxyConfig.host, proxyConfig.port)
            } else {
                summary = getString(R.string.pref_network_proxy_summary)
            }
            
            setOnPreferenceClickListener {
                showProxyDialog()
                true
            }
        }
    }
    
    /**
     * Muestra el diálogo de configuración de proxy
     * Como en la app original con host, port, username, password
     */
    private fun showProxyDialog() {
        val context = requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_proxy, null)
        
        val etHost = dialogView.findViewById<EditText>(R.id.etProxyHost)
        val etPort = dialogView.findViewById<EditText>(R.id.etProxyPort)
        val etUsername = dialogView.findViewById<EditText>(R.id.etProxyUsername)
        val etPassword = dialogView.findViewById<EditText>(R.id.etProxyPassword)
        
        // Cargar configuración actual si existe
        val currentConfig = preferencesManager.getProxyConfig()
        if (currentConfig != null) {
            etHost.setText(currentConfig.host)
            etPort.setText(currentConfig.port.toString())
            etUsername.setText(currentConfig.username ?: "")
            etPassword.setText(currentConfig.password ?: "")
        }
        
        AlertDialog.Builder(context)
            .setTitle(R.string.pref_network_proxy_title)
            .setMessage(R.string.pref_network_proxy_summary)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val host = etHost.text.toString().trim()
                val portStr = etPort.text.toString().trim()
                val username = etUsername.text.toString().trim().ifEmpty { null }
                val password = etPassword.text.toString().trim().ifEmpty { null }
                
                val port = try {
                    portStr.toInt()
                } catch (e: NumberFormatException) {
                    -1
                }
                
                saveProxyConfig(host, port, username, password)
            }
            .setNegativeButton(R.string.disable) { _, _ ->
                // Deshabilitar proxy
                saveProxyConfig(null, -1, null, null)
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }
    
    /**
     * Guarda la configuración del proxy y actualiza la UI
     */
    private fun saveProxyConfig(host: String?, port: Int, username: String?, password: String?) {
        val proxyPref = findPreference<SwitchPreferenceCompat>("proxy")
        
        if (host.isNullOrEmpty() || port < 0 || port > 65535 || !host.contains(".")) {
            // Deshabilitar proxy
            preferencesManager.setProxyConfig(null)
            proxyPref?.isChecked = false
            proxyPref?.summary = getString(R.string.pref_network_proxy_summary)
        } else {
            // Guardar proxy
            val config = PreferencesManager.ProxyConfig(host, port, username, password)
            preferencesManager.setProxyConfig(config)
            proxyPref?.isChecked = true
            proxyPref?.summary = getString(R.string.pref_network_proxy_summary_, host, port)
        }
        
        Toast.makeText(context, R.string.restart_app_for_changes, Toast.LENGTH_LONG).show()
    }
}
