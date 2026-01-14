package com.mommys.app.ui.post

import android.app.Dialog
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mommys.app.R
import com.mommys.app.data.model.Comment
import com.mommys.app.databinding.BottomSheetCommentsBinding

/**
 * BottomSheet para mostrar comentarios de un post
 * Similar a CommentsActivity de la app original
 */
class CommentsBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_POST_ID = "post_id"
        private const val ARG_COMMENT_COUNT = "comment_count"

        fun newInstance(postId: Int, commentCount: Int = 0): CommentsBottomSheet {
            return CommentsBottomSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_POST_ID, postId)
                    putInt(ARG_COMMENT_COUNT, commentCount)
                }
            }
        }
    }

    private var _binding: BottomSheetCommentsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CommentsViewModel by viewModels()
    private lateinit var adapter: CommentsAdapter

    private var postId: Int = 0
    private var commentCount: Int = 0
    private var currentSortOrder: Int = 1 // 0=oldest, 1=newest, 2=score

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        postId = arguments?.getInt(ARG_POST_ID) ?: 0
        commentCount = arguments?.getInt(ARG_COMMENT_COUNT) ?: 0
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheet = (dialogInterface as BottomSheetDialog)
                .findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                
                // Hacer que ocupe al menos 60% de la pantalla
                val displayMetrics = resources.displayMetrics
                val screenHeight = displayMetrics.heightPixels
                it.layoutParams.height = (screenHeight * 0.7).toInt()
            }
        }
        
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCommentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupButtons()
        setupObservers()

        // Cargar comentarios
        if (postId > 0) {
            viewModel.loadComments(postId)
        }
    }

    private fun setupRecyclerView() {
        adapter = CommentsAdapter(
            onAuthorClick = { authorName ->
                // TODO: Abrir perfil del autor
                Toast.makeText(context, "User: $authorName", Toast.LENGTH_SHORT).show()
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@CommentsBottomSheet.adapter
        }
    }

    private fun setupButtons() {
        // Título con conteo
        binding.txtTitle.text = if (commentCount > 0) {
            getString(R.string.comments_title) + " ($commentCount)"
        } else {
            getString(R.string.comments_title)
        }

        // Botón de ordenar
        binding.btnSort.setOnClickListener { view ->
            showSortMenu(view)
        }

        // Botón de agregar comentario
        binding.btnAdd.setOnClickListener {
            showAddCommentDialog()
        }
    }

    private fun setupObservers() {
        viewModel.comments.observe(viewLifecycleOwner) { comments ->
            if (comments.isEmpty()) {
                binding.txtEmpty.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
            } else {
                binding.txtEmpty.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE
                sortAndSubmit(comments)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.commentPosted.observe(viewLifecycleOwner) { success ->
            if (success == true) {
                Toast.makeText(context, R.string.comment_posted, Toast.LENGTH_SHORT).show()
                // Recargar comentarios
                viewModel.loadComments(postId)
            }
        }
    }

    /**
     * Muestra menú de ordenamiento
     * Como la app original menu_comments_sort
     */
    private fun showSortMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_comments_sort, popup.menu)
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
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

    /**
     * Muestra diálogo para agregar comentario
     * Como la app original onOptionsItemSelected R.id.create_comment
     */
    private fun showAddCommentDialog() {
        val context = context ?: return

        val editText = EditText(context).apply {
            hint = getString(R.string.comment_create_hint)
            isSingleLine = false
            setLines(4)
            maxLines = 20
            movementMethod = ScrollingMovementMethod.getInstance()
        }

        AlertDialog.Builder(context)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
