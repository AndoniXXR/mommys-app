package com.mommys.app.util

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

/**
 * Manager para verificar e instalar actualizaciones desde GitHub Releases
 */
object UpdateManager {
    private const val TAG = "UpdateManager"
    
    // GitHub repository info
    private const val GITHUB_OWNER = "AndoniXXR"
    private const val GITHUB_REPO = "mommys-app"
    
    // URL de la API de GitHub para obtener releases
    private const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    
    // Preference keys
    private const val PREF_LAST_UPDATE_CHECK = "last_update_check"
    private const val PREF_UPDATE_FILE = "update_preferences"
    
    // Intervalo de verificación: 24 horas en milisegundos
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    
    // Handler para callbacks en el main thread
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Referencia al receiver activo para poder desregistrarlo
    private var activeReceiver: BroadcastReceiver? = null
    private var activeDownloadId: Long = -1
    
    /**
     * Datos de una release de GitHub
     */
    data class ReleaseInfo(
        val versionName: String,
        val versionCode: Int,
        val downloadUrl: String,
        val releaseNotes: String,
        val publishedAt: String,
        val assetName: String
    )
    
    /**
     * Resultado de la verificación de actualización
     */
    sealed class UpdateResult {
        data class UpdateAvailable(val release: ReleaseInfo, val currentVersion: String) : UpdateResult()
        object NoUpdateAvailable : UpdateResult()
        data class Error(val message: String) : UpdateResult()
    }
    
