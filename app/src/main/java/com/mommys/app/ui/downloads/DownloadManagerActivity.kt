package com.mommys.app.ui.downloads

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.mommys.app.R
import com.mommys.app.data.db.downloads.AppDownloadsDatabase
import com.mommys.app.data.db.downloads.DownloadItem
import com.mommys.app.service.DownloadQueueService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity para gestionar la cola de descargas
 * Como DownloadManagerActivity en la app original
 */
class DownloadManagerActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var statusText: TextView
    private lateinit var emptyText: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnClearAll: Button
    
    private lateinit var database: AppDownloadsDatabase
    private lateinit var adapter: DownloadAdapter
    private val items = mutableListOf<DownloadItem>()
    private val scope = CoroutineScope(Dispatchers.Main)
    
    private val handler = Handler(Looper.getMainLooper())
    private val statusUpdateRunnable = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 1000)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_manager)
        
        // Setup toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        
        // Init views
        recyclerView = findViewById(R.id.recyclerView)
        statusText = findViewById(R.id.status)
        emptyText = findViewById(R.id.emptyText)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnClearAll = findViewById(R.id.btnClearAll)
        
        // Init database
        database = AppDownloadsDatabase.getInstance(applicationContext)
        
        // Setup RecyclerView
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(DividerItemDecoration(this, layoutManager.orientation))
        
        adapter = DownloadAdapter(items, database) { count ->
            updateEmptyState(count)
        }
        recyclerView.adapter = adapter
        
        // Load downloads
        loadDownloads()
        
        // Button listeners
        btnStart.setOnClickListener {
            DownloadQueueService.start(this)
            Toast.makeText(this, R.string.download_manager_started, Toast.LENGTH_SHORT).show()
        }
        
        btnStop.setOnClickListener {
            DownloadQueueService.stop()
            Toast.makeText(this, R.string.stopped, Toast.LENGTH_SHORT).show()
        }
        
        btnClearAll.setOnClickListener {
            showClearAllDialog()
        }
        
        // Start status updates
        handler.post(statusUpdateRunnable)
    }
    
    private fun loadDownloads() {
        scope.launch {
            val downloads = withContext(Dispatchers.IO) {
                database.downloadDao().getAllDownloads()
            }
            items.clear()
            items.addAll(downloads)
            adapter.notifyDataSetChanged()
            updateEmptyState(items.size)
        }
    }
    
    private fun updateEmptyState(count: Int) {
        if (count == 0) {
            emptyText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }
    
    private fun updateStatus() {
        if (isFinishing || isDestroyed) return
        
        val isRunning = DownloadQueueService.isRunning()
        statusText.text = getString(
            if (isRunning) R.string.download_manager_status_running
            else R.string.download_manager_status_not_running
        )
    }
    
    private fun showClearAllDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.download_manager_clear_all_dialog_title)
            .setMessage(R.string.download_manager_clear_all_dialog_message)
            .setPositiveButton(R.string.yes) { _, _ ->
                clearAllDownloads()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }
    
    private fun clearAllDownloads() {
        scope.launch {
            withContext(Dispatchers.IO) {
                database.downloadDao().deleteAll()
            }
            items.clear()
            adapter.notifyDataSetChanged()
            updateEmptyState(0)
            Toast.makeText(this@DownloadManagerActivity, R.string.cleared, Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }
}
