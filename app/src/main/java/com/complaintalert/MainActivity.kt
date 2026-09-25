package com.complaintalert

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var pendingText: TextView
    private lateinit var resolvedText: TextView
    private lateinit var urgentText: TextView
    private lateinit var complaintList: LinearLayout
    private lateinit var emptyText: TextView

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlEdit = findViewById(R.id.urlEdit)
        keyEdit = findViewById(R.id.keyEdit)
        connectionText = findViewById(R.id.connectionText)
        totalText = findViewById(R.id.totalText)
        pendingText = findViewById(R.id.pendingText)
        resolvedText = findViewById(R.id.resolvedText)
        urgentText = findViewById(R.id.urgentText)
        complaintList = findViewById(R.id.complaintList)
        emptyText = findViewById(R.id.emptyText)

        val prefs = getSharedPreferences("config", MODE_PRIVATE)
        urlEdit.setText(prefs.getString("url", ""))
        keyEdit.setText(prefs.getString("key", ""))

        requestNotificationPermission()

        findViewById<Button>(R.id.startButton).setOnClickListener { saveAndStart() }
        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopService(Intent(this, MonitorService::class.java))
            connectionText.text = "● Monitoring Stopped"
        }
        findViewById<Button>(R.id.refreshButton).setOnClickListener { refreshAll() }

        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
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

        getSharedPreferences("config", MODE_PRIVATE).edit()
            .putString("url", url)
            .putString("key", key)
            .apply()

        ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java))
        connectionText.text = "● Monitoring Active"
        refreshAll()
    }

    private fun refreshAll() {
        val url = urlEdit.text?.toString()?.trim().orEmpty()
        val key = keyEdit.text?.toString()?.trim().orEmpty()
        if (!url.startsWith("https://") || key.isBlank()) return

        executor.execute {
            try {
                val statsRoot = JSONObject(httpGet(buildUrl(url, "stats", key)))
                val pendingRoot = JSONObject(httpGet(buildUrl(url, "pending", key)))

                if (!statsRoot.optBoolean("success") || !pendingRoot.optBoolean("success")) {
                    throw Exception("API returned an error")
                }

                val stats = statsRoot.getJSONObject("stats")
                val tickets = pendingRoot.optJSONArray("tickets")

                runOnUiThread {
                    totalText.text = "Total\n${stats.optInt("total") }"
                    pendingText.text = "Pending\n${pendingRoot.optInt("count") }"
                    resolvedText.text = "Resolved\n${stats.optInt("resolved") }"
                    urgentText.text = "Urgent\n${stats.optInt("urgent") }"
                    renderTickets(tickets)
                    connectionText.text = "● Monitoring Active / Server Connected"
                }
            } catch (_: Exception) {
                runOnUiThread { connectionText.text = "● Server check failed" }
            }
        }
    }

    private fun renderTickets(tickets: org.json.JSONArray?) {
        complaintList.removeAllViews()

        if (tickets == null || tickets.length() == 0) {
            emptyText.visibility = View.VISIBLE
            return
        }

        emptyText.visibility = View.GONE

        for (i in 0 until tickets.length()) {
            val ticket = tickets.getJSONObject(i)
            complaintList.addView(createTicketRow(ticket))
        }
    }

    private fun createTicketRow(ticket: JSONObject): View {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(10)) }
            radius = dp(12).toFloat()
            isClickable = true
            isFocusable = true
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val no = TextView(this).apply {
            text = ticket.optString("ticketNo", "-")
            textSize = 17f
            setTextColor(Color.rgb(13, 71, 161))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val status = TextView(this).apply {
            text = ticket.optString("status", "New")
            textSize = 14f
            setTextColor(Color.rgb(198, 40, 40))
            setTypeface(null, Typeface.BOLD)
        }

        top.addView(no)
        top.addView(status)
        box.addView(top)

        val issueType = TextView(this).apply {
            text = "Issue: ${ticket.optString("issueType", "-")}"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(5), 0, 0)
        }
        box.addView(issueType)

        val issue = TextView(this).apply {
            text = ticket.optString("issue", "-")
            textSize = 14f
            maxLines = 3
            setPadding(0, dp(3), 0, 0)
        }
        box.addView(issue)

        val sub = TextView(this).apply {
            text = "Sub Division: ${ticket.optString("subDivisionCode", "-")}    Priority: ${ticket.optString("priority", "Normal")}"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(7), 0, 0)
        }
        box.addView(sub)

        card.addView(box)
        card.setOnClickListener { showTicketDetails(ticket) }
        return card
    }

    private fun showTicketDetails(ticket: JSONObject) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(4), dp(22), 0)
        }

        fun addField(label: String, value: String) {
            val tv = TextView(this).apply {
                text = "$label\n${if (value.isBlank()) "-" else value}"
                textSize = 15f
                setPadding(0, dp(7), 0, dp(7))
            }
            layout.addView(tv)
        }

        addField("Complaint No", ticket.optString("ticketNo"))
        addField("Date", ticket.optString("date"))
        addField("Sub Division", ticket.optString("subDivisionCode"))
        addField("Operator", ticket.optString("operatorName"))
        addField("Mobile", ticket.optString("mobileNumber"))
        addField("Issue Type", ticket.optString("issueType"))
        addField("Issue", ticket.optString("issue"))
        addField("Priority", ticket.optString("priority"))
        addField("Status", ticket.optString("status"))
        addField("Remarks", ticket.optString("remarks"))

        val dialog = AlertDialog.Builder(this)
            .setTitle("Complaint Details")
            .setView(layout)
            .setNegativeButton("Close", null)
            .setPositiveButton("Resolve", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                resolveTicket(ticket.optString("ticketNo"), dialog)
            }
        }
        dialog.show()
    }

    private fun resolveTicket(ticketNo: String, dialog: AlertDialog) {
        val baseUrl = urlEdit.text?.toString()?.trim().orEmpty()
        val key = keyEdit.text?.toString()?.trim().orEmpty()

        executor.execute {
            try {
                val url = buildUrl(
                    baseUrl,
                    "resolve",
                    key,
                    mapOf("ticketNo" to ticketNo, "resolution" to "Resolved from Complaint Alert app")
                )
                val root = JSONObject(httpGet(url))
                if (!root.optBoolean("success")) throw Exception(root.optString("message", "Resolve failed"))

                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, "Complaint resolved successfully", Toast.LENGTH_SHORT).show()
                    refreshAll()
                }
            } catch (ex: Exception) {
                runOnUiThread {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    Toast.makeText(this, "Resolve failed: ${ex.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun buildUrl(base: String, action: String, key: String, extra: Map<String, String> = emptyMap()): String {
        val params = LinkedHashMap<String, String>()
        params["action"] = action
        params["key"] = key
        params.putAll(extra)
        return base + "?" + params.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
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
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
