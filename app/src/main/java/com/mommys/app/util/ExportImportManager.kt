package com.mommys.app.util

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manager para exportar e importar datos de la app
 * Basado en ui/c.java de la app original
 * 
 * El formato de archivo es:
 * - .tws = archivo encriptado con AES
 * - .tws_plain = archivo sin encriptar (JSON)
 * 
 * Estructura JSON:
 * {
 *   "settings": ["tipo:key:valor", ...],
 *   "userInfo": ["tipo:key:valor", ...]
 * }
 * 
 * Tipos: b=boolean, i=int, f=float, l=long, s=string
 */
object ExportImportManager {
    
    private const val TAG = "ExportImportManager"
    
    // Keys a excluir del export (datos sensibles que no deben compartirse)
    private val EXCLUDED_KEYS = arrayOf("cookies", "coins_purchases", "coins_consume_pending", "coins_consume_complete", "api_key")
    
    // Configuración de encriptación (igual que la app original)
    private const val PASSWORD = "gf./dfmGFdf3_dfÖDSBY34/REW#("
    private const val SALT = "g.#hå"
    private const val IV = "D9FGH35MF3AG0IFD"
    private const val ITERATIONS = 65536
    private const val KEY_LENGTH = 256
    
    // Preference file names
    private const val PREF_USER_PREFERENCES = "user_preferences"
    private const val PREF_USER_INFO = "user_info"
    
    /**
     * Resultado de una operación de export/import
     */
    sealed class Result {
        data class Success(val message: String) : Result()
        data class Error(val message: String) : Result()
    }
    
