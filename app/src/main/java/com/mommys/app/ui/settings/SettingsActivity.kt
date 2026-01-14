package com.mommys.app.ui.settings

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.mommys.app.R
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.databinding.ActivitySettingsNewBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsNewBinding
    private lateinit var preferencesManager: PreferencesManager
    
    // Track navigation stack for proper back button handling
    private val fragmentStack = mutableListOf<Int>()
    
    companion object {
        const val SECTION_MAIN = 0
        const val SECTION_GENERAL = 1
        const val SECTION_FOLLOWING = 2
        const val SECTION_SEARCHING = 3
        const val SECTION_GRID = 4
        const val SECTION_POST = 5
        const val SECTION_STORAGE = 6
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        preferencesManager = PreferencesManager(this)
        
        // Apply FLAG_SECURE if hideInTasks is enabled (matching original app)
        if (preferencesManager.hideInTasks) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        
        binding = ActivitySettingsNewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        
        if (savedInstanceState == null) {
            navigateToSection(SECTION_MAIN)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }
    
    fun navigateToSection(section: Int) {
        val fragment: Fragment = when (section) {
            SECTION_GENERAL -> GeneralSettingsFragment()
            SECTION_FOLLOWING -> FollowingSettingsFragment()
            SECTION_SEARCHING -> SearchSettingsFragment()
            SECTION_GRID -> GridSettingsFragment()
            SECTION_POST -> PostSettingsFragment()
            SECTION_STORAGE -> StorageSettingsFragment()
            else -> MainSettingsFragment()
        }
        
        val title = when (section) {
            SECTION_GENERAL -> getString(R.string.pref_general)
            SECTION_FOLLOWING -> getString(R.string.pref_following)
            SECTION_SEARCHING -> getString(R.string.pref_searching)
            SECTION_GRID -> getString(R.string.pref_grid_view)
            SECTION_POST -> getString(R.string.pref_post_view)
            SECTION_STORAGE -> getString(R.string.pref_storage)
            else -> getString(R.string.settings_title)
        }
        
        binding.toolbar.title = title
        
        if (section != SECTION_MAIN) {
            fragmentStack.add(section)
        }
        
        supportFragmentManager.commit {
            setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            replace(R.id.fragmentContainer, fragment)
            if (section != SECTION_MAIN) {
                addToBackStack(null)
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            fragmentStack.removeLastOrNull()
            if (fragmentStack.isEmpty()) {
                binding.toolbar.title = getString(R.string.settings_title)
            }
            return true
        }
        return super.onSupportNavigateUp()
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            fragmentStack.removeLastOrNull()
            if (fragmentStack.isEmpty()) {
                binding.toolbar.title = getString(R.string.settings_title)
            }
        } else {
            super.onBackPressed()
        }
    }
}
