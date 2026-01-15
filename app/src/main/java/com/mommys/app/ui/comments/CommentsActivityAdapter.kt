package com.mommys.app.ui.comments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mommys.app.R
import com.mommys.app.data.model.Comment
import com.mommys.app.databinding.ItemCommentActivityBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Adapter para CommentsActivity con botones de acción
 * Basado en adapter_comment_item.xml y ei/h.java de la app original
 * Incluye: Reply, Vote Up, Vote Down, Report, Edit, Delete, Copy
 */
class CommentsActivityAdapter(
    private val currentUsername: String? = null,
    private val onAuthorClick: (String) -> Unit = {},
    private val onReplyClick: (Comment) -> Unit = {},
    private val onVoteUpClick: (Comment) -> Unit = {},
    private val onVoteDownClick: (Comment) -> Unit = {},
    private val onReportClick: (Comment) -> Unit = {},
    private val onEditClick: (Comment) -> Unit = {},
    private val onDeleteClick: (Comment) -> Unit = {},
    private val onCopyClick: (Comment) -> Unit = {}
) : ListAdapter<Comment, CommentsActivityAdapter.CommentViewHolder>(CommentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentActivityBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CommentViewHolder(
        private val binding: ItemCommentActivityBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: Comment) {
            val context = binding.root.context
            
            // Author name
            binding.txtCreator.text = comment.creatorName ?: "User #${comment.creatorId}"
            binding.txtCreator.setOnClickListener {
                comment.creatorName?.let { onAuthorClick(it) }
            }

            // Score con color según positivo/negativo
            binding.txtScore.text = comment.score.toString()
            binding.txtScore.setTextColor(
                when {
                    comment.score > 0 -> ContextCompat.getColor(context, R.color.colorGreen)
                    comment.score < 0 -> ContextCompat.getColor(context, R.color.colorRed)
                    else -> ContextCompat.getColor(context, R.color.colorDarkGrey)
                }
            )

            // Details (ID + date) - como la app original
            val dateStr = formatDate(comment.createdAt)
            binding.txtDetails.text = context.getString(
                R.string.comment_item_details,
                comment.id,
                dateStr
            )

            // Body - procesar DText básico
            binding.txtBody.text = processBody(comment.body)

            // Verificar si es comentario propio
            val isOwnComment = currentUsername != null && 
                comment.creatorName?.equals(currentUsername, ignoreCase = true) == true

            // Options button - mostrar menú con edit/delete si es propio
            binding.imgOptions.setOnClickListener { view ->
                showOptionsMenu(view, comment, isOwnComment)
            }

            // Action buttons
            binding.btnReply.setOnClickListener {
                onReplyClick(comment)
            }
            
            binding.btnVoteDown.setOnClickListener {
                onVoteDownClick(comment)
            }
            
            binding.btnVoteUp.setOnClickListener {
                onVoteUpClick(comment)
            }
            
            binding.imgReport.setOnClickListener {
                onReportClick(comment)
            }

            // Badges
            var showBadges = false

            if (comment.isSticky) {
                binding.badgeSticky.visibility = View.VISIBLE
                showBadges = true
            } else {
                binding.badgeSticky.visibility = View.GONE
            }

            if (comment.isHidden) {
                binding.badgeHidden.visibility = View.VISIBLE
                showBadges = true
            } else {
                binding.badgeHidden.visibility = View.GONE
            }

            binding.badgesLayout.visibility = if (showBadges) View.VISIBLE else View.GONE
        }
        
        private fun showOptionsMenu(anchor: View, comment: Comment, isOwnComment: Boolean) {
            val popup = android.widget.PopupMenu(anchor.context, anchor)
            
            // Siempre mostrar copiar
            popup.menu.add(0, 1, 0, R.string.action_copy)
            
            // Solo mostrar edit/delete si es el propio comentario
            if (isOwnComment) {
                popup.menu.add(0, 2, 1, R.string.comment_edit)
                popup.menu.add(0, 3, 2, R.string.comment_delete)
            }
            
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        onCopyClick(comment)
                        true
                    }
                    2 -> {
                        onEditClick(comment)
                        true
                    }
                    3 -> {
                        onDeleteClick(comment)
                        true
                    }
                    else -> false
                }
            }
            
            popup.show()
        }

        /**
         * Formatear fecha ISO a formato legible
         */
        private fun formatDate(isoDate: String?): String {
            if (isoDate == null) return ""
            
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
                inputFormat.timeZone = TimeZone.getTimeZone("UTC")
                val date = inputFormat.parse(isoDate)
                
                val outputFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                date?.let { outputFormat.format(it) } ?: ""
            } catch (e: Exception) {
                // Fallback a formato simple
                isoDate.substringBefore("T")
            }
        }

        /**
         * Procesar DText básico
         * El API devuelve texto con formato DText que incluye quotes, links, etc.
         */
        private fun processBody(body: String): String {
            return body
                // Simplificar quotes [quote]...[/quote]
                .replace(Regex("\\[quote\\].*?\\[/quote\\]", RegexOption.DOT_MATCHES_ALL), "[quoted text]\n")
                // Links internos [[post #123]]
                .replace(Regex("\\[\\[([^\\]]+)\\]\\]")) { match ->
                    match.groupValues[1]
                }
                // Links externos "text":url
                .replace(Regex("\"([^\"]+)\":(https?://[^\\s]+)")) { match ->
                    match.groupValues[1]
                }
                // Bold **text**
                .replace(Regex("\\*\\*([^*]+)\\*\\*")) { match ->
                    match.groupValues[1]
                }
                // Italic *text*
                .replace(Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)")) { match ->
                    match.groupValues[1]
                }
                .trim()
        }
    }

    class CommentDiffCallback : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(oldItem: Comment, newItem: Comment): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Comment, newItem: Comment): Boolean {
            return oldItem == newItem
        }
    }
}
