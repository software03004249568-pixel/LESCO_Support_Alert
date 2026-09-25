package com.complaintalert

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var urlEdit: EditText
    private lateinit var keyEdit: EditText
    private lateinit var connectionText: TextView
    private lateinit var totalText: TextView
    private lateinit var newText: TextView
    private lateinit var progressText: TextView
    private lateinit var urgentText: TextView
    private lateinit var lastTicketText: TextView

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlEdit = findViewById(R.id.urlEdit)
        keyEdit = findViewById(R.id.keyEdit)
        connectionText = findViewById(R.id.connectionText)
        totalText = findViewById(R.id.totalText)
        newText = findViewById(R.id.newText)
        progressText = findViewById(R.id.progressText)
        urgentText = findViewById(R.id.urgentText)
        lastTicketText = findViewById(R.id.lastTicketText)

        val prefs = getSharedPreferences("config", MODE_PRIVATE)
        urlEdit.setText(prefs.getString("url", ""))
        keyEdit.setText(prefs.getString("key", ""))

        requestNotificationPermission()

        findViewById<Button>(R.id.startButton).setOnClickListener {
            saveAndStart()
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopService(Intent(this, MonitorService::class.java))
            connectionText.text = "● Monitoring Stopped"
        }

        findViewById<Button>(R.id.refreshButton).setOnClickListener {
            refreshDashboard()
        }

        refreshDashboard()
    }

    private fun saveAndStart() {
        val url = urlEdit.text?.toString()?.trim().orEmpty()
        val key = keyEdit.text?.toString()?.trim().orEmpty()

        if (!url.startsWith("https://")) {
            connectionText.text = "● Invalid Web App URL"
            return
        }

        if (key.isBlank()) {
            connectionText.text = "● API Key is required"
            return
        }

        getSharedPreferences("config", MODE_PRIVATE)
            .edit()
            .putString("url", url)
            .putString("key", key)
            .apply()

        ContextCompat.startForegroundService(
            this,
            Intent(this, MonitorService::class.java)
        )

        connectionText.text = "● Monitoring Active"
        refreshDashboard()
    }

    private fun refreshDashboard() {
        val url = urlEdit.text?.toString()?.trim().orEmpty()
        val key = keyEdit.text?.toString()?.trim().orEmpty()

        if (!url.startsWith("https://") || key.isBlank()) return

        executor.execute {
            try {
                val api = url +
                    "?action=stats&key=" +
                    URLEncoder.encode(key, "UTF-8")

                val text = httpGet(api)
                val root = JSONObject(text)

                if (!root.optBoolean("success", false)) return@execute

                val stats = root.getJSONObject("stats")

                runOnUiThread {
                    totalText.text = "Total\n${stats.optInt("total")}"
                    newText.text = "New\n${stats.optInt("new")}"
                    progressText.text = "In Progress\n${stats.optInt("inProgress")}"
                    urgentText.text = "Urgent\n${stats.optInt("urgent")}"
                }

                val ticketsUrl = url +
                    "?action=tickets&key=" +
                    URLEncoder.encode(key, "UTF-8") +
                    "&limit=1"

                val ticketText = httpGet(ticketsUrl)
                val ticketRoot = JSONObject(ticketText)
                val tickets = ticketRoot.optJSONArray("tickets")

                if (tickets != null && tickets.length() > 0) {
                    val t = tickets.getJSONObject(0)
                    runOnUiThread {
                        lastTicketText.text =
                            "Last Ticket: ${t.optString("ticketNo")}\n" +
                            "Status: ${t.optString("status")}\n" +
                            "Issue: ${t.optString("issueType")}"
                    }
                }

            } catch (_: Exception) {
                runOnUiThread {
                    connectionText.text = "● Server check failed"
                }
            }
        }
    }

    private fun httpGet(api: String): String {
        val conn = URL(api).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
