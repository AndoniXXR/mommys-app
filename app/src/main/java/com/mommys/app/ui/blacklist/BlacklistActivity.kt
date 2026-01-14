package com.mommys.app.ui.blacklist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.mommys.app.R
import com.mommys.app.data.preferences.PreferencesManager

/**
 * BlacklistActivity - Exactly like the original decompiled app
 * Uses EditText with multi-line text input for blacklist entries
 * Each line represents a blacklist entry (can contain multiple tags)
 */
class BlacklistActivity : AppCompatActivity() {

    private lateinit var editTextBlacklist: EditText
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blacklist_import_export)
        
        // Setup toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.blacklist)
        
        editTextBlacklist = findViewById(R.id.eTBlacklist)
        preferencesManager = PreferencesManager(this)
        
        // Hide keyboard initially
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        inputMethodManager?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        editTextBlacklist.clearFocus()
        
        // Load current blacklist
        loadBlacklist()
    }

    private fun loadBlacklist() {
        val blacklist = preferencesManager.getBlacklistRaw()
        editTextBlacklist.setText(blacklist)
    }

    private fun saveBlacklist() {
        val text = editTextBlacklist.text.toString().trim()
        preferencesManager.setBlacklistRaw(text)
        Toast.makeText(this, R.string.blacklist_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_blacklist, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.save_blacklist -> {
                saveBlacklist()
                true
            }
            R.id.copy -> {
                copyToClipboard()
                true
            }
            R.id.share_plain_text -> {
                shareAsPlainText()
                true
            }
            R.id.to_file -> {
                // TODO: Implement save to file
                Toast.makeText(this, "TODO", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.from_file -> {
                // TODO: Implement import from file
                Toast.makeText(this, "TODO", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.help -> {
                showHelpDialog()
                true
            }
            R.id.order_alphabetically -> {
                orderAlphabetically()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun copyToClipboard() {
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
        val clipData = ClipData.newPlainText("Blacklist", editTextBlacklist.text.toString().trim())
        clipboardManager?.setPrimaryClip(clipData)
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun shareAsPlainText() {
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, editTextBlacklist.text.toString().trim())
            type = "text/plain"
        }
        startActivity(Intent.createChooser(intent, "Share blacklist"))
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.blacklist_menu_help)
            .setMessage(R.string.blacklist_menu_help_text)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun orderAlphabetically() {
        val text = editTextBlacklist.text.toString().trim()
        val lines = text.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .sorted()
        
        val sortedText = lines.joinToString("\n")
        editTextBlacklist.setText(sortedText)
        
        // Also save to preferences
        preferencesManager.setBlacklistRaw(sortedText)
    }
}
