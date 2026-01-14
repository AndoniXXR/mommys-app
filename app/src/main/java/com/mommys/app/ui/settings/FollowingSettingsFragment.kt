package com.mommys.app.ui.settings

import android.Manifest
import android.app.Dialog
import android.app.job.JobScheduler
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.mommys.app.R
import com.mommys.app.data.db.logs.AppLogDatabase
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.service.FollowingJobService
import com.mommys.app.ui.LogActivity

class FollowingSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var preferencesManager: PreferencesManager
    
    // Permission request launcher for Android 13+ notification permission
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, schedule job
            scheduleFollowingJob()
            AppLogDatabase.log(requireContext(), "Following enabled")
        } else {
            // Permission denied, disable the switch
            preferencesManager.followingEnabled = false
            findPreference<SwitchPreferenceCompat>("following_enabled")?.isChecked = false
            Toast.makeText(
                requireContext(), 
                R.string.notification_permission_required, 
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    companion object {
        private const val JOB_ID_FOLLOWING = 94174434 // Same as original app
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_following, rootKey)
        preferencesManager = PreferencesManager(requireContext())
        
        setupPreferences()
    }

    private fun setupPreferences() {
        setupFollowingEnabled()
        setupManageTags()
        setupOnlyWifi()
        setupPeriod()
        setupDisplayTag()
        setupDisplayInSavedSearch()
        setupViewLog()
    }
    
    private fun setupFollowingEnabled() {
        findPreference<SwitchPreferenceCompat>("following_enabled")?.apply {
            isChecked = preferencesManager.followingEnabled
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                preferencesManager.followingEnabled = enabled
                
                if (!enabled) {
                    // Cancel job when disabled (like original)
                    cancelFollowingJob()
                    AppLogDatabase.log(requireContext(), "Following disabled")
                } else {
                    // Check notification permission on Android 13+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                requireContext(),
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            // Request permission
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return@setOnPreferenceChangeListener true
                        }
                    }
                    // Schedule job when enabled
                    scheduleFollowingJob()
                    AppLogDatabase.log(requireContext(), "Following enabled")
                }
                true
            }
        }
    }
    
    private fun setupManageTags() {
        findPreference<Preference>("following_manage")?.apply {
            setOnPreferenceClickListener {
                showManageTagsDialog()
                true
            }
        }
    }
    
    private fun setupOnlyWifi() {
        findPreference<SwitchPreferenceCompat>("following_only_wifi")?.apply {
            isChecked = preferencesManager.followingOnlyWifi
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.followingOnlyWifi = newValue as Boolean
                // Reschedule job if enabled to apply new network constraint
                if (preferencesManager.followingEnabled) {
                    scheduleFollowingJob(forceReschedule = true)
                }
                true
            }
        }
    }
    
    private fun setupPeriod() {
        findPreference<ListPreference>("following_period")?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            val currentPeriod = preferencesManager.followingPeriod
            setValueIndex(currentPeriod.coerceIn(0, 5))  // 0-5 for 6 period options (like original app)
            
            setOnPreferenceChangeListener { _, newValue ->
                val period = (newValue as String).toIntOrNull() ?: 1
                preferencesManager.followingPeriod = period
                // Reschedule job if enabled to apply new period
                if (preferencesManager.followingEnabled) {
                    scheduleFollowingJob(forceReschedule = true)
                }
                true
            }
        }
    }
    
    private fun setupDisplayTag() {
        findPreference<SwitchPreferenceCompat>("following_display_tag")?.apply {
            isChecked = preferencesManager.followingDisplayTag
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.followingDisplayTag = newValue as Boolean
                true
            }
        }
    }
    
    private fun setupDisplayInSavedSearch() {
        findPreference<SwitchPreferenceCompat>("following_display_in_saved_search")?.apply {
            isChecked = preferencesManager.followingDisplayInSavedSearch
            setOnPreferenceChangeListener { _, newValue ->
                preferencesManager.followingDisplayInSavedSearch = newValue as Boolean
                true
            }
        }
    }
    
    private fun setupViewLog() {
        findPreference<Preference>("following_view_log")?.apply {
            setOnPreferenceClickListener {
                // Open LogActivity like original app
                startActivity(Intent(requireContext(), LogActivity::class.java))
                true
            }
        }
    }
    
    private fun showManageTagsDialog() {
        val context = requireContext()
        
        // Use custom layout like original app (dialog_follow_tags.xml)
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_follow_tags, null)
        dialog.setContentView(view)
        
        val editText = view.findViewById<EditText>(R.id.editText)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnSave = view.findViewById<Button>(R.id.btnSave)
        
        // Load current tags
        editText.setText(preferencesManager.followingTags)
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnSave.setOnClickListener {
            val tags = editText.text.toString().trim()
            preferencesManager.followingTags = tags
            Toast.makeText(context, R.string.save, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun cancelFollowingJob() {
        FollowingJobService.cancel(requireContext())
    }
    
    private fun scheduleFollowingJob(forceReschedule: Boolean = false) {
        FollowingJobService.schedule(requireContext(), forceReschedule)
    }
}
