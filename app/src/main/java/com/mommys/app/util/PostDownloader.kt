package com.mommys.app.util

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.mommys.app.R
import com.mommys.app.data.model.Post
import com.mommys.app.data.model.isVideo
import com.mommys.app.data.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Clase para manejar la descarga de posts
 * Implementación completa como la app original:
 * - Descarga imágenes y videos
 * - Guarda en la galería usando MediaStore (Android 10+) o directamente (Android 9-)
 * - Soporta nombres de archivo personalizados con máscaras
 * - Notifica progreso y completado
 */
object PostDownloader {
    
    private const val TAG = "PostDownloader"
    
    // Nombre de carpeta por defecto
    private const val DEFAULT_FOLDER = "Mommys"
    
    // Extensiones por tipo de archivo
    private val FILE_EXTENSIONS = mapOf(
        "png" to ".png",
        "jpg" to ".jpg",
        "jpeg" to ".jpg",
        "gif" to ".gif",
        "webm" to ".webm",
        "mp4" to ".mp4",
        "webp" to ".webp",
        "swf" to ".swf"
    )
    
    // MIME types
    private val MIME_TYPES = mapOf(
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webm" to "video/webm",
        "mp4" to "video/mp4",
        "webp" to "image/webp",
        "swf" to "application/x-shockwave-flash"
    )
    
    /**
     * Datos del archivo descargado
     */
    data class DownloadedFile(
        val fileName: String,
        val uri: String,
        val mimeType: String
    )
    
    /**
     * Interfaz para callbacks de descarga
     */
    interface DownloadCallback {
        fun onStart()
        fun onProgress(progress: Int)
        fun onSuccess(downloadedFile: DownloadedFile)
        fun onError(error: String)
    }
    
    /**
     * Descargar un post
     * @param context Contexto de la aplicación
     * @param post El post a descargar
     * @param prefsManager Manager de preferencias para obtener configuración
     * @param callback Callback para notificar progreso
     */
    suspend fun downloadPost(
        context: Context,
        post: Post,
        prefsManager: PreferencesManager,
        callback: DownloadCallback? = null
    ): DownloadedFile? = withContext(Dispatchers.IO) {
        try {
            callback?.let { 
                withContext(Dispatchers.Main) { it.onStart() }
            }
            
            val fileUrl = post.file.url
            if (fileUrl == null) {
                val error = context.getString(R.string.post_error_deleted)
                callback?.let { 
                    withContext(Dispatchers.Main) { it.onError(error) }
                }
                return@withContext null
            }
            
            // Determinar extensión y tipo
            val extension = getFileExtension(post)
            val mimeType = getMimeType(post)
            val isVideo = post.isVideo()
            
            // Generar nombre de archivo usando la máscara del usuario o default
            val fileName = generateFileName(post, prefsManager) + extension
            val overwrite = prefsManager.storageOverwrite
            
            Log.d(TAG, "Downloading: $fileUrl -> $fileName (overwrite=$overwrite)")
            
            // Usar método apropiado según versión de Android
            val resultUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ usar MediaStore
                downloadWithMediaStore(context, fileUrl, fileName, mimeType, isVideo, overwrite, callback)
            } else {
                // Android 9 y menor usar archivo directo
                downloadToExternalStorage(context, fileUrl, fileName, isVideo, overwrite, callback)
            }
            
            if (resultUri != null) {
                val downloadedFile = DownloadedFile(fileName, resultUri, mimeType)
                callback?.let { 
                    withContext(Dispatchers.Main) { 
                        it.onSuccess(downloadedFile)
                    }
                }
                return@withContext downloadedFile
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            callback?.let { 
                withContext(Dispatchers.Main) { 
                    it.onError(e.message ?: "Unknown error")
                }
            }
            return@withContext null
        }
    }
    