    /**
     * Obtiene la versión actual de la app
     */
    fun getCurrentVersion(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current version", e)
            "1.0.0"
        }
    }
    
    /**
     * Obtiene el código de versión actual
     */
    fun getCurrentVersionCode(context: Context): Long {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current version code", e)
            1L
        }
    }
    
    /**
     * Verifica si debe buscar actualizaciones automáticamente (cada 24h)
     */
    fun shouldCheckForUpdates(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_UPDATE_FILE, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(PREF_LAST_UPDATE_CHECK, 0)
        val now = System.currentTimeMillis()
        return (now - lastCheck) >= CHECK_INTERVAL_MS
    }
    
    /**
     * Guarda el timestamp de la última verificación
     */
    private fun saveLastCheckTime(context: Context) {
        val prefs = context.getSharedPreferences(PREF_UPDATE_FILE, Context.MODE_PRIVATE)
        prefs.edit().putLong(PREF_LAST_UPDATE_CHECK, System.currentTimeMillis()).apply()
    }
    
    /**
     * Verifica si hay una actualización disponible
     */
    suspend fun checkForUpdates(context: Context, forceCheck: Boolean = false): UpdateResult = withContext(Dispatchers.IO) {
        try {
            // Si no es forzado, verificar si ya se comprobó recientemente
            if (!forceCheck && !shouldCheckForUpdates(context)) {
                Log.d(TAG, "Skipping update check - checked recently")
                return@withContext UpdateResult.NoUpdateAvailable
            }
            
            Log.d(TAG, "Checking for updates from: $GITHUB_API_URL")
            
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "MommysApp-UpdateChecker")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "GitHub API error: ${response.code}")
                
                // Manejar diferentes códigos de error
                return@withContext when (response.code) {
                    404 -> {
                        // No hay releases publicadas todavía - esto NO es un error
                        Log.d(TAG, "No releases found (404) - treating as no update available")
                        saveLastCheckTime(context) // Guardar para no reintentar inmediatamente
                        UpdateResult.NoUpdateAvailable
                    }
                    403 -> {
                        // Rate limit o acceso denegado
                        UpdateResult.Error("Límite de peticiones alcanzado. Intenta más tarde.")
                    }
                    500, 502, 503 -> {
                        // Error del servidor de GitHub
                        UpdateResult.Error("GitHub no está disponible temporalmente.")
                    }
                    else -> {
                        UpdateResult.Error("Error al conectar con GitHub: ${response.code}")
                    }
                }
            }
            
            val body = response.body?.string() ?: return@withContext UpdateResult.Error("Respuesta vacía")
            val json = JSONObject(body)
            
            // Parsear la release
            val tagName = json.optString("tag_name", "").removePrefix("v")
            val releaseNotes = json.optString("body", "")
            val publishedAt = json.optString("published_at", "")
            
            // Buscar el asset APK
            val assets = json.optJSONArray("assets") ?: JSONArray()
            var downloadUrl = ""
            var assetName = ""
            
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk")) {
                    downloadUrl = asset.optString("browser_download_url", "")
                    assetName = name
                    break
                }
            }
            
            if (downloadUrl.isEmpty()) {
                Log.e(TAG, "No APK found in release assets")
                // Si no hay APK, no hay actualizacion disponible (no es error)
                saveLastCheckTime(context)
                return@withContext UpdateResult.NoUpdateAvailable
            }
            
            // Extraer version code del tag o usar un default
            val versionCode = extractVersionCode(tagName)
            
            val releaseInfo = ReleaseInfo(
                versionName = tagName,
                versionCode = versionCode,
                downloadUrl = downloadUrl,
                releaseNotes = releaseNotes,
                publishedAt = publishedAt,
                assetName = assetName
            )
            
            // Comparar versiones
            val currentVersion = getCurrentVersion(context)
            val currentVersionCode = getCurrentVersionCode(context)
            
            Log.d(TAG, "Current: $currentVersion ($currentVersionCode), Latest: $tagName ($versionCode)")
            
            // Guardar tiempo de verificación
            saveLastCheckTime(context)
            
            // Comparar versiones - solo mostrar si la nueva es ESTRICTAMENTE mayor
            val comparisonResult = compareVersions(tagName, currentVersion)
            Log.d(TAG, "Version comparison result: $comparisonResult (positive = update available)")
            
            if (comparisonResult > 0) {
                Log.d(TAG, "Update available!")
                return@withContext UpdateResult.UpdateAvailable(releaseInfo, currentVersion)
            }
            
            Log.d(TAG, "No update available (current version is same or newer)")
            return@withContext UpdateResult.NoUpdateAvailable
            
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "No internet connection", e)
            return@withContext UpdateResult.Error("Sin conexión a internet")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Connection timeout", e)
            return@withContext UpdateResult.Error("Tiempo de conexión agotado")
        } catch (e: java.io.IOException) {
            Log.e(TAG, "Network error", e)
            return@withContext UpdateResult.Error("Error de red: ${e.message}")
        } catch (e: org.json.JSONException) {
            Log.e(TAG, "JSON parsing error", e)
            return@withContext UpdateResult.Error("Error al procesar respuesta")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            return@withContext UpdateResult.Error("Error: ${e.message}")
        }
    }
    
    /**
     * Extrae el código de versión del nombre de versión
     * Ej: "1.2.3" -> 10203
     */
    private fun extractVersionCode(versionName: String): Int {
        return try {
            val cleanVersion = versionName.removePrefix("v")
            val parts = cleanVersion.split(".")
            when (parts.size) {
                3 -> parts[0].toInt() * 10000 + parts[1].toInt() * 100 + parts[2].toInt()
                2 -> parts[0].toInt() * 10000 + parts[1].toInt() * 100
                1 -> parts[0].toInt() * 10000
                else -> 1
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting version code from: $versionName", e)
            1
        }
    }
    
    /**
     * Compara dos versiones semver
     * @return positivo si newVersion > currentVersion, 
     *         negativo si newVersion < currentVersion,
     *         0 si son iguales
     */
    fun compareVersions(newVersion: String, currentVersion: String): Int {
        try {
            val newParts = newVersion.removePrefix("v").split(".").map { 
                it.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0 
            }
            val currentParts = currentVersion.removePrefix("v").split(".").map { 
                it.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0 
            }
            
            // Comparar major
            val newMajor = newParts.getOrElse(0) { 0 }
            val curMajor = currentParts.getOrElse(0) { 0 }
            if (newMajor != curMajor) return newMajor - curMajor
            
            // Comparar minor
            val newMinor = newParts.getOrElse(1) { 0 }
            val curMinor = currentParts.getOrElse(1) { 0 }
            if (newMinor != curMinor) return newMinor - curMinor
            
            // Comparar patch
            val newPatch = newParts.getOrElse(2) { 0 }
            val curPatch = currentParts.getOrElse(2) { 0 }
            return newPatch - curPatch
            
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions: $newVersion vs $currentVersion", e)
            return 0
        }
    }
    
    /**
     * Compara si la versión nueva es mayor que la actual (legacy method)
     */
    fun isNewerVersion(newVersion: String, currentVersion: String): Boolean {
        return compareVersions(newVersion, currentVersion) > 0
    }
    
    /**
     * Descarga e instala la actualización
     * @param activity La activity que inicia la descarga (necesaria para el install intent)
     */
    fun downloadAndInstallUpdate(
        context: Context,
        releaseInfo: ReleaseInfo,
        onProgress: (Int) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            
            // Cancelar descarga anterior si existe
            cancelActiveDownload(context)
            
            // Limpiar APKs anteriores
            cleanOldApks(context)
            
            val fileName = "mommys-update-${releaseInfo.versionName}.apk"
            val destinationDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val destinationFile = File(destinationDir, fileName)
            
            // Si ya existe, eliminarlo
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            
            val request = DownloadManager.Request(Uri.parse(releaseInfo.downloadUrl)).apply {
                setTitle("Actualizando Mommys")
                setDescription("Descargando versión ${releaseInfo.versionName}")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(destinationFile))
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                )
                // Permitir que se descargue con datos moviles
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)
            }
            
            activeDownloadId = downloadManager.enqueue(request)
            Log.d(TAG, "Download started with ID: $activeDownloadId")
            
            // Guardar referencia weak al context para evitar memory leaks
            val contextRef = WeakReference(context)
            
            // Registrar receiver para cuando complete la descarga
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent == null) return
                    
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    Log.d(TAG, "Download broadcast received for ID: $id (expected: $activeDownloadId)")
                    
                    if (id == activeDownloadId) {
                        // Desregistrar este receiver de forma segura
                        unregisterReceiverSafely(contextRef.get())
                        
                        // Verificar que el archivo se descargó correctamente
                        val query = DownloadManager.Query().setFilterById(activeDownloadId)
                        val cursor = downloadManager.query(query)
                        
                        try {
                            if (cursor != null && cursor.moveToFirst()) {
                                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                val status = if (statusIndex >= 0) cursor.getInt(statusIndex) else -1
                                
                                Log.d(TAG, "Download status: $status")
                                
                                when (status) {
                                    DownloadManager.STATUS_SUCCESSFUL -> {
                                        Log.d(TAG, "Download successful, file: ${destinationFile.absolutePath}")
                                        
                                        // Verificar que el archivo existe y tiene contenido
                                        if (destinationFile.exists() && destinationFile.length() > 0) {
                                            // Llamar onComplete en el main thread
                                            mainHandler.post {
                                                onComplete()
                                            }
                                            
                                            // Instalar el APK con un pequeño delay para que el dialog se cierre
                                            mainHandler.postDelayed({
                                                val currentContext = contextRef.get()
                                                if (currentContext != null) {
                                                    installApk(currentContext, destinationFile)
                                                }
                                            }, 500)
                                        } else {
                                            Log.e(TAG, "Downloaded file is empty or doesn't exist")
                                            mainHandler.post {
                                                onError("El archivo descargado está vacío")
                                            }
                                        }
                                    }
                                    DownloadManager.STATUS_FAILED -> {
                                        val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                        val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else -1
                                        Log.e(TAG, "Download failed with reason: $reason")
                                        mainHandler.post {
                                            onError("Descarga fallida (código: $reason)")
                                        }
                                    }
                                    else -> {
                                        Log.e(TAG, "Unexpected download status: $status")
                                        mainHandler.post {
                                            onError("Estado de descarga inesperado")
                                        }
                                    }
                                }
                            } else {
                                Log.e(TAG, "Could not query download status")
                                mainHandler.post {
                                    onError("No se pudo verificar la descarga")
                                }
                            }
                        } finally {
                            cursor?.close()
                        }
                        
                        // Reset active download
                        activeDownloadId = -1
                    }
                }
            }
            
            // Guardar referencia al receiver
            activeReceiver = receiver
            
            // Registrar el receiver
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            
            Log.d(TAG, "BroadcastReceiver registered for download completion")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting download", e)
            onError("Error al iniciar descarga: ${e.message}")
        }
    }
    
    /**
     * Cancela la descarga activa si existe
     */
    private fun cancelActiveDownload(context: Context) {
        try {
            if (activeDownloadId != -1L) {
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadManager.remove(activeDownloadId)
                Log.d(TAG, "Cancelled previous download: $activeDownloadId")
            }
            unregisterReceiverSafely(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling download", e)
        }
    }
    
    /**
     * Desregistra el receiver de forma segura
     */
    private fun unregisterReceiverSafely(context: Context?) {
        try {
            activeReceiver?.let { receiver ->
                context?.unregisterReceiver(receiver)
                Log.d(TAG, "BroadcastReceiver unregistered")
            }
        } catch (e: IllegalArgumentException) {
            // Receiver ya no estaba registrado
            Log.d(TAG, "Receiver was already unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        } finally {
            activeReceiver = null
        }
    }
    
    /**
     * Instala el APK descargado
     */
    private fun installApk(context: Context, apkFile: File) {
        try {
            Log.d(TAG, "Starting APK installation: ${apkFile.absolutePath}")
            
            if (!apkFile.exists()) {
                Log.e(TAG, "APK file does not exist: ${apkFile.absolutePath}")
                return
            }
            
            val intent = Intent(Intent.ACTION_VIEW)
            
            val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }
            
            Log.d(TAG, "APK URI: $apkUri")
            
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            // En Android 7+ necesitamos asegurarnos de que el intent puede ser resuelto
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Log.d(TAG, "Install intent started successfully")
            } else {
                Log.e(TAG, "No activity found to handle install intent")
                // Intentar con intent alternativo
                val altIntent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                altIntent.data = apkUri
                altIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                altIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                altIntent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                context.startActivity(altIntent)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
        }
    }
    
    /**
     * Limpia APKs de actualizaciones anteriores
     */
    private fun cleanOldApks(context: Context) {
        try {
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            downloadDir?.listFiles()?.forEach { file ->
                if (file.name.startsWith("mommys-update-") && file.name.endsWith(".apk")) {
                    val deleted = file.delete()
                    Log.d(TAG, "Deleted old APK: ${file.name} - success: $deleted")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning old APKs", e)
        }
    }
    
    /**
     * Limpia recursos cuando ya no se necesitan (llamar desde onDestroy de la Activity)
     */
    fun cleanup(context: Context) {
        unregisterReceiverSafely(context)
        activeDownloadId = -1
    }
}
