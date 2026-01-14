package com.mommys.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.mommys.app.R
import com.mommys.app.data.db.logs.AppLogDatabase
import com.mommys.app.data.db.logs.LogAdapter
import com.mommys.app.data.db.logs.LogEntity

/**
 * Activity for viewing application logs (Following notifications log)
 */
class LogActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var toolbar: MaterialToolbar
    private lateinit var adapter: LogAdapter
    private lateinit var database: AppLogDatabase
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)
        
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)
        
        database = AppLogDatabase.getInstance(this)
        
        adapter = LogAdapter()
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
        
        loadLogs()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_log, menu)
        return super.onCreateOptionsMenu(menu)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.clear -> {
                clearLogs()
                true
            }
            R.id.share -> {
                shareLogs()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun loadLogs() {
        Thread {
            val logs = database.logDao().getAllLogs()
            runOnUiThread {
                adapter.setLogs(logs)
                updateEmptyView(logs.isEmpty())
            }
        }.start()
    }
    
    private fun clearLogs() {
        Thread {
            database.logDao().clearAll()
            runOnUiThread {
                adapter.setLogs(emptyList())
                updateEmptyView(true)
            }
        }.start()
    }
    
    private fun shareLogs() {
        val logs = adapter.getLogs()
        val sb = StringBuilder()
        for (log in logs) {
            sb.append(log.toString())
            sb.append("\n")
        }
        
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, sb.toString())
            type = "text/plain"
        }
        startActivity(Intent.createChooser(intent, "Share logs"))
    }
    
    private fun updateEmptyView(isEmpty: Boolean) {
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
}
