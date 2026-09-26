package com.complaintalert

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
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
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var urlEdit: TextInputEditText
    private lateinit var keyEdit: TextInputEditText
    private lateinit var configCard: MaterialCardView
    private lateinit var configSavedText: TextView
    private lateinit var connectionText: TextView
    private lateinit var totalText: TextView
    private lateinit var pendingText: TextView
    private lateinit var resolvedText: TextView
    private lateinit var urgentText: TextView
    private lateinit var complaintList: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var changeConfigButton: Button
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("config", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlEdit = findViewById(R.id.urlEdit)
        keyEdit = findViewById(R.id.keyEdit)
        configCard = findViewById(R.id.configCard)
        configSavedText = findViewById(R.id.configSavedText)
        connectionText = findViewById(R.id.connectionText)
        totalText = findViewById(R.id.totalText)
        pendingText = findViewById(R.id.pendingText)
        resolvedText = findViewById(R.id.resolvedText)
        urgentText = findViewById(R.id.urgentText)
        complaintList = findViewById(R.id.complaintList)
        emptyText = findViewById(R.id.emptyText)
        changeConfigButton = findViewById(R.id.changeConfigButton)

        urlEdit.setText(prefs.getString("url", ""))
        keyEdit.setText(prefs.getString("key", ""))
        updateConfigUi()
        requestNotificationPermission()

        findViewById<Button>(R.id.saveStartButton).setOnClickListener { saveAndStart() }
        findViewById<Button>(R.id.stopButton).setOnClickListener { stopMonitoring() }
        findViewById<Button>(R.id.refreshButton).setOnClickListener { refreshAll() }
        changeConfigButton.setOnClickListener { showConfiguration() }

        // Once configured, monitoring starts automatically whenever the app is opened.
        if (hasValidConfig()) {
            startMonitoringService()
            requestBatteryOptimizationExemption()
            refreshAll()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasValidConfig()) refreshAll()
    }

    private fun hasValidConfig(): Boolean {
        val url = prefs.getString("url", "") ?: ""
        val key = prefs.getString("key", "") ?: ""
        return url.startsWith("https://") && key.isNotBlank()
    }

    private fun saveAndStart() {
        val url = urlEdit.text?.toString()?.trim().orEmpty()
        val key = keyEdit.text?.toString()?.trim().orEmpty()
        if (!url.startsWith("https://") || !url.contains("/exec")) {
            connectionText.text = "● Enter a valid Google Apps Script /exec URL"
            return
        }
        if (key.isBlank()) {
            connectionText.text = "● API Key is required"
            return
        }
        prefs.edit().putString("url", url).putString("key", key).putBoolean("monitoringEnabled", true).apply()
        updateConfigUi()
        startMonitoringService()
        requestBatteryOptimizationExemption()
        refreshAll()
    }

    private fun startMonitoringService() {
        prefs.edit().putBoolean("monitoringEnabled", true).apply()
        ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java))
        connectionText.text = "● Monitoring Active / Server Connecting…"
    }

    private fun stopMonitoring() {
        stopService(Intent(this, MonitorService::class.java))
        prefs.edit().putBoolean("monitoringEnabled", false).apply()
        connectionText.text = "● Monitoring Paused"
    }

    private fun updateConfigUi() {
        val configured = hasValidConfig()
        configSavedText.visibility = if (configured) View.VISIBLE else View.GONE
        urlEdit.visibility = if (configured) View.GONE else View.VISIBLE
        keyEdit.visibility = if (configured) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.saveStartButton).visibility = if (configured) View.GONE else View.VISIBLE
        changeConfigButton.visibility = if (configured) View.VISIBLE else View.GONE
    }

    private fun showConfiguration() {
        urlEdit.visibility = View.VISIBLE
        keyEdit.visibility = View.VISIBLE
        findViewById<Button>(R.id.saveStartButton).visibility = View.VISIBLE
        changeConfigButton.visibility = View.GONE
    }

    private fun refreshAll() {
        if (!hasValidConfig()) return
        val url = prefs.getString("url", "") ?: return
        val key = prefs.getString("key", "") ?: return
        executor.execute {
            try {
                // One dashboard request avoids partial failures caused by two separate
                // Apps Script calls and keeps the UI/API contract consistent.
                val root = JSONObject(httpGet(buildUrl(url, "dashboard", key)))
                if (!root.optBoolean("success", false)) {
                    throw Exception(root.optString("message", "API returned an error"))
                }
                val stats = root.optJSONObject("stats") ?: JSONObject()
                val tickets = root.optJSONArray("tickets") ?: JSONArray()
                val count = root.optInt("count", tickets.length())
                runOnUiThread {
                    totalText.text = "Total\n${stats.optInt("total")}"
                    pendingText.text = "Pending\n$count"
                    resolvedText.text = "Resolved\n${stats.optInt("resolved")}"
                    urgentText.text = "Urgent\n${stats.optInt("urgent")}"
                    renderTickets(tickets)
                    connectionText.text = "● Monitoring Active / Server Connected"
                }
            } catch (ex: Exception) {
                runOnUiThread {
                    connectionText.text = "● Server check failed: ${ex.message ?: "Connection error"}"
                }
            }
        }
    }

    private fun renderTickets(tickets: JSONArray?) {
        complaintList.removeAllViews()
        if (tickets == null || tickets.length() == 0) {
            emptyText.visibility = View.VISIBLE
            return
        }
        emptyText.visibility = View.GONE
        for (i in 0 until tickets.length()) complaintList.addView(createTicketRow(tickets.getJSONObject(i)))
    }

    private fun createTicketRow(ticket: JSONObject): View {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(10)) }
            radius = dp(16).toFloat()
            strokeWidth = dp(1)
            strokeColor = Color.rgb(219, 228, 240)
            cardElevation = dp(2).toFloat()
            isClickable = true
            isFocusable = true
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(13), dp(15), dp(13)) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val no = TextView(this).apply {
            text = ticket.optString("ticketNo", "-"); textSize = 17f; setTextColor(Color.rgb(18, 75, 150)); setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val status = TextView(this).apply { text = ticket.optString("status", "New"); textSize = 14f; setTextColor(Color.rgb(198, 40, 40)); setTypeface(null, Typeface.BOLD) }
        top.addView(no); top.addView(status); box.addView(top)
        box.addView(TextView(this).apply { text = "Issue: ${ticket.optString("issueType", "-")}"; textSize = 15f; setTypeface(null, Typeface.BOLD); setPadding(0, dp(6), 0, 0) })
        box.addView(TextView(this).apply { text = ticket.optString("issue", "-"); textSize = 14f; maxLines = 3; setPadding(0, dp(3), 0, 0) })
        box.addView(TextView(this).apply { text = "Sub Division: ${ticket.optString("subDivisionCode", "-")}    Priority: ${ticket.optString("priority", "Normal")}"; textSize = 13f; setTextColor(Color.DKGRAY); setPadding(0, dp(7), 0, 0) })
        card.addView(box)
        card.setOnClickListener { showTicketDetails(ticket) }
        return card
    }

    private fun showTicketDetails(ticket: JSONObject) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(4), dp(22), 0) }
        fun addField(label: String, value: String) {
            layout.addView(TextView(this).apply { text = "$label\n${if (value.isBlank()) "-" else value}"; textSize = 15f; setPadding(0, dp(7), 0, dp(7)) })
        }
        addField("Complaint No", ticket.optString("ticketNo")); addField("Date", ticket.optString("date")); addField("Sub Division", ticket.optString("subDivisionCode")); addField("Operator", ticket.optString("operatorName")); addField("Mobile", ticket.optString("mobileNumber")); addField("Issue Type", ticket.optString("issueType")); addField("Issue", ticket.optString("issue")); addField("Priority", ticket.optString("priority")); addField("Status", ticket.optString("status")); addField("Remarks", ticket.optString("remarks"))
        val dialog = AlertDialog.Builder(this).setTitle("Complaint Details").setView(layout).setNegativeButton("Close", null).setPositiveButton("Resolve", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false; resolveTicket(ticket.optString("ticketNo"), dialog) } }
        dialog.show()
    }

    private fun resolveTicket(ticketNo: String, dialog: AlertDialog) {
        val baseUrl = prefs.getString("url", "") ?: ""
        val key = prefs.getString("key", "") ?: ""
        executor.execute {
            try {
                val root = JSONObject(httpGet(buildUrl(baseUrl, "resolve", key, mapOf("ticketNo" to ticketNo, "resolution" to "Resolved from Complaint Alert app"))))
                if (!root.optBoolean("success")) throw Exception(root.optString("message", "Resolve failed"))
                runOnUiThread { dialog.dismiss(); Toast.makeText(this, "Complaint resolved successfully", Toast.LENGTH_SHORT).show(); refreshAll() }
            } catch (ex: Exception) {
                runOnUiThread { dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true; Toast.makeText(this, "Resolve failed: ${ex.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (_: Exception) { }
    }

    private fun buildUrl(base: String, action: String, key: String, extra: Map<String, String> = emptyMap()): String {
        val params = LinkedHashMap<String, String>(); params["action"] = action; params["key"] = key; params.putAll(extra)
        return base + "?" + params.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
    }

    private fun httpGet(api: String): String {
        val conn = URL(api).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"; conn.connectTimeout = 15000; conn.readTimeout = 15000
        return try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
