package com.mommys.app.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mommys.app.R
import com.mommys.app.databinding.ActivityAboutBinding

/**
 * AboutActivity - Shows information about the app
 * Like se/zepiwolf/tws/AboutActivity.java in the original app
 */
class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        // Set build flavour text
        binding.txtFlavour.text = getString(R.string.about_flavour, "Release")
        
        // Terms of Service click - open web browser
        binding.txtToS.setOnClickListener {
            openUrl("https://e621.net/static/terms_of_service")
        }
        
        // Privacy Policy click - open web browser
        binding.txtPP.setOnClickListener {
            openUrl("https://e621.net/static/privacy")
        }
        
        // Send feedback button
        binding.btnSendFeedback.setOnClickListener {
            sendFeedback()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Send feedback via email
     * Like original app's send feedback functionality
     */
    private fun sendFeedback() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName ?: "1.0.0"
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("mommysapp@example.com"))
                putExtra(Intent.EXTRA_SUBJECT, "Mommys feedback - v$versionName ($versionCode)")
                putExtra(Intent.EXTRA_TEXT, "Write your feedback here:\n\n")
            }
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, R.string.no_email_app, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
