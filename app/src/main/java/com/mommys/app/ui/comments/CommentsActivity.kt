package com.mommys.app.ui.comments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.mommys.app.R
import com.mommys.app.data.api.ApiClient
import com.mommys.app.data.model.Comment
import com.mommys.app.data.model.Post
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.databinding.ActivityCommentsBinding
import com.mommys.app.ui.post.CommentsViewModel
import com.mommys.app.ui.profile.ProfileActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity para ver y gestionar comentarios de un post
 * Basado en CommentsActivity.java de la app original
 * 
 * Soporta:
 * - Ver comentarios de un post específico
 * - Deep links: /comments/{id} (redirige al post del comentario)
 * - Crear comentarios
 * - Ordenar comentarios (nuevo, antiguo, score)
 * - Votar comentarios (upvote/downvote)
 * - Reply a comentarios
 * - Editar/Eliminar comentarios propios
 */
class CommentsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_POST_ID = "post_id"
        const val EXTRA_COMMENT_COUNT = "comment_count"
        const val EXTRA_POST_PREVIEW = "post_preview"
        const val EXTRA_FROM_POST = "from_post" // Si viene de PostActivity
        
        // Static post holder para evitar serialización
        var currentPost: Post? = null
        
        /**
         * Crear intent para abrir comentarios de un post
         */
        fun newIntent(context: Context, postId: Int, commentCount: Int = 0, previewUrl: String? = null): Intent {
            return Intent(context, CommentsActivity::class.java).apply {
                putExtra(EXTRA_POST_ID, postId)
                putExtra(EXTRA_COMMENT_COUNT, commentCount)
                previewUrl?.let { putExtra(EXTRA_POST_PREVIEW, it) }
            }
        }
        
        /**
         * Crear intent desde un Post
         */
        fun newIntent(context: Context, post: Post): Intent {
            currentPost = post
            return Intent(context, CommentsActivity::class.java).apply {
                putExtra(EXTRA_POST_ID, post.id)
                putExtra(EXTRA_COMMENT_COUNT, post.commentCount)
                putExtra(EXTRA_POST_PREVIEW, post.preview?.url ?: post.sample?.url)
                putExtra(EXTRA_FROM_POST, true)
            }
        }
    }

    private lateinit var binding: ActivityCommentsBinding
    private val viewModel: CommentsViewModel by viewModels()
    private lateinit var adapter: CommentsActivityAdapter
    private lateinit var prefsManager: PreferencesManager
    
    private var postId: Int = 0
    private var commentCount: Int = 0
    private var previewUrl: String? = null
    private var currentSortOrder: Int = 1 // 0=oldest, 1=newest, 2=score

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommentsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefsManager = PreferencesManager(this)
        
        // Configurar toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.comments_title)
        }
        
        // Tint del navigation icon
        binding.toolbar.navigationIcon?.setColorFilter(
            ContextCompat.getColor(this, R.color.colorWhite),
            PorterDuff.Mode.SRC_ATOP
        )
        
        // Obtener datos del intent o deep link
        handleIntent()
        
        if (postId == 0) {
            Toast.makeText(this, "Invalid post", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Título con conteo
        supportActionBar?.title = if (commentCount > 0) {
            "${getString(R.string.comments_title)} ($commentCount)"
        } else {
            getString(R.string.comments_title)
        }
        
        setupHeader()
        setupRecyclerView()
        setupObservers()
        
        // Cargar comentarios
        viewModel.loadComments(postId)
    }
    
    private fun handleIntent() {
        val data = intent.data
        
        if (data != null) {
            // Deep link: /comments/{id}
            handleDeepLink(data)
        } else {
            // Intent normal
            postId = intent.getIntExtra(EXTRA_POST_ID, 0)
            commentCount = intent.getIntExtra(EXTRA_COMMENT_COUNT, 0)
            previewUrl = intent.getStringExtra(EXTRA_POST_PREVIEW)
            
            // Intentar obtener preview de currentPost
            if (previewUrl == null && currentPost != null) {
                previewUrl = currentPost?.preview?.url ?: currentPost?.sample?.url
            }
        }
    }
    
    /**
     * Manejar deep link de comentarios
     * /comments/{id} - El id es del comentario, necesitamos obtener el post_id
     */
    private fun handleDeepLink(uri: Uri) {
        val path = uri.path ?: return
        
        // Extraer ID del comentario de /comments/123
        val regex = Regex("/comments/(\\d+)")
        val match = regex.find(path)
        
        if (match != null) {
            val commentId = match.groupValues[1].toIntOrNull()
            if (commentId != null) {
                // Cargar el comentario para obtener el post_id
                loadCommentById(commentId)
                return
            }
        }
        
        // Si no se pudo parsear, cerrar
        finish()
    }
    
    /**
     * Cargar un comentario por su ID para obtener el post_id
     * GET /comments/{id}.json
     */
    private fun loadCommentById(commentId: Int) {
        setLoading(true)
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.getComment(commentId)
                }
                
                if (response.isSuccessful) {
                    val comment = response.body()
                    if (comment != null) {
                        postId = comment.postId
                        // Ahora cargar todos los comentarios del post
                        viewModel.loadComments(postId)
                        supportActionBar?.title = getString(R.string.comments_title)
                    } else {
                        Toast.makeText(this@CommentsActivity, "Comment not found", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    Toast.makeText(this@CommentsActivity, "Error loading comment", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CommentsActivity, e.message, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    private fun setupHeader() {
        // Cargar imagen de preview del post en la toolbar colapsable
        if (!previewUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(previewUrl)
                .centerCrop()
                .into(binding.imagePreview)
            binding.imagePreview.visibility = View.VISIBLE
        } else {
            binding.imagePreview.visibility = View.GONE
        }
    }
    
    private fun setupRecyclerView() {
        adapter = CommentsActivityAdapter(
            currentUsername = prefsManager.username,
            onAuthorClick = { authorName ->
                // Abrir perfil del autor
                val intent = Intent(this, ProfileActivity::class.java)
                intent.putExtra(ProfileActivity.EXTRA_USERNAME, authorName)
                startActivity(intent)
            },
            onReplyClick = { comment ->
                showReplyDialog(comment)
            },
            onVoteUpClick = { comment ->
                voteComment(comment.id, 1)
            },
            onVoteDownClick = { comment ->
                voteComment(comment.id, -1)
            },
            onReportClick = { comment ->
                reportComment(comment)
            },
            onEditClick = { comment ->
                showEditCommentDialog(comment)
            },
            onDeleteClick = { comment ->
                confirmDeleteComment(comment)
            },
            onCopyClick = { comment ->
                copyToClipboard(comment.body)
            }
        )
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@CommentsActivity)
            adapter = this@CommentsActivity.adapter
        }
    }
    
    private fun setupObservers() {
        viewModel.comments.observe(this) { comments ->
            if (comments.isEmpty()) {
                binding.txtEmpty.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
            } else {
                binding.txtEmpty.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE
                sortAndSubmit(comments)
            }
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            setLoading(isLoading)
        }
        
        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }
        
        viewModel.commentPosted.observe(this) { success ->
            if (success == true) {
                Toast.makeText(this, R.string.comment_posted, Toast.LENGTH_SHORT).show()
                viewModel.loadComments(postId)
            }
        }
        
        viewModel.commentEdited.observe(this) { success ->
            if (success == true) {
                Toast.makeText(this, R.string.comment_edited, Toast.LENGTH_SHORT).show()
            }
        }
        
        viewModel.commentDeleted.observe(this) { success ->
            if (success == true) {
                Toast.makeText(this, R.string.comment_deleted, Toast.LENGTH_SHORT).show()
            }
        }
        
        viewModel.voteResult.observe(this) { result ->
            result?.let {
                if (it.success) {
                    val message = when {
                        it.ourScore > 0 -> getString(R.string.action_voted_up)
                        it.ourScore < 0 -> getString(R.string.action_voted_down)
                        else -> getString(R.string.action_vote_removed)
                    }
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }
    
    /**
     * Ordenar comentarios según el orden seleccionado
     * Como método G() en CommentsActivity original
     */
    private fun sortAndSubmit(comments: List<Comment>) {
        val sorted = when (currentSortOrder) {
            0 -> comments.sortedBy { it.id }  // Oldest first
            1 -> comments.sortedByDescending { it.id }  // Newest first
            2 -> comments.sortedByDescending { it.score }  // By score
            else -> comments
        }
        adapter.submitList(sorted)
    }
    
    // ==================== Menu ====================
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_comments, menu)
        // Tint icons
        for (i in 0 until menu.size()) {
            menu.getItem(i).icon?.setColorFilter(
                ContextCompat.getColor(this, R.color.colorWhite),
                PorterDuff.Mode.SRC_ATOP
            )
        }
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.create_comment -> {
                showAddCommentDialog()
                true
            }
            R.id.order_by -> {
                showSortMenu()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    // ==================== Sort Menu ====================
    
    private fun showSortMenu() {
        if (binding.progressBar.visibility == View.VISIBLE) {
            Toast.makeText(this, R.string.comment_sort_wait, Toast.LENGTH_SHORT).show()
            return
        }
        
        val anchor = findViewById<View>(R.id.order_by) ?: binding.toolbar
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_comments_sort, popup.menu)
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort_by_new -> {
                    currentSortOrder = 1
                    sortAndSubmit(viewModel.comments.value ?: emptyList())
                    true
                }
                R.id.sort_by_old -> {
                    currentSortOrder = 0
                    sortAndSubmit(viewModel.comments.value ?: emptyList())
                    true
                }
                R.id.sort_by_score -> {
                    currentSortOrder = 2
                    sortAndSubmit(viewModel.comments.value ?: emptyList())
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }
    
    // ==================== Dialogs ====================
    
    private fun showAddCommentDialog() {
        if (!prefsManager.isLoggedIn) {
            Toast.makeText(this, R.string.login_required, Toast.LENGTH_SHORT).show()
            return
        }
        
        val editText = EditText(this).apply {
            hint = getString(R.string.comment_create_hint)
            isSingleLine = false
            setLines(4)
            maxLines = 20
            movementMethod = ScrollingMovementMethod.getInstance()
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.comment_create_title)
            .setMessage(R.string.comment_create_message)
            .setView(editText)
            .setPositiveButton(R.string.comment_post_reply) { _, _ ->
                val commentBody = editText.text.toString().trim()
                if (commentBody.isNotEmpty()) {
                    viewModel.postComment(postId, commentBody)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun showReplyDialog(comment: Comment) {
        if (!prefsManager.isLoggedIn) {
            Toast.makeText(this, R.string.login_required, Toast.LENGTH_SHORT).show()
            return
        }
        
        val editText = EditText(this).apply {
            hint = getString(R.string.comment_create_hint)
            isSingleLine = false
            setLines(4)
            maxLines = 20
            movementMethod = ScrollingMovementMethod.getInstance()
            // Pre-fill with quote
            setText("[quote]${comment.creatorName ?: "User"} said:\n${comment.body}\n[/quote]\n\n")
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.comment_reply)
            .setView(editText)
            .setPositiveButton(R.string.comment_post_reply) { _, _ ->
                val replyBody = editText.text.toString().trim()
                if (replyBody.isNotEmpty()) {
                    viewModel.postComment(postId, replyBody)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun showEditCommentDialog(comment: Comment) {
        val editText = EditText(this).apply {
            hint = getString(R.string.comment_create_hint)
            isSingleLine = false
            setLines(4)
            maxLines = 20
            setText(comment.body)
            movementMethod = ScrollingMovementMethod.getInstance()
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.comment_edit)
            .setView(editText)
            .setPositiveButton(R.string.save) { _, _ ->
                val newBody = editText.text.toString().trim()
                if (newBody.isNotEmpty() && newBody != comment.body) {
                    viewModel.editComment(comment.id, newBody)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun confirmDeleteComment(comment: Comment) {
        AlertDialog.Builder(this)
            .setTitle(R.string.comment_delete)
            .setMessage(R.string.comment_delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteComment(comment.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    // ==================== Comment Actions ====================
    
    private fun voteComment(commentId: Int, score: Int) {
        if (!prefsManager.isLoggedIn) {
            Toast.makeText(this, R.string.login_required, Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.voteComment(commentId, score)
    }
    
    private fun reportComment(comment: Comment) {
        val baseUrl = if (prefsManager.safeMode) "https://e926.net" else "https://e621.net"
        val url = "$baseUrl/tickets/new?disp_id=${comment.id}&type=comment"
        
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
    
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Comment", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
}