    /**
     * Descargar usando MediaStore (Android 10+)
     * Guarda en Pictures/Mommys o Movies/Mommys
     */
    private suspend fun downloadWithMediaStore(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String,
        isVideo: Boolean,
        overwrite: Boolean,
        callback: DownloadCallback?
    ): String? = withContext(Dispatchers.IO) {
        var outputStream: OutputStream? = null
        var inputStream: InputStream? = null
        
        try {
            // Determinar la colección (Pictures o Movies)
            val collection = if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            
            // Determinar la carpeta relativa
            val relativePath = if (isVideo) {
                Environment.DIRECTORY_MOVIES + File.separator + DEFAULT_FOLDER
            } else {
                Environment.DIRECTORY_PICTURES + File.separator + DEFAULT_FOLDER
            }
            
            val resolver = context.contentResolver
            
            // Verificar si el archivo ya existe (para storage_overwrite)
            val existingUri = findExistingFile(resolver, collection, fileName, relativePath)
            if (existingUri != null && !overwrite) {
                Log.d(TAG, "File already exists and overwrite=false, skipping: $fileName")
                return@withContext existingUri.toString()
            }
            
            // Si existe y overwrite=true, eliminar el existente
            if (existingUri != null && overwrite) {
                Log.d(TAG, "Overwriting existing file: $fileName")
                resolver.delete(existingUri, null, null)
            }
            
            // Crear ContentValues
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            
            // Insertar en MediaStore
            val uri = resolver.insert(collection, contentValues)
                ?: throw Exception("Failed to create MediaStore entry")
            
            try {
                // Descargar el archivo
                val client = OkHttpClient.Builder().build()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    throw Exception("Download failed: ${response.code}")
                }
                
                val body = response.body ?: throw Exception("Empty response")
                val contentLength = body.contentLength()
                
                outputStream = resolver.openOutputStream(uri)
                    ?: throw Exception("Failed to open output stream")
                inputStream = body.byteStream()
                
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead: Long = 0
                var lastProgress = 0
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    
                    if (contentLength > 0) {
                        val progress = ((totalBytesRead * 100) / contentLength).toInt()
                        if (progress != lastProgress) {
                            lastProgress = progress
                            callback?.let {
                                withContext(Dispatchers.Main) { it.onProgress(progress) }
                            }
                        }
                    }
                }
                
                outputStream.flush()
                
                // Marcar como completado
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                
                Log.d(TAG, "Downloaded to MediaStore: $uri")
                return@withContext uri.toString()
                
            } catch (e: Exception) {
                // Eliminar entrada fallida
                resolver.delete(uri, null, null)
                throw e
            }
            
        } finally {
            try {
                outputStream?.close()
                inputStream?.close()
            } catch (e: Exception) {
                // Ignorar
            }
        }
    }
    
    /**
     * Descargar a almacenamiento externo (Android 9 y menor)
     */
    @Suppress("DEPRECATION")
    private suspend fun downloadToExternalStorage(
        context: Context,
        url: String,
        fileName: String,
        isVideo: Boolean,
        overwrite: Boolean,
        callback: DownloadCallback?
    ): String? = withContext(Dispatchers.IO) {
        var outputStream: FileOutputStream? = null
        var inputStream: InputStream? = null
        
        try {
            // Obtener directorio base
            val baseDir = if (isVideo) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            }
            
            // Crear subdirectorio
            val saveDir = File(baseDir, DEFAULT_FOLDER)
            if (!saveDir.exists()) {
                saveDir.mkdirs()
            }
            
            val file = File(saveDir, fileName)
            
            // Verificar si existe y respetar storage_overwrite
            if (file.exists() && !overwrite) {
                Log.d(TAG, "File already exists and overwrite=false, skipping: $fileName")
                return@withContext android.net.Uri.fromFile(file).toString()
            }
            
            // Descargar el archivo
            val client = OkHttpClient.Builder().build()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw Exception("Download failed: ${response.code}")
            }
            
            val body = response.body ?: throw Exception("Empty response")
            val contentLength = body.contentLength()
            
            outputStream = FileOutputStream(file)
            inputStream = body.byteStream()
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead: Long = 0
            var lastProgress = 0
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                if (contentLength > 0) {
                    val progress = ((totalBytesRead * 100) / contentLength).toInt()
                    if (progress != lastProgress) {
                        lastProgress = progress
                        callback?.let {
                            withContext(Dispatchers.Main) { it.onProgress(progress) }
                        }
                    }
                }
            }
            
            outputStream.flush()
            
            // Notificar al MediaScanner y obtener URI
            var resultUri: String? = null
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf(if (isVideo) "video/*" else "image/*")
            ) { _, uri ->
                resultUri = uri?.toString()
            }
            
            Log.d(TAG, "Downloaded to: ${file.absolutePath}")
            // Para Android 9-, usamos file:// URI
            return@withContext android.net.Uri.fromFile(file).toString()
            
        } finally {
            try {
                outputStream?.close()
                inputStream?.close()
            } catch (e: Exception) {
                // Ignorar
            }
        }
    }
    
    /**
     * Usar DownloadManager del sistema (método alternativo)
     * Útil para descargas en segundo plano
     */
    fun downloadWithSystemManager(
        context: Context,
        post: Post,
        prefsManager: PreferencesManager
    ): Long {
        val url = post.file.url ?: return -1
        val extension = getFileExtension(post)
        val fileName = generateFileName(post, prefsManager) + extension
        val isVideo = post.isVideo()
        
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDescription("Downloading post #${post.id}")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            
            if (isVideo) {
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_MOVIES,
                    "$DEFAULT_FOLDER/$fileName"
                )
            } else {
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_PICTURES,
                    "$DEFAULT_FOLDER/$fileName"
                )
            }
            
            // Permitir en redes móviles y WiFi
            setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI or 
                DownloadManager.Request.NETWORK_MOBILE
            )
        }
        
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return downloadManager.enqueue(request)
    }
    
    /**
     * Generar nombre de archivo usando máscara
     * Como la app original con máscaras: %id%, %md5%, %artist%, %rating%, etc.
     */
    fun generateFileName(post: Post, prefsManager: PreferencesManager): String {
        val mask = prefsManager.storageFileNameMask
        val hideFromGallery = prefsManager.storageHide
        
        var fileName = if (mask.isBlank()) {
            // Default: id
            post.id.toString()
        } else {
            // Obtener primer artista (si existe)
            val artist = post.tags.artist.firstOrNull() ?: "unknown"
            val character = post.tags.character.firstOrNull() ?: ""
            val copyright = post.tags.copyright.firstOrNull() ?: ""
            
            // Reemplazar máscaras
            mask
                .replace("%id%", post.id.toString())
                .replace("%md5%", post.file.md5 ?: post.id.toString())
                .replace("%artist%", sanitizeFileName(artist))
                .replace("%rating%", post.rating)
                .replace("%score%", post.score.total.toString())
                .replace("%width%", post.file.width.toString())
                .replace("%height%", post.file.height.toString())
                .replace("%character%", sanitizeFileName(character))
                .replace("%copyright%", sanitizeFileName(copyright))
                .replace("%ext%", post.file.ext ?: "jpg")
                // Limpiar caracteres no permitidos
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .take(120) // Limitar longitud
        }
        
        // Si storage_hide está activo, añadir punto al principio para ocultar de galería
        if (hideFromGallery) {
            fileName = ".$fileName"
        }
        
        return fileName
    }
    
    /**
     * Sanitizar nombre para evitar caracteres inválidos
     */
    private fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(50)
    }
    
    /**
     * Obtener extensión del archivo
     */
    fun getFileExtension(post: Post): String {
        val ext = post.file.ext?.lowercase() ?: ""
        return FILE_EXTENSIONS[ext] ?: ".jpg"
    }
    
    /**
     * Obtener MIME type del archivo
     */
    private fun getMimeType(post: Post): String {
        val ext = post.file.ext?.lowercase() ?: ""
        return MIME_TYPES[ext] ?: "image/jpeg"
    }
    
    /**
     * Buscar archivo existente en MediaStore (para verificar duplicados)
     */
    private fun findExistingFile(
        resolver: android.content.ContentResolver,
        collection: Uri,
        fileName: String,
        relativePath: String
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }
        
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(fileName, "$relativePath/")
        
        resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                return android.content.ContentUris.withAppendedId(collection, id)
            }
        }
        return null
    }
}
