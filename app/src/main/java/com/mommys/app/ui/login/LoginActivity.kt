package com.mommys.app.ui.login

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mommys.app.R
import com.mommys.app.data.api.ApiClient
import com.mommys.app.data.api.ApiService
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.databinding.ActivityLoginBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * LoginActivity - Pantalla de inicio de sesión
 * 
 * Implementa el flujo de login similar a la app original:
 * 1. Usuario ingresa username y API key
 * 2. Se validan las credenciales con la API (POST a votes endpoint)
 * 3. Si son correctas (200 o 422) se guardan
 * 4. Si son incorrectas (401/403) se muestra error con opción "Login anyway"
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var preferencesManager: PreferencesManager
    private val api: ApiService by lazy { ApiClient.apiService }
    
    // Credenciales temporales durante validación
    private var pendingUsername: String? = null
    private var pendingApiKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        preferencesManager = PreferencesManager(this)
        
        // Configurar subtítulo con instrucciones
        binding.txtLoginSubtitle.text = getString(R.string.login_subtitle_instructions)
        
        // Prellenar si ya hay credenciales guardadas
        val savedUsername = preferencesManager.getUsername()
        val savedApiKey = preferencesManager.getApiKey()
        if (savedUsername != null) {
            binding.editUsername.setText(savedUsername)
            if (savedApiKey != null) {
                binding.editApiKey.setText(savedApiKey)
            }
        }
        
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            attemptLogin()
        }
        
        binding.btnCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    /**
     * Intenta hacer login validando las credenciales con la API
     */
    private fun attemptLogin() {
        // Obtener y limpiar input
        val rawUsername = binding.editUsername.text.toString()
        val rawApiKey = binding.editApiKey.text.toString()
        
        // Normalizar credenciales (como la app original)
        val username = normalizeUsername(rawUsername)
        val apiKey = normalizeApiKey(rawApiKey)
        
        // Validación local
        if (username.isEmpty()) {
            showErrorDialog(
                getString(R.string.error),
                getString(R.string.login_username_empty)
            )
            return
        }
        
        if (apiKey.isEmpty()) {
            showErrorDialog(
                getString(R.string.error),
                getString(R.string.login_api_key_empty)
            )
            return
        }
        
        // Limpiar errores previos
        binding.tilUsername.error = null
        binding.tilApiKey.error = null
        
        // Guardar credenciales pendientes
        pendingUsername = username
        pendingApiKey = apiKey
        
        // Mostrar loading y deshabilitar inputs
        setLoading(true)
        
        // Validar con la API
        validateCredentials(username, apiKey)
    }

    /**
     * Normaliza el username como la app original:
     * - Convierte a minúsculas
     * - Elimina saltos de línea
     * - Elimina tabs
     * - Trim
     * - Reemplaza espacios por _
     */
    private fun normalizeUsername(input: String): String {
        return input
            .lowercase(Locale.ENGLISH)
            .replace("\r\n", "\n")
            .replace("\n", "")
            .replace("\t", "")
            .trim()
            .replace(" ", "_")
    }

    /**
     * Normaliza el API key:
     * - Elimina saltos de línea
     * - Elimina tabs
     * - Trim
     * - Reemplaza espacios por _
     */
    private fun normalizeApiKey(input: String): String {
        return input
            .replace("\r\n", "\n")
            .replace("\n", "")
            .replace("\t", "")
            .trim()
            .replace(" ", "_")
    }

    /**
     * Valida las credenciales haciendo un POST a la API
     * Usa el mismo método que la app original: votar un post con score=0
     */
    private fun validateCredentials(username: String, apiKey: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = api.validateCredentials(
                    score = 0,
                    login = username,
                    apiKey = apiKey
                )
                
                withContext(Dispatchers.Main) {
                    handleValidationResponse(response.code(), response.message())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    handleValidationError(e.message ?: "Unknown error")
                }
            }
        }
    }

    /**
     * Maneja la respuesta de validación
     * 200 o 422 = credenciales correctas (422 significa que ya votó, pero las creds son válidas)
     * 401/403 = credenciales incorrectas
     */
    private fun handleValidationResponse(code: Int, message: String?) {
        setLoading(false)
        
        when (code) {
            200, 422 -> {
                // Login exitoso
                onLoginSuccess()
            }
            401, 403 -> {
                // Credenciales incorrectas
                showLoginErrorDialog(
                    getString(R.string.login_error_title),
                    getString(R.string.login_error_wrong_credentials)
                )
            }
            else -> {
                // Otro error
                showLoginErrorDialog(
                    getString(R.string.login_error_title),
                    getString(R.string.login_error_message, "$code: ${message ?: "Unknown"}")
                )
            }
        }
    }

    /**
     * Maneja errores de conexión
     */
    private fun handleValidationError(error: String) {
        setLoading(false)
        showLoginErrorDialog(
            getString(R.string.login_error_title),
            getString(R.string.login_error_network, error)
        )
    }

    /**
     * Login exitoso - guarda credenciales y termina
     */
    private fun onLoginSuccess() {
        val username = pendingUsername ?: return
        val apiKey = pendingApiKey ?: return
        
        // Guardar credenciales
        preferencesManager.setCredentials(username, apiKey)
        
        // Limpiar pendientes
        pendingUsername = null
        pendingApiKey = null
        
        // Mostrar toast de éxito
        Toast.makeText(
            this,
            getString(R.string.login_success_toast, username),
            Toast.LENGTH_SHORT
        ).show()
        
        // Devolver resultado OK
        setResult(RESULT_OK)
        finish()
    }

    /**
     * Guarda credenciales sin validar (opción "Login anyway")
     */
    private fun forceLogin() {
        val username = pendingUsername ?: return
        val apiKey = pendingApiKey ?: return
        
        // Guardar credenciales de todas formas
        preferencesManager.setCredentials(username, apiKey)
        
        // Limpiar
        pendingUsername = null
        pendingApiKey = null
        
        Toast.makeText(
            this,
            getString(R.string.login_success_toast, username),
            Toast.LENGTH_SHORT
        ).show()
        
        setResult(RESULT_OK)
        finish()
    }

    /**
     * Muestra diálogo de error simple
     */
    private fun showErrorDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    /**
     * Muestra diálogo de error de login con opción "Login anyway"
     */
    private fun showLoginErrorDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.login_anyway) { _, _ ->
                forceLogin()
            }
            .show()
    }

    /**
     * Activa/desactiva el estado de carga
     */
    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.editUsername.isEnabled = !loading
        binding.editApiKey.isEnabled = !loading
        binding.btnLogin.isEnabled = !loading
        binding.btnLogin.alpha = if (loading) 0.5f else 1.0f
        binding.btnCancel.isEnabled = !loading
    }
}
