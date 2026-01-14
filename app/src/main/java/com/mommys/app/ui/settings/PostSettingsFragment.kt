package com.mommys.app.ui.settings

import android.content.Context
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.mommys.app.R
import com.mommys.app.data.preferences.PreferencesManager

/**
 * Fragment para Post View Customization settings.
 * 
 * Configurado para usar el mismo SharedPreferences file ("user_preferences")
 * que PreferencesManager, así las preferencias persisten automáticamente.
 */
class PostSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var preferencesManager: PreferencesManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // IMPORTANTE: Configurar el mismo SharedPreferences que usa PreferencesManager
        // para que los cambios en las preferencias se reflejen correctamente
        preferenceManager.sharedPreferencesName = "user_preferences"
        preferenceManager.sharedPreferencesMode = Context.MODE_PRIVATE
        
        setPreferencesFromResource(R.xml.preferences_post, rootKey)
        
        preferencesManager = PreferencesManager(requireContext())
        
        // Las preferencias ya se leen/guardan automáticamente del SharedPreferences
        // Solo necesitamos configurar ListPreference summaries
        setupListPreferenceSummaries()
    }
    
    private fun setupListPreferenceSummaries() {
        // Back button location
        findPreference<ListPreference>("post_back_button_location")?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }
        
        // Video quality
        findPreference<ListPreference>("post_default_video_quality")?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }
        
        // Video format
        findPreference<ListPreference>("post_default_video_format")?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }
    }
}
