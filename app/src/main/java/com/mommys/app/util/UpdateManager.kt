package com.mommys.app.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
                return@withContext UpdateResult.Error("Error al conectar con GitHub: ${response.code}")
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
                return@withContext UpdateResult.Error("No se encontró APK en la release")
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
            
            // Comparar versiones
            if (isNewerVersion(tagName, currentVersion)) {
                Log.d(TAG, "Update available!")
                return@withContext UpdateResult.UpdateAvailable(releaseInfo, currentVersion)
            }
            
            Log.d(TAG, "No update available")
            return@withContext UpdateResult.NoUpdateAvailable
            
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
            val parts = versionName.split(".")
            when (parts.size) {
                3 -> parts[0].toInt() * 10000 + parts[1].toInt() * 100 + parts[2].toInt()
                2 -> parts[0].toInt() * 10000 + parts[1].toInt() * 100
                1 -> parts[0].toInt() * 10000
                else -> 1
            }
        } catch (e: Exception) {
            1
        }
    }
    
    /**
     * Compara si la versión nueva es mayor que la actual
     * Soporta formato semver: major.minor.patch
     */
    fun isNewerVersion(newVersion: String, currentVersion: String): Boolean {
        try {
            val newParts = newVersion.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = currentVersion.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
            
            // Comparar major
            val newMajor = newParts.getOrElse(0) { 0 }
            val curMajor = currentParts.getOrElse(0) { 0 }
            if (newMajor > curMajor) return true
            if (newMajor < curMajor) return false
            
            // Comparar minor
            val newMinor = newParts.getOrElse(1) { 0 }
            val curMinor = currentParts.getOrElse(1) { 0 }
            if (newMinor > curMinor) return true
            if (newMinor < curMinor) return false
            
            // Comparar patch
            val newPatch = newParts.getOrElse(2) { 0 }
            val curPatch = currentParts.getOrElse(2) { 0 }
            return newPatch > curPatch
            
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions", e)
            return false
        }
    }
    
    /**
     * Descarga e instala la actualización
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
            }
            
            val downloadId = downloadManager.enqueue(request)
            
            // Registrar receiver para cuando complete la descarga
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        try {
                            context.unregisterReceiver(this)
                        } catch (e: Exception) {
                            // Ignorar
                        }
                        
                        // Verificar que el archivo se descargó correctamente
                        val query = DownloadManager.Query().setFilterById(downloadId)
                        val cursor = downloadManager.query(query)
                        if (cursor.moveToFirst()) {
                            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val status = cursor.getInt(statusIndex)
                            
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                onComplete()
                                installApk(context, destinationFile)
                            } else {
                                onError("Descarga fallida")
                            }
                        }
                        cursor.close()
                    }
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }
            
            Log.d(TAG, "Download started: $downloadId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting download", e)
            onError("Error al iniciar descarga: ${e.message}")
        }
    }
    
    /**
     * Instala el APK descargado
     */
    private fun installApk(context: Context, apkFile: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            
            val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }
            
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            
            context.startActivity(intent)
            
            Log.d(TAG, "Install intent started for: ${apkFile.absolutePath}")
            
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
                    file.delete()
                    Log.d(TAG, "Deleted old APK: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning old APKs", e)
        }
    }
}
