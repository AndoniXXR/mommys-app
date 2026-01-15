package com.mommys.app.ui.profile

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Html
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.mommys.app.MommysApplication
import com.mommys.app.R
import com.mommys.app.data.api.ApiClient
import com.mommys.app.data.model.User
import com.mommys.app.databinding.ActivityProfileBinding
import com.mommys.app.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ProfileActivity - Pantalla de perfil de usuario
 * 
 * Similar a se.zepiwolf.tws.ProfileActivity:
 * - Muestra información del usuario con formato HTML
 * - Carga el avatar del usuario si tiene
 * - Fondo con color aleatorio oscuro
 * - Menú con opciones para ver favourites y uploads
 */
class ProfileActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_USERNAME = "user"
    }
    
    private lateinit var binding: ActivityProfileBinding
    private val prefs by lazy { MommysApplication.getInstance().preferencesManager }
    private val api by lazy { ApiClient.getApiService() }
    
    private var username: String = ""
    private var currentUser: User? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Obtener username: primero de extras, luego de deep link, luego de prefs
        val deepLinkUsername = handleDeepLink()
        val extrasUsername = intent.getStringExtra(EXTRA_USERNAME)
        
        username = when {
            extrasUsername != null && deepLinkUsername == null -> extrasUsername
            deepLinkUsername != null -> deepLinkUsername
            else -> prefs.getUsername() ?: ""
        }
        
        // Si es "home", abrir navegador (como la app original)
        if (username.equals("home", ignoreCase = true)) {
            val baseUrl = if (prefs.useE621()) "https://e621.net" else "https://e926.net"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("$baseUrl/users/home")))
            } catch (e: Exception) {
                Toast.makeText(this, R.string.post_open_in_browser_failed, Toast.LENGTH_SHORT).show()
            }
            finish()
            return
        }
        
        // Validar que tenemos un username
        if (username.isEmpty()) {
            Toast.makeText(this, R.string.profile_error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setupToolbar()
        setupViews()
        loadUserProfile()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.profile_title, username)
        }
    }
    
    private fun setupViews() {
        // Mostrar username inicial
        binding.txtUsername.text = formatUsername(username)
        
        // Fondo con color aleatorio oscuro (como la app original)
        val gradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.rgb(
                (Math.random() * 25).toInt() + 10,
                (Math.random() * 25).toInt() + 10,
                (Math.random() * 25).toInt() + 10
            ))
        }
        binding.imgBg.setImageDrawable(gradientDrawable)
        
        // Botón de retry
        binding.btnRefresh.setOnClickListener {
            loadUserProfile()
        }
    }
    
    /**
     * Formatea el username para mostrar con estilo
     * Similar a ii.d.a() en la app original
     */
    private fun formatUsername(name: String): String {
        return name.replace("_", " ")
    }
    
    /**
     * Carga el perfil del usuario desde la API
     */
    private fun loadUserProfile() {
        binding.progressBar.visibility = View.VISIBLE
        binding.lLError.visibility = View.GONE
        binding.txtContent.text = ""
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Intentar primero por username
                val response = api.getUserByUsername(username)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val user = response.body()!!
                        currentUser = user
                        displayUserInfo(user)
                    } else {
                        showError()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showError()
                }
            }
        }
    }
    
    /**
     * Muestra la información del usuario con formato HTML
     * Similar al método b() en la app original
     */
    private fun displayUserInfo(user: User) {
        binding.progressBar.visibility = View.GONE
        binding.lLError.visibility = View.GONE
        
        // Actualizar título
        supportActionBar?.title = getString(R.string.profile_title, user.name)
        binding.txtUsername.text = formatUsername(user.name)
        
        // Cargar avatar si tiene
        user.avatarId?.let { avatarId ->
            if (avatarId > 0) {
                loadAvatar(avatarId)
            }
        }
        
        // Construir info HTML como la app original
        val sb = StringBuilder()
        
        sb.append("<b>Account level</b>: ${user.levelString}<br>")
        sb.append("<b>User id</b>: ${user.id}<br>")
        sb.append("<b>Is banned?</b> ${user.isBanned}<br>")
        sb.append("<b>Upload limit</b>: ${user.baseUploadLimit}<br>")
        
        user.avatarId?.let {
            sb.append("<b>Avatar id</b>: $it<br>")
        }
        
        user.hasMail?.let {
            sb.append("<b>Has mail?</b> $it<br>")
        }
        
        sb.append("<br>")
        
        // Fechas
        user.createdAt?.let {
            sb.append("<b>Account created at</b> ${formatDate(it)}<br>")
        }
        user.updatedAt?.let {
            sb.append("<b>Account updated at</b> ${formatDate(it)}<br>")
        }
        user.lastLoggedInAt?.let {
            sb.append("<b>Last login at</b> ${formatDate(it)}<br>")
        }
        
        sb.append("<br>")
        
        // Estadísticas
        user.favoriteCount?.let {
            sb.append("<b>Favourites</b>: $it<br>")
        }
        sb.append("<b>Uploads</b>: ${user.postUploadCount}<br>")
        user.commentCount?.let {
            sb.append("<b>Comments</b>: $it<br>")
        }
        sb.append("<b>Posts updated</b>: ${user.postUpdateCount}<br>")
        user.forumPostCount?.let {
            sb.append("<b>Forum posts</b>: $it<br>")
        }
        user.poolVersionCount?.let {
            sb.append("<b>Pools updated</b>: $it<br>")
        }
        user.wikiPageVersionCount?.let {
            sb.append("<b>Wiki changes</b>: $it<br>")
        }
        user.artistVersionCount?.let {
            sb.append("<b>Artist changes</b>: $it<br>")
        }
        user.flagCount?.let {
            sb.append("<b>Posts flagged</b>: $it<br>")
        }
        
        sb.append("<br>")
        
        // Tags favoritos
        val favTags = user.favoriteTags?.trim() ?: ""
        sb.append("<b>Favourite tags</b>:<br>")
        sb.append(if (favTags.isEmpty()) "None" else favTags)
        sb.append("<br><br>")
        
        // Blacklist
        val blacklist = user.blacklistedTags?.trim() ?: ""
        sb.append("<b>Blacklist</b>:<br>")
        sb.append(if (blacklist.isEmpty()) "None" else blacklist.replace("\n", "<br>"))
        sb.append("<br><br>")
        
        // Tags recientes
        val recentTags = user.recentTags?.trim() ?: ""
        sb.append("<b>Recent tags</b>:<br>")
        sb.append(if (recentTags.isEmpty()) "None" else recentTags)
        
        binding.txtContent.text = Html.fromHtml(sb.toString(), Html.FROM_HTML_MODE_LEGACY)
    }
    
    /**
     * Formatea una fecha ISO a formato legible
     */
    private fun formatDate(isoDate: String): String {
        return try {
            // Simplificar: solo mostrar fecha y hora
            val parts = isoDate.split("T")
            if (parts.size >= 2) {
                "${parts[0]} ${parts[1].substringBefore(".")}"
            } else {
                isoDate
            }
        } catch (e: Exception) {
            isoDate
        }
    }
    
    /**
     * Carga el avatar del usuario
     * Busca el post con el ID del avatar y usa su preview
     */
    private fun loadAvatar(avatarId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = api.getPost(avatarId)
                if (response.isSuccessful && response.body() != null) {
                    val post = response.body()!!.post
                    val avatarUrl = post.preview?.url ?: post.sample?.url
                    
                    withContext(Dispatchers.Main) {
                        avatarUrl?.let { url ->
                            Glide.with(this@ProfileActivity)
                                .load(url)
                                .centerCrop()
                                .into(binding.imgBg)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun showError() {
        binding.progressBar.visibility = View.GONE
        binding.lLError.visibility = View.VISIBLE
        Toast.makeText(this, R.string.profile_error, Toast.LENGTH_SHORT).show()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_profile, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.viewFavourites -> {
                currentUser?.let { user ->
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        putExtra("tags", "fav:${user.name}")
                    })
                }
                true
            }
            R.id.viewUploads -> {
                currentUser?.let { user ->
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        putExtra("tags", "user:${user.name}")
                    })
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    /**
     * Maneja deep links para /users/{username}
     * Similar a F() en la app original (ProfileActivity.java línea 45-60)
     */
    private fun handleDeepLink(): String? {
        if (intent.action != Intent.ACTION_VIEW) return null
        
        val data = intent.data ?: return null
        
        // El path es /users/{username} - extraemos el último segmento
        return data.lastPathSegment
    }
}
