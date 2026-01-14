package com.mommys.app.ui.notes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import com.bumptech.glide.Glide
import com.mommys.app.R
import com.mommys.app.data.api.ApiClient
import com.mommys.app.data.model.Note
import com.mommys.app.data.model.Post
import com.mommys.app.data.preferences.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * NotesActivity - Muestra las notas de un post sobre la imagen
 * 
 * Implementado exactamente como la app original:
 * - NotesActivity.java en se/zepiwolf/tws/
 * - Muestra la imagen del post
 * - Carga notas desde API: /notes.json?search[post_id]=
 * - Posiciona las notas como overlays sobre la imagen
 * - Click en cualquier lugar cierra la activity
 */
class NotesActivity : AppCompatActivity() {

    companion object {
        // El post se pasa estáticamente como la app original
        var currentPost: Post? = null
    }

    private lateinit var imageView: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var notesContainer: FrameLayout
    private lateinit var contentFrame: FrameLayout
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes)

        val post = currentPost
        if (post == null) {
            finish()
            return
        }

        preferencesManager = PreferencesManager(this)

        // Inicializar vistas
        imageView = findViewById(R.id.image)
        progressBar = findViewById(R.id.progressBar)
        notesContainer = findViewById(R.id.fLNotes)
        contentFrame = findViewById(R.id.fLContent)

        // Click para cerrar (como la app original)
        contentFrame.setOnClickListener {
            finish()
        }

        // Mostrar progress bar
        progressBar.visibility = View.VISIBLE

        // Cargar imagen del post
        val imageUrl = post.file.url
        Glide.with(this)
            .load(imageUrl)
            .into(imageView)

        // Cargar notas
        loadNotes(post.id, post.file.width)
    }

    /**
     * Carga las notas desde la API
     * Como la app original vi/y.java: /notes.json?search[post_id]=
     */
    private fun loadNotes(postId: Int, imageWidth: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val baseUrl = if (preferencesManager.useE621()) 
                    "https://e621.net/" else "https://e926.net/"
                val apiService = ApiClient.getApiService(baseUrl)
                val response = apiService.getNotes(postId)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE

                    if (response.isSuccessful) {
                        val notes = response.body() ?: emptyList()
                        if (notes.isEmpty()) {
                            Toast.makeText(
                                this@NotesActivity,
                                "No notes found for this post",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            displayNotes(notes, imageWidth)
                        }
                    } else {
                        Toast.makeText(
                            this@NotesActivity,
                            "Error loading notes: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@NotesActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Muestra las notas sobre la imagen
     * Como la app original NotesActivity.F()
     * 
     * Calcula la escala basándose en el ancho de pantalla vs ancho original
     * y posiciona cada nota con FrameLayout.LayoutParams
     */
    private fun displayNotes(notes: List<Note>, originalImageWidth: Int) {
        val screenWidth = resources.displayMetrics.widthPixels
        val scale = screenWidth.toDouble() / (originalImageWidth + 0.0)

        val inflater = LayoutInflater.from(this)

        for (note in notes) {
            // Solo mostrar notas activas (como la app original)
            if (!note.isActive) continue

            val noteView = inflater.inflate(R.layout.item_note, notesContainer, false)
            val txtBody = noteView.findViewById<TextView>(R.id.txtBody)

            // Parsear HTML en el body de la nota (como d0.n(63, fVar.f17981f))
            val bodyHtml = note.body
            txtBody.text = HtmlCompat.fromHtml(bodyHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)

            // Calcular posición y tamaño escalados
            val scaledX = (note.x * scale).toInt()
            val scaledY = (note.y * scale).toInt()
            var scaledWidth = (note.width * scale * 1.3).toInt()  // Factor 1.3 como app original
            var scaledHeight = (note.height * scale * 1.1).toInt()  // Factor 1.1 como app original

            // Ajustes de margen para notas pequeñas (como app original)
            var marginOffsetX = 0
            var marginOffsetY = 0

            if (scaledWidth < 120) {
                marginOffsetX = (120 - scaledWidth) / 2
                scaledWidth = 120
            }
            if (scaledHeight < 60) {
                marginOffsetY = (60 - scaledHeight) / 2
                scaledHeight = 60
            }

            val params = FrameLayout.LayoutParams(scaledWidth, scaledHeight)
            params.leftMargin = scaledX - marginOffsetX
            params.topMargin = scaledY - marginOffsetY
            noteView.layoutParams = params

            notesContainer.addView(noteView)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar referencia estática
        currentPost = null
    }
}
