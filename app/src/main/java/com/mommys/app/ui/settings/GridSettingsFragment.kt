package com.mommys.app.ui.settings

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.mommys.app.R
import com.mommys.app.data.db.seen.AppSeenDatabase
import com.mommys.app.data.preferences.PreferencesManager as AppPreferencesManager

class GridSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var preferencesManager: AppPreferencesManager
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Configurar para usar el mismo SharedPreferences que PreferencesManager
        preferenceManager.sharedPreferencesName = "user_preferences"
        preferenceManager.sharedPreferencesMode = Context.MODE_PRIVATE
        
        setPreferencesFromResource(R.xml.preferences_grid, rootKey)
        
        preferencesManager = AppPreferencesManager(requireContext())
        
        setupLayoutPreferences()
        setupDisplayPreferences()
        setupViewedPreferences()
        setupBehaviorPreferences()
    }

    private fun setupLayoutPreferences() {
        // Los SeekBarPreference leen y guardan automáticamente del SharedPreferences
        // ya que configuramos preferenceManager.sharedPreferencesName = "user_preferences"
        // No necesitamos listeners adicionales para estas preferencias
    }

    private fun setupDisplayPreferences() {
        val statsPref = findPreference<SwitchPreferenceCompat>("grid_stats")
        val infoPref = findPreference<SwitchPreferenceCompat>("grid_info")
        val newLabelPref = findPreference<SwitchPreferenceCompat>("grid_new_label")
        val coloursPref = findPreference<SwitchPreferenceCompat>("grid_colours")
        val gifsPref = findPreference<SwitchPreferenceCompat>("grid_gifs")
        
        statsPref?.isChecked = preferencesManager.gridStats
        statsPref?.setOnPreferenceChangeListener { _, newValue ->
            preferencesManager.gridStats = newValue as Boolean
            true
        }
        
        infoPref?.isChecked = preferencesManager.gridInfo
        infoPref?.setOnPreferenceChangeListener { _, newValue ->
            preferencesManager.gridInfo = newValue as Boolean
            true
        }
        
        newLabelPref?.isChecked = preferencesManager.gridNewLabel
        newLabelPref?.setOnPreferenceChangeListener { _, newValue ->
            preferencesManager.gridNewLabel = newValue as Boolean
            true
        }
        
        coloursPref?.isChecked = preferencesManager.gridColours
        coloursPref?.setOnPreferenceChangeListener { _, newValue ->
            preferencesManager.gridColours = newValue as Boolean
            true
        }
        
        gifsPref?.isChecked = preferencesManager.gridGifs
        gifsPref?.setOnPreferenceChangeListener { _, newValue ->
            preferencesManager.gridGifs = newValue as Boolean
            true
        }
    }

    private fun setupViewedPreferences() {
        val darkenSeenPref = findPreference<SwitchPreferenceCompat>("grid_darken_seen")
        val hideSeenPref = findPreference<SwitchPreferenceCompat>("grid_hide_seen")
        val clearPref = findPreference<Preference>("grid_darken_clear")
        
        darkenSeenPref?.isChecked = preferencesManager.gridDarkenSeen
        darkenSeenPref?.setOnPreferenceChangeListener { _, newValue ->
            preferencesManager.gridDarkenSeen = newValue as Boolean
            true
        }
        
        hideSeenPref?.isChecked = preferencesManager.gridHideSeen
        hideSeenPref?.setOnPreferenceChangeListener { _, newValue ->
            preferencesManager.gridHideSeen = newValue as Boolean
            true
        }
        
        // Load seen count and update summary (like original app)
        clearPref?.let { pref ->
            updateSeenCount(pref)
            
            pref.setOnPreferenceClickListener {
                // Clear seen posts from both database and PreferencesManager
                AppSeenDatabase.clearAll(requireContext()) {
                    preferencesManager.clearViewedPosts()
                    handler.post {
                        Toast.makeText(context, R.string.pref_grid_darken_cleared, Toast.LENGTH_SHORT).show()
                        updateSeenCount(pref)
                    }
                }
                true
            }
        }
    }
    
    /**
     * Update the seen count summary for the clear preference.
     * This runs in background thread and updates UI on main thread.
     */
    private fun updateSeenCount(preference: Preference) {
        Thread {
            val count = AppSeenDatabase.getSeenCountSync(requireContext())
            handler.postDelayed({
                preference.summary = getString(R.string.pref_grid_darken_clear_count_summary, count)
            }, 10) // Small delay like original app
        }.start()
    }

    private fun setupBehaviorPreferences() {
        val refreshPref = findPreference<SwitchPreferenceCompat>("grid_refresh")
        val navigatePref = findPreference<SwitchPreferenceCompat>("grid_navigate")
        
        refreshPref?.isChecked = preferencesManager.gridRefresh
        refreshPref?.setOnPreferenceChangeListener { _, newValue ->
            preferencesManager.gridRefresh = newValue as Boolean
            true
        }
        
        navigatePref?.isChecked = preferencesManager.gridNavigate
        navigatePref?.setOnPreferenceChangeListener { _, newValue ->
            preferencesManager.gridNavigate = newValue as Boolean
            true
        }
    }
}
