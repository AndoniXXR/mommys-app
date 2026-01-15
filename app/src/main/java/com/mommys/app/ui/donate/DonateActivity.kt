package com.mommys.app.ui.donate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.mommys.app.R

/**
 * DonateActivity - Shows donation options
 * 
 * IMPORTANTE: Esta activity es para Mommys App, NO para la app original.
 * Las URLs deben ser personalizadas según el desarrollador de esta app.
 */
class DonateActivity : AppCompatActivity() {

    companion object {
        // URLs de donación - PERSONALIZAR según el desarrollador
        // Estas son URLs de ejemplo, deben reemplazarse con las reales
        private const val URL_PAYPAL = "https://www.paypal.me/"
        private const val URL_PATREON = "https://www.patreon.com/"
        private const val URL_KOFI = "https://ko-fi.com/"
        private const val URL_GITHUB = "https://github.com/sponsors/"
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var txtDonations: TextView
    private lateinit var txtLeaderboard: TextView
    private lateinit var btnPayPal: Button
    private lateinit var btnPatreon: Button
    private lateinit var btnKofi: Button
    private lateinit var btnGitHub: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_donate)

        // Initialize views
        toolbar = findViewById(R.id.toolbar)
        txtDonations = findViewById(R.id.txtDonations)
        txtLeaderboard = findViewById(R.id.txtLeaderboard)
        btnPayPal = findViewById(R.id.btnPayPal)
        btnPatreon = findViewById(R.id.btnPatreon)
        btnKofi = findViewById(R.id.btnKofi)
        btnGitHub = findViewById(R.id.btnGitHub)

        // Setup toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.donate_title)

        // Setup button click listeners
        setupButtons()

        // Load donators data (placeholder por ahora)
        loadDonators()
    }

    private fun setupButtons() {
        btnPayPal.setOnClickListener { openUrl(URL_PAYPAL) }
        btnPatreon.setOnClickListener { openUrl(URL_PATREON) }
        btnKofi.setOnClickListener { openUrl(URL_KOFI) }
        btnGitHub.setOnClickListener { openUrl(URL_GITHUB) }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadDonators() {
        // Mostrar mensaje de agradecimiento en lugar de lista de donadores
        // ya que esta es una app diferente a la original
        txtDonations.text = getString(R.string.donate_thanks_message)
        txtLeaderboard.text = getString(R.string.donate_support_message)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
