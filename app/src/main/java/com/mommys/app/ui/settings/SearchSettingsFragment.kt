package com.mommys.app.ui.settings

import android.os.Bundle
import android.provider.SearchRecentSuggestions
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.mommys.app.R
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.data.search.SearchSuggestionsProvider

class SearchSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var preferencesManager: PreferencesManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_searching, rootKey)
        
        preferencesManager = PreferencesManager(requireContext())
        
        setupSearchHistory()
        setupSearchSuggestions()
        setupSearchSavedNewWindow()
        setupSearchLastOnStart()
        setupSearchInNewTask()
        setupGridFavOrder()
        setupSearchNewestFirst()
        setupSearchIncludeFlash()
    }

    private fun setupSearchHistory() {
        findPreference<SwitchPreferenceCompat>("search_history")?.apply {
            isChecked = preferencesManager.searchHistory
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                preferencesManager.searchHistory = enabled
                
                // Clear search history when disabled (like original app)
                if (!enabled) {
                    clearRecentSearchSuggestions()
                }
                true
            }
        }
    }

    private fun setupSearchSuggestions() {
        findPreference<SwitchPreferenceCompat>("search_suggestions")?.apply {
            isChecked = preferencesManager.searchSuggestions
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.searchSuggestions = newValue as Boolean
                true
            }
        }
    }

    private fun setupSearchSavedNewWindow() {
        findPreference<SwitchPreferenceCompat>("search_saved_new_window")?.apply {
            isChecked = preferencesManager.searchSavedNewWindow
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.searchSavedNewWindow = newValue as Boolean
                true
            }
        }
    }

    private fun setupSearchLastOnStart() {
        findPreference<SwitchPreferenceCompat>("search_last_on_start")?.apply {
            isChecked = preferencesManager.searchLastOnStart
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.searchLastOnStart = newValue as Boolean
                true
            }
        }
    }

    private fun setupSearchInNewTask() {
        findPreference<SwitchPreferenceCompat>("search_in_new_task")?.apply {
            isChecked = preferencesManager.searchInNewTask
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.searchInNewTask = newValue as Boolean
                true
            }
        }
    }

    private fun setupGridFavOrder() {
        findPreference<SwitchPreferenceCompat>("grid_fav_order")?.apply {
            isChecked = preferencesManager.gridFavOrder
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.gridFavOrder = newValue as Boolean
                true
            }
        }
    }

    private fun setupSearchNewestFirst() {
        findPreference<SwitchPreferenceCompat>("search_newest_first")?.apply {
            isChecked = preferencesManager.searchNewestFirst
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.searchNewestFirst = newValue as Boolean
                true
            }
        }
    }

    private fun setupSearchIncludeFlash() {
        findPreference<SwitchPreferenceCompat>("search_include_flash")?.apply {
            isChecked = preferencesManager.searchIncludeFlash
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.searchIncludeFlash = newValue as Boolean
                true
            }
        }
    }

    private fun clearRecentSearchSuggestions() {
        try {
            SearchRecentSuggestions(
                requireContext(),
                SearchSuggestionsProvider.AUTHORITY,
                SearchSuggestionsProvider.MODE
            ).clearHistory()
        } catch (e: Exception) {
            // Ignore errors
        }
    }
}
