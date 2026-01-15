package com.mommys.app.ui.edit

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.mommys.app.R
import com.mommys.app.data.api.ApiClient
import com.mommys.app.data.model.Post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * EditPostActivity - Edit tags and rating of a post
 * Based on se/zepiwolf/tws/EditPostActivity.java from original app
 */
class EditPostActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_POST_ID = "post_id"
        const val EXTRA_POST_POSITION = "pos"
        
        // Static holder for post data (like original app's static field O)
        @JvmStatic
        var currentPost: Post? = null
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var imgPost: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var rGRating: RadioGroup
    private lateinit var eTReason: EditText
    private lateinit var eTArtist: EditText
    private lateinit var eTCopyright: EditText
    private lateinit var eTCharacter: EditText
    private lateinit var eTSpecies: EditText
    private lateinit var eTGeneral: EditText

    private var post: Post? = null
    private var postPosition: Int = -1
    private var hasChanges: Boolean = false
    private val isSubmitting = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_post)

        // Get post from static holder (like original app)
        post = currentPost
        currentPost = null

        postPosition = intent.getIntExtra(EXTRA_POST_POSITION, -1)

        if (post == null || postPosition < 0) {
            finish()
            return
        }

        initViews()
        setupToolbar()
        populateData()
        setupTextWatchers()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        imgPost = findViewById(R.id.imgPost)
        progressBar = findViewById(R.id.progressBar)
        rGRating = findViewById(R.id.rGRating)
        eTReason = findViewById(R.id.eTReason)
        eTArtist = findViewById(R.id.eTArtist)
        eTCopyright = findViewById(R.id.eTCopyright)
        eTCharacter = findViewById(R.id.eTCharacter)
        eTSpecies = findViewById(R.id.eTSpecies)
        eTGeneral = findViewById(R.id.eTGeneral)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.edit_post_title, post!!.id)
    }

    private fun populateData() {
        val p = post ?: return

        // Load post image preview
        val previewUrl = p.preview.url ?: p.sample.url ?: p.file.url
        Glide.with(this)
            .load(previewUrl)
            .into(imgPost)

        // Populate tag fields
        eTArtist.setText(tagsToString(p.tags.artist))
        eTCopyright.setText(tagsToString(p.tags.copyright))
        eTCharacter.setText(tagsToString(p.tags.character))
        eTSpecies.setText(tagsToString(p.tags.species))

        // General tags include general + meta + lore + invalid
        val generalTags = mutableListOf<String>().apply {
            addAll(p.tags.general)
            addAll(p.tags.meta)
            addAll(p.tags.lore)
        }
        eTGeneral.setText(tagsToString(generalTags))

        // Set rating radio button
        when (p.rating.lowercase()) {
            "s" -> rGRating.check(R.id.rBSafe)
            "q" -> rGRating.check(R.id.rBQuestionable)
            "e" -> rGRating.check(R.id.rBExplicit)
        }

        // Listen for rating changes
        rGRating.setOnCheckedChangeListener { _, _ ->
            hasChanges = true
        }

        // Initially disable loading state
        setLoading(false)
    }

    private fun setupTextWatchers() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                hasChanges = true
            }
        }

        eTArtist.addTextChangedListener(textWatcher)
        eTCopyright.addTextChangedListener(textWatcher)
        eTCharacter.addTextChangedListener(textWatcher)
        eTSpecies.addTextChangedListener(textWatcher)
        eTGeneral.addTextChangedListener(textWatcher)
        eTReason.addTextChangedListener(textWatcher)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_edit_post, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_done -> {
                submitChanges()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun submitChanges() {
        if (!hasChanges) {
            Toast.makeText(this, R.string.edit_post_done_no_changes, Toast.LENGTH_SHORT).show()
            return
        }

        if (!isSubmitting.compareAndSet(false, true)) {
            return
        }

        val p = post ?: return

        // Get original tags
        val originalTags = getAllOriginalTags()

        // Get new tags from input fields
        val newTags = mutableListOf<String>().apply {
            addAll(parseTagsFromEditText(eTArtist))
            addAll(parseTagsFromEditText(eTCopyright))
            addAll(parseTagsFromEditText(eTCharacter))
            addAll(parseTagsFromEditText(eTSpecies))
            addAll(parseTagsFromEditText(eTGeneral))
        }

        // Calculate diff
        val addedTags = newTags.filter { it.isNotEmpty() && it !in originalTags }
        val removedTags = originalTags.filter { it.isNotEmpty() && it !in newTags }

        // Get new rating
        val newRating = getSelectedRating()
        val oldRating = p.rating

        // Build confirmation message
        val addedStr = if (addedTags.isNotEmpty()) addedTags.joinToString(" ") else getString(R.string.none)
        val removedStr = if (removedTags.isNotEmpty()) removedTags.joinToString(" ") else getString(R.string.none)

        val message = getString(
            R.string.edit_post_done_confirm,
            addedStr,
            removedStr,
            oldRating,
            newRating
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.edit_post_confirm_title)
            .setMessage(message)
            .setPositiveButton(R.string.edit_post_done_confirm_submit) { _, _ ->
                performSubmit(addedTags, removedTags, newRating)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                isSubmitting.set(false)
            }
            .setOnCancelListener {
                // Solo resetear si el usuario cancela (presiona back)
                isSubmitting.set(false)
            }
            .show()
    }

    private fun performSubmit(addedTags: List<String>, removedTags: List<String>, newRating: String) {
        setLoading(true)

        val p = post ?: return
        val oldRating = p.rating
        
        // Build tag_string_diff like the original app (di/k.java)
        // Added tags without prefix, removed tags with "-" prefix
        val tagDiffBuilder = StringBuilder()
        for (tag in addedTags) {
            if (tag.isNotEmpty()) {
                tagDiffBuilder.append(tag)
                tagDiffBuilder.append(" ")
            }
        }
        for (tag in removedTags) {
            if (tag.isNotEmpty()) {
                tagDiffBuilder.append("-")
                tagDiffBuilder.append(tag)
                tagDiffBuilder.append(" ")
            }
        }
        
        val tagStringDiff = tagDiffBuilder.toString().trim().ifEmpty { null }
        val editReason = eTReason.text.toString().trim().ifEmpty { null }
        
        // Check if rating changed
        val ratingChanged = !newRating.equals(oldRating, ignoreCase = true)
        
        // If no changes at all, abort
        if (tagStringDiff == null && !ratingChanged) {
            setLoading(false)
            isSubmitting.set(false)
            Log.wtf("EditPostActivity", "uploadEdits: no changes to submit")
            Toast.makeText(this, R.string.edit_post_done_no_changes, Toast.LENGTH_SHORT).show()
            return
        }
        
        scope.launch {
            try {
                val api = ApiClient.getApiService()
                
                val response = withContext(Dispatchers.IO) {
                    api.updatePost(
                        postId = p.id,
                        tagStringDiff = tagStringDiff,
                        rating = if (ratingChanged) newRating else null,
                        oldRating = if (ratingChanged) oldRating else null,
                        editReason = editReason
                    )
                }
                
                if (response.isSuccessful) {
                    Toast.makeText(this@EditPostActivity, R.string.edit_post_submit_success, Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMessage = when (response.code()) {
                        401, 403 -> getString(R.string.error_unauthorized)
                        422 -> errorBody ?: getString(R.string.edit_post_submit_error)
                        else -> "HTTP ${response.code()}: $errorBody"
                    }
                    Log.e("EditPostActivity", "Update failed: $errorMessage")
                    Toast.makeText(this@EditPostActivity, errorMessage, Toast.LENGTH_LONG).show()
                    setLoading(false)
                    isSubmitting.set(false)
                }
            } catch (e: Exception) {
                Log.e("EditPostActivity", "Update exception", e)
                Toast.makeText(this@EditPostActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                setLoading(false)
                isSubmitting.set(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE

        val enabled = !loading
        eTArtist.isEnabled = enabled
        eTCopyright.isEnabled = enabled
        eTCharacter.isEnabled = enabled
        eTSpecies.isEnabled = enabled
        eTGeneral.isEnabled = enabled
        eTReason.isEnabled = enabled
        rGRating.isEnabled = enabled
        findViewById<View>(R.id.rBSafe).isEnabled = enabled
        findViewById<View>(R.id.rBQuestionable).isEnabled = enabled
        findViewById<View>(R.id.rBExplicit).isEnabled = enabled
    }

    private fun getAllOriginalTags(): List<String> {
        val p = post ?: return emptyList()
        return mutableListOf<String>().apply {
            addAll(p.tags.artist)
            addAll(p.tags.copyright)
            addAll(p.tags.character)
            addAll(p.tags.species)
            addAll(p.tags.general)
            addAll(p.tags.meta)
            addAll(p.tags.lore)
        }
    }

    private fun getSelectedRating(): String {
        return when (rGRating.checkedRadioButtonId) {
            R.id.rBSafe -> "s"
            R.id.rBQuestionable -> "q"
            R.id.rBExplicit -> "e"
            else -> "q"
        }
    }

    private fun tagsToString(tags: List<String>): String {
        return tags.joinToString("\n")
    }

    private fun parseTagsFromEditText(editText: EditText): List<String> {
        return editText.text.toString()
            .replace("\r\n", "\n")
            .replace("\n", " ")
            .trim()
            .split(" ")
            .filter { it.isNotEmpty() }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
