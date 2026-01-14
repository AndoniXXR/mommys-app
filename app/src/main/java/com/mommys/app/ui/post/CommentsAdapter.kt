package com.mommys.app.ui.post

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mommys.app.data.model.Comment
import com.mommys.app.databinding.ItemCommentBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Adapter para mostrar lista de comentarios
 * Como la app original ei/h.java
 */
class CommentsAdapter(
    private val onAuthorClick: (String) -> Unit = {}
) : ListAdapter<Comment, CommentsAdapter.CommentViewHolder>(CommentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(
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
        private val binding: ItemCommentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: Comment) {
            // Author name
            binding.txtAuthor.text = comment.creatorName ?: "User #${comment.creatorId}"
            binding.txtAuthor.setOnClickListener {
                comment.creatorName?.let { onAuthorClick(it) }
            }

            // Score
            binding.txtScore.text = comment.score.toString()

            // Date - formato simple
            binding.txtDate.text = formatDate(comment.createdAt)

            // Body - procesar DText básico
            binding.txtBody.text = processBody(comment.body)

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
         * Por ahora solo procesamos lo básico
         */
        private fun processBody(body: String): String {
            return body
                // Quitar quotes [quote]...[/quote]
                .replace(Regex("\\[quote\\].*?\\[/quote\\]", RegexOption.DOT_MATCHES_ALL), "")
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
