package com.mommys.app.ui.settings

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.mommys.app.R
import com.mommys.app.data.api.ApiClient
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.ui.blacklist.BlacklistActivity

class GeneralSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var preferencesManager: PreferencesManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_general, rootKey)
        
        preferencesManager = PreferencesManager(requireContext())
        
        setupLanguagePreference()
        setupHostPreference()
        setupConsentAbove18Preference()
        setupThemePreference()
        setupPostQualityPreference()
        setupThumbQualityPreference()
        setupBlacklistPreferences()
        setupHideInTasksPreference()
        setupDisguisePreference()
        setupStartInSavedPreference()
        setupPinPreferences()
    }

    private fun setupLanguagePreference() {
        val languagePref = findPreference<ListPreference>("general_language")
        languagePref?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            // Language functionality will be implemented separately
        }
    }

    private fun setupHostPreference() {
        val hostPref = findPreference<EditTextPreference>("general_change_host")
        val consentPref = findPreference<SwitchPreferenceCompat>("consent_above_18")
        
        hostPref?.apply {
            // Load current value
            val currentHost = preferencesManager.getHost()
            text = currentHost
            summary = currentHost
            
            setOnBindEditTextListener { editText ->
                editText.hint = "e621.net or e926.net"
            }
            
            setOnPreferenceChangeListener { _, newValue ->
                val host = newValue as String
                handleHostChange(host, this, consentPref)
            }
        }
    }

    private fun handleHostChange(host: String, editTextPref: EditTextPreference, consentPref: SwitchPreferenceCompat?): Boolean {
        return when (host) {
            "e621.net" -> {
                // Original: N(true, true, true) sets filter_rating to show all ratings
                preferencesManager.setFilterRating(true, true, true)
                preferencesManager.setHost(host)
                preferencesManager.setAbove18(true)
                ApiClient.setUseE621(true)
                editTextPref.text = host
                editTextPref.summary = host
                consentPref?.isChecked = true
                true
            }
            "e926.net" -> {
                preferencesManager.setHost(host)
                ApiClient.setUseE621(false)
                editTextPref.text = host
                editTextPref.summary = host
                true
            }
            else -> {
                Toast.makeText(context, R.string.pref_general_edit_host_not_supported, Toast.LENGTH_SHORT).show()
                editTextPref.text = "e926.net"
                preferencesManager.setHost("e926.net")
                false
            }
        }
    }

    private fun setupConsentAbove18Preference() {
        val consentPref = findPreference<SwitchPreferenceCompat>("consent_above_18")
        val hostPref = findPreference<EditTextPreference>("general_change_host")
        
        consentPref?.apply {
            isChecked = preferencesManager.isAbove18()
            
            setOnPreferenceChangeListener { _, newValue ->
                val isAbove18 = newValue as Boolean
                preferencesManager.setAbove18(isAbove18)
                
                if (!isAbove18) {
                    // Force e926 when consent is removed
                    hostPref?.text = "e926.net"
                    hostPref?.summary = "e926.net"
                    preferencesManager.setHost("e926.net")
                    ApiClient.setUseE621(false)
                }
                true
            }
        }
    }

    private fun setupThemePreference() {
        val themePref = findPreference<ListPreference>("general_dark_mode")
        
        themePref?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            
            // Load current value
            val currentTheme = preferencesManager.getThemeMode()
            value = currentTheme.toString()
            setValueIndex(currentTheme.coerceIn(0, 3))
            
            setOnPreferenceChangeListener { _, newValue ->
                val themeValue = (newValue as String).toIntOrNull() ?: 0
                preferencesManager.setThemeMode(themeValue)
                
                val mode = when (themeValue) {
                    0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    3 -> AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(mode)
                
                Toast.makeText(context, R.string.restart_app_for_changes, Toast.LENGTH_SHORT).show()
                true
            }
        }
    }

    private fun setupPostQualityPreference() {
        val qualityPref = findPreference<ListPreference>("general_post_quality")
        
        qualityPref?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            
            val currentQuality = preferencesManager.getPostQuality()
            value = currentQuality.toString()
            
            setOnPreferenceChangeListener { _, newValue ->
                val quality = (newValue as String).toIntOrNull() ?: 1
                preferencesManager.setPostQuality(quality)
                true
            }
        }
    }

    private fun setupThumbQualityPreference() {
        val thumbPref = findPreference<ListPreference>("general_thumb_quality")
        
        thumbPref?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            
            val currentQuality = preferencesManager.getThumbQuality()
            value = currentQuality.toString()
            
            setOnPreferenceChangeListener { _, newValue ->
                val quality = (newValue as String).toIntOrNull() ?: 0
                preferencesManager.setThumbQuality(quality)
                true
            }
        }
    }

    private fun setupBlacklistPreferences() {
        val blacklistEnabledPref = findPreference<SwitchPreferenceCompat>("general_blacklist_enabled")
        val blacklistPref = findPreference<Preference>("general_blacklist")
        val blacklistPoolPostsPref = findPreference<SwitchPreferenceCompat>("general_blacklist_pool_posts")
        
        blacklistEnabledPref?.apply {
            isChecked = preferencesManager.blacklistEnabled
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.blacklistEnabled = newValue as Boolean
                true
            }
        }
        
        blacklistPref?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), BlacklistActivity::class.java))
            true
        }
        
        blacklistPoolPostsPref?.apply {
            isChecked = preferencesManager.blacklistPoolPosts
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.blacklistPoolPosts = newValue as Boolean
                true
            }
        }
    }

    private fun setupHideInTasksPreference() {
        val hideInTasksPref = findPreference<SwitchPreferenceCompat>("general_hide_in_tasks")
        
        hideInTasksPref?.apply {
            isChecked = preferencesManager.hideInTasks
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.hideInTasks = newValue as Boolean
                Toast.makeText(context, R.string.restart_app_for_changes, Toast.LENGTH_SHORT).show()
                true
            }
        }
    }

    private fun setupDisguisePreference() {
        val disguisePref = findPreference<SwitchPreferenceCompat>("general_disguise")
        
        disguisePref?.apply {
            isChecked = preferencesManager.disguiseEnabled
            
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                preferencesManager.disguiseEnabled = enabled
                
                val packageManager = requireContext().packageManager
                val packageName = requireContext().packageName
                
                if (enabled) {
                    // Enable disguise (calculator icon)
                    packageManager.setComponentEnabledSetting(
                        ComponentName(packageName, "$packageName.REGULAR"),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    packageManager.setComponentEnabledSetting(
                        ComponentName(packageName, "$packageName.CALC"),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    Toast.makeText(context, R.string.disguise_message_disguised, Toast.LENGTH_LONG).show()
                } else {
                    // Disable disguise (regular icon)
                    packageManager.setComponentEnabledSetting(
                        ComponentName(packageName, "$packageName.REGULAR"),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    packageManager.setComponentEnabledSetting(
                        ComponentName(packageName, "$packageName.CALC"),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    Toast.makeText(context, R.string.disguise_message_regular, Toast.LENGTH_LONG).show()
                }
                true
            }
        }
    }

    private fun setupStartInSavedPreference() {
        val startInSavedPref = findPreference<SwitchPreferenceCompat>("general_start_in_saved")
        
        startInSavedPref?.apply {
            isChecked = preferencesManager.startInSavedSearches
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.startInSavedSearches = newValue as Boolean
                true
            }
        }
    }

    private fun setupPinPreferences() {
        val pinUnlockPref = findPreference<SwitchPreferenceCompat>("consent_pin_unlock")
        val pinAppLinkPref = findPreference<SwitchPreferenceCompat>("consent_pin_app_link")
        val biometricsPref = findPreference<SwitchPreferenceCompat>("consent_biometrics")
        val pinAutoLockPref = findPreference<SwitchPreferenceCompat>("consent_pin_auto_lock")
        val pinAutoLockInstantlyPref = findPreference<SwitchPreferenceCompat>("consent_pin_auto_lock_instantly")
        
        // PIN unlock (matching original app logic)
        pinUnlockPref?.apply {
            val isPinSet = preferencesManager.isPinSet()
            isChecked = isPinSet
            
            if (isPinSet) {
                val formattedPin = preferencesManager.pinCode
                summary = getString(R.string.pref_consent_pin_unlock_summary_pin, formattedPin)
            }
            
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                if (enabled) {
                    showPinSetupDialog()
                } else {
                    preferencesManager.clearPin()
                    summary = getString(R.string.pref_consent_pin_unlock_summary)
                }
                true
            }
        }
        
        // PIN for app links
        pinAppLinkPref?.apply {
            isChecked = preferencesManager.pinAppLink
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.pinAppLink = newValue as Boolean
                true
            }
        }
        
        // Biometrics
        biometricsPref?.apply {
            isChecked = preferencesManager.useBiometric()
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.setUseBiometric(newValue as Boolean)
                true
            }
        }
        
        // Auto lock
        pinAutoLockPref?.apply {
            isChecked = preferencesManager.pinAutoLock
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.pinAutoLock = newValue as Boolean
                true
            }
        }
        
        // Auto lock instantly
        pinAutoLockInstantlyPref?.apply {
            isChecked = preferencesManager.pinAutoLockInstantly
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.pinAutoLockInstantly = newValue as Boolean
                true
            }
        }
    }

    private fun showPinSetupDialog() {
        val context = requireContext()
        val pinUnlockPref = findPreference<SwitchPreferenceCompat>("consent_pin_unlock")
        
        val editText = EditText(context).apply {
            hint = getString(R.string.pin_set_hint)
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(4))
            setHintTextColor(resources.getColor(R.color.colorDarkGrey, null))
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            inputType = InputType.TYPE_CLASS_NUMBER
            keyListener = DigitsKeyListener.getInstance("0123456789")
        }
        
        AlertDialog.Builder(context)
            .setTitle(R.string.pin_set_title)
            .setMessage(R.string.pin_set_message)
            .setView(editText)
            .setPositiveButton(R.string.save) { _, _ ->
                val pinText = editText.text.toString()
                
                if (pinText.length == 4) {
                    val pinInt = pinText.toInt()
                    preferencesManager.pinValue = pinInt
                    val formattedPin = preferencesManager.pinCode
                    Toast.makeText(context, "Pin code set to $formattedPin!", Toast.LENGTH_LONG).show()
                    pinUnlockPref?.summary = getString(R.string.pref_consent_pin_unlock_summary_pin, formattedPin)
                    pinUnlockPref?.isChecked = true
                } else {
                    pinUnlockPref?.isChecked = false
                    Toast.makeText(context, "PIN must be 4 digits", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                // Keep previous state
                pinUnlockPref?.isChecked = preferencesManager.isPinSet()
            }
            .setNeutralButton(R.string.disable) { _, _ ->
                preferencesManager.clearPin()
                pinUnlockPref?.isChecked = false
                pinUnlockPref?.summary = getString(R.string.pref_consent_pin_unlock_summary)
                Toast.makeText(context, "Pin disabled", Toast.LENGTH_SHORT).show()
            }
            .setOnCancelListener {
                // If dialog is cancelled, keep previous state
                pinUnlockPref?.isChecked = preferencesManager.isPinSet()
            }
            .show()
    }
}