    /**
     * Genera el nombre del archivo de backup
     * Formato: TWS_BU_yyMMdd_HHmmss.tws (o .tws_plain si no encriptado)
     */
    fun generateBackupFileName(encrypted: Boolean): String {
        val dateFormat = SimpleDateFormat("yyMMdd_HHmmss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val extension = if (encrypted) ".tws" else ".tws_plain"
        return "TWS_BU_$timestamp$extension"
    }
    
    /**
     * Genera el JSON con todos los datos exportables
     * Como ui/c.java g0()
     */
    fun generateExportData(context: Context): JSONObject? {
        return try {
            val userPrefs = context.getSharedPreferences(PREF_USER_PREFERENCES, Context.MODE_PRIVATE)
            val userInfo = context.getSharedPreferences(PREF_USER_INFO, Context.MODE_PRIVATE)
            
            val settingsArray = JSONArray()
            val userInfoArray = JSONArray()
            
            // Export settings
            for ((key, value) in userPrefs.all) {
                if (!isExcludedKey(key)) {
                    val type = getValueType(value)
                    settingsArray.put("$type:$key:$value")
                }
            }
            
            // Export user info (excepto api_key y datos sensibles)
            for ((key, value) in userInfo.all) {
                if (!isExcludedKey(key)) {
                    val type = getValueType(value)
                    userInfoArray.put("$type:$key:$value")
                }
            }
            
            JSONObject().apply {
                put("settings", settingsArray)
                put("userInfo", userInfoArray)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating export data", e)
            null
        }
    }
    
    /**
     * Encripta los datos usando AES/CBC/PKCS5Padding
     * Como ui/c.java k0()
     */
    fun encryptData(data: ByteArray): ByteArray {
        val keySpec = PBEKeySpec(
            PASSWORD.toCharArray(),
            SALT.toByteArray(),
            ITERATIONS,
            KEY_LENGTH
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = factory.generateSecret(keySpec)
        val secret = SecretKeySpec(secretKey.encoded, "AES")
        
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secret, IvParameterSpec(IV.toByteArray()))
        
        return cipher.doFinal(data)
    }
    
    /**
     * Desencripta los datos
     * Como ui/c.java i0()
     */
    fun decryptData(encryptedData: ByteArray): ByteArray {
        val keySpec = PBEKeySpec(
            PASSWORD.toCharArray(),
            SALT.toByteArray(),
            ITERATIONS,
            KEY_LENGTH
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = factory.generateSecret(keySpec)
        val secret = SecretKeySpec(secretKey.encoded, "AES")
        
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secret, IvParameterSpec(IV.toByteArray()))
        
        return cipher.doFinal(encryptedData)
    }
    
    /**
     * Escribe los datos de export a un archivo
     */
    fun writeExportToUri(
        context: Context,
        uri: Uri,
        data: JSONObject,
        encrypt: Boolean
    ): Result {
        return try {
            var bytes = data.toString().toByteArray(StandardCharsets.UTF_8)
            
            if (encrypt) {
                bytes = encryptData(bytes)
            }
            
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(bytes)
            } ?: return Result.Error("Failed to open output stream")
            
            Result.Success("Data exported successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing export", e)
            Result.Error("Export failed: ${e.message}")
        }
    }
    
    /**
     * Lee e importa datos desde un archivo
     */
    fun importFromUri(
        context: Context,
        uri: Uri,
        encrypted: Boolean
    ): Result {
        return try {
            // Leer archivo
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val temp = ByteArray(16384)
                var read: Int
                while (input.read(temp, 0, temp.size).also { read = it } != -1) {
                    buffer.write(temp, 0, read)
                }
                buffer.toByteArray()
            } ?: return Result.Error("Failed to read file")
            
            // Desencriptar si es necesario
            val jsonString = if (encrypted) {
                String(decryptData(bytes), StandardCharsets.UTF_8)
            } else {
                String(bytes, StandardCharsets.UTF_8)
            }
            
            // Parsear e importar
            importFromJson(context, jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing", e)
            Result.Error("Import failed: ${e.message}")
        }
    }
    
    /**
     * Importa datos desde un string JSON
     * Como ui/c.java h0()
     */
    private fun importFromJson(context: Context, jsonString: String): Result {
        if (jsonString.isEmpty()) {
            return Result.Error("No data to import")
        }
        
        return try {
            val json = JSONObject(jsonString)
            val settings = json.getJSONArray("settings")
            val userInfo = json.getJSONArray("userInfo")
            
            val userPrefs = context.getSharedPreferences(PREF_USER_PREFERENCES, Context.MODE_PRIVATE)
            val userInfoPrefs = context.getSharedPreferences(PREF_USER_INFO, Context.MODE_PRIVATE)
            
            // Importar settings
            val settingsEditor = userPrefs.edit()
            for (i in 0 until settings.length()) {
                parseAndApplyPreference(settings.getString(i), settingsEditor)
            }
            settingsEditor.apply()
            
            // Importar user info
            val userInfoEditor = userInfoPrefs.edit()
            for (i in 0 until userInfo.length()) {
                parseAndApplyPreference(userInfo.getString(i), userInfoEditor)
            }
            userInfoEditor.apply()
            
            Result.Success("Data imported successfully. Please restart the app.")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing import data", e)
            Result.Error("Invalid file format")
        }
    }
    
    /**
     * Parsea una línea de preferencia y la aplica
     * Formato: tipo:key:valor
     * Como ui/c.java d0()
     */
    private fun parseAndApplyPreference(line: String, editor: SharedPreferences.Editor) {
        val parts = line.split(":", limit = 3)
        if (parts.size != 3) return
        
        val (type, key, value) = parts
        
        // No importar keys excluidas
        if (isExcludedKey(key)) return
        
        try {
            when (type) {
                "b" -> editor.putBoolean(key, value.toBoolean())
                "i" -> editor.putInt(key, value.toInt())
                "f" -> editor.putFloat(key, value.toFloat())
                "l" -> editor.putLong(key, value.toLong())
                "s" -> editor.putString(key, value)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse preference: $line", e)
        }
    }
    
    /**
     * Determina el tipo de un valor para serialización
     * Como ui/c.java e0()
     */
    private fun getValueType(value: Any?): String {
        return when (value) {
            is String -> "s"
            is Int -> "i"
            is Boolean -> "b"
            is Float -> "f"
            is Long -> "l"
            else -> "?"
        }
    }
    
    /**
     * Verifica si una key debe excluirse del export
     */
    private fun isExcludedKey(key: String): Boolean {
        return EXCLUDED_KEYS.any { key.equals(it, ignoreCase = true) }
    }
    
    /**
     * Obtiene el nombre de archivo desde una URI
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> {
                context.contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow("_display_name"))
                    } else null
                }
            }
            else -> uri.lastPathSegment
        }
    }
    
    /**
     * Determina si un archivo está encriptado basado en su extensión
     */
    fun isEncryptedFile(fileName: String): Boolean? {
        return when {
            fileName.endsWith(".tws") -> true
            fileName.endsWith(".tws_plain") -> false
            else -> null // No es un archivo válido
        }
    }
}
