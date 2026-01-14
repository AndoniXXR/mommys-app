package com.mommys.app.ui.launcher

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.mommys.app.MommysApplication
import com.mommys.app.R
import com.mommys.app.databinding.ActivityLauncherBinding
import com.mommys.app.ui.main.MainActivity
import com.mommys.app.ui.pincode.PinCodeActivity

/**
 * LauncherActivity - Primera pantalla de la app
 * Basada en se.zepiwolf.tws.LauncherActivity
 * 
 * Muestra los términos de servicio y permite seleccionar idioma
 */
class LauncherActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLauncherBinding
    private val prefs by lazy { MommysApplication.getInstance().preferencesManager }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Si ya aceptó los términos, ir directo a la app
        if (prefs.hasAccepted()) {
            navigateToApp()
            return
        }
        
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViews()
    }
    
    private fun setupViews() {
        // Configurar spinner de idiomas
        val languages = resources.getStringArray(R.array.languages)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            languages
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = adapter
        
        binding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val langCode = when (position) {
                    0 -> "en"
                    1 -> "es"
                    2 -> "pt"
                    else -> "en"
                }
                prefs.setLanguage(langCode)
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Checkbox para aceptar términos
        binding.checkboxAccept.setOnCheckedChangeListener { _, isChecked ->
            binding.btnStart.isEnabled = isChecked
            binding.btnStart.alpha = if (isChecked) 1f else 0.5f
        }
        
        // Botón iniciar deshabilitado por defecto
        binding.btnStart.isEnabled = false
        binding.btnStart.alpha = 0.5f
        
        // Click en términos de servicio
        binding.txtTerms.setOnClickListener {
            // TODO: Mostrar términos de servicio
        }
        
        // Click en política de privacidad
        binding.txtPrivacy.setOnClickListener {
            // TODO: Mostrar política de privacidad
        }
        
        // Botón de iniciar
        binding.btnStart.setOnClickListener {
            prefs.setAccepted(true)
            navigateToApp()
        }
        
        // Mostrar detalles de la app
        binding.txtAppDetails.text = getString(R.string.app_details)
    }
    
    private fun navigateToApp() {
        val intent = if (prefs.isPinSet()) {
            Intent(this, PinCodeActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }
        startActivity(intent)
        finish()
        overridePendingTransition(0, 0)
    }
}
