package com.complaintalert

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class ReportsActivity : AppCompatActivity() {
    private lateinit var fromDateButton: MaterialButton
    private lateinit var toDateButton: MaterialButton
    private lateinit var generateButton: MaterialButton
    private lateinit var exportButton: MaterialButton
    private lateinit var reportMessage: TextView
    private lateinit var totalText: TextView
    private lateinit var pendingText: TextView
    private lateinit var resolvedText: TextView
    private lateinit var emptyText: TextView
    private lateinit var reportList: LinearLayout
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("config", MODE_PRIVATE) }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var fromDate = ""
    private var toDate = ""
    private var csvContent = ""

    private val createCsvDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // Excel UTF-8 BOM
                output.write(csvContent.toByteArray(StandardCharsets.UTF_8))
            } ?: throw Exception("Could not open the selected file location")
            Toast.makeText(this, "Report exported successfully", Toast.LENGTH_LONG).show()
        } catch (ex: Exception) {
            Toast.makeText(this, "Export failed: ${ex.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)
        supportActionBar?.title = "Complaint Reports"

        fromDateButton = findViewById(R.id.fromDateButton)
        toDateButton = findViewById(R.id.toDateButton)
        generateButton = findViewById(R.id.generateButton)
        exportButton = findViewById(R.id.exportButton)
        reportMessage = findViewById(R.id.reportMessage)
        totalText = findViewById(R.id.reportTotal)
        pendingText = findViewById(R.id.reportPending)
        resolvedText = findViewById(R.id.reportResolved)
        emptyText = findViewById(R.id.reportEmpty)
        reportList = findViewById(R.id.reportList)

        val today = Calendar.getInstance()
        toDate = dateFormat.format(today.time)
        today.set(Calendar.DAY_OF_MONTH, 1)
        fromDate = dateFormat.format(today.time)
        updateDateButtons()

        fromDateButton.setOnClickListener { chooseDate(true) }
        toDateButton.setOnClickListener { chooseDate(false) }
        generateButton.setOnClickListener { generateReport() }
        exportButton.setOnClickListener {
            if (csvContent.isNotBlank()) createCsvDocument.launch("LESCO_Complaint_Report_${fromDate}_to_${toDate}.csv")
        }
    }

    private fun chooseDate(isFrom: Boolean) {
        val value = if (isFrom) fromDate else toDate
        val parsed = try { dateFormat.parse(value) ?: Date() } catch (_: Exception) { Date() }
        val calendar = Calendar.getInstance().apply { time = parsed }
        DatePickerDialog(this, { _, year, month, day ->
            val selected = Calendar.getInstance().apply { set(year, month, day) }
            if (isFrom) fromDate = dateFormat.format(selected.time) else toDate = dateFormat.format(selected.time)
            updateDateButtons()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDateButtons() {
        fromDateButton.text = "From: $fromDate"
        toDateButton.text = "To: $toDate"
    }

    private fun generateReport() {
        if (fromDate > toDate) {
            reportMessage.text = "From Date cannot be later than To Date."
            return
        }
        val baseUrl = prefs.getString("url", "") ?: ""
        val key = prefs.getString("key", "") ?: ""
        if (baseUrl.isBlank() || key.isBlank()) {
            reportMessage.text = "App configuration is missing. Return to the main screen and configure it."
            return
        }
        val requestedFrom = fromDate
        val requestedTo = toDate
        generateButton.isEnabled = false
        exportButton.isEnabled = false
        reportMessage.text = "Loading complaints for $requestedFrom to $requestedTo…"
        executor.execute {
            try {
                val api = buildUrl(baseUrl, key, mapOf("action" to "report", "fromDate" to requestedFrom, "toDate" to requestedTo))
                val root = JSONObject(httpGet(api))
                if (!root.optBoolean("success", false)) throw Exception(root.optString("message", "Report request failed"))
                val tickets = root.optJSONArray("tickets") ?: JSONArray()
                val stats = root.optJSONObject("stats") ?: JSONObject()
                val csv = makeCsv(tickets)
                runOnUiThread {
                    csvContent = csv
                    totalText.text = "Total\n${stats.optInt("total", tickets.length())}"
                    pendingText.text = "Pending\n${stats.optInt("pending")}"
                    resolvedText.text = "Resolved\n${stats.optInt("resolved") + stats.optInt("closed")}"
                    reportMessage.text = "${tickets.length()} complaint(s) found from $requestedFrom to $requestedTo."
                    renderTickets(tickets)
                    exportButton.isEnabled = tickets.length() > 0
                    generateButton.isEnabled = true
                }
            } catch (ex: Exception) {
                runOnUiThread {
                    reportMessage.text = "Report failed: ${ex.message ?: "Connection error"}"
                    generateButton.isEnabled = true
                }
            }
        }
    }

    private fun renderTickets(tickets: JSONArray) {
        reportList.removeAllViews()
        if (tickets.length() == 0) {
            emptyText.visibility = View.VISIBLE
            emptyText.text = "No complaints found for the selected date range."
            return
        }
        emptyText.visibility = View.GONE
        for (i in 0 until tickets.length()) {
            val item = tickets.getJSONObject(i)
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(10)) }
                radius = dp(15).toFloat()
                strokeWidth = dp(1)
                strokeColor = android.graphics.Color.rgb(219, 228, 240)
                cardElevation = dp(2).toFloat()
            }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val ticketNo = TextView(this).apply {
                text = item.optString("ticketNo", "-")
                textSize = 16f
                setTextColor(android.graphics.Color.rgb(13, 71, 161))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val status = TextView(this).apply {
                text = item.optString("status", "New")
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(if (text.equals("Resolved", ignoreCase = true) || text.equals("Closed", ignoreCase = true)) android.graphics.Color.rgb(46, 125, 50) else android.graphics.Color.rgb(230, 81, 0))
            }
            titleRow.addView(ticketNo); titleRow.addView(status); box.addView(titleRow)
            addDetail(box, "Complaint Date", item.optString("date"))
            addDetail(box, "Sub Division", item.optString("subDivisionCode"))
            addDetail(box, "Operator", item.optString("operatorName"))
            addDetail(box, "Mobile", item.optString("mobileNumber"))
            addDetail(box, "Issue Type", item.optString("issueType"))
            addDetail(box, "Issue / Problem", item.optString("issue"))
            addDetail(box, "Priority", item.optString("priority"))
            addDetail(box, "Resolved By", item.optString("closedBy"))
            addDetail(box, "Resolved Date", item.optString("closedDate"))
            addDetail(box, "Resolution / Remarks", listOf(item.optString("resolution"), item.optString("remarks")).filter { it.isNotBlank() }.joinToString(" — "))
            card.addView(box)
            reportList.addView(card)
        }
    }

    private fun addDetail(parent: LinearLayout, label: String, value: String) {
        val clean = if (value.isBlank() || value == "null") "-" else value
        parent.addView(TextView(this).apply {
            text = "$label: $clean"
            textSize = 13.5f
            setTextColor(android.graphics.Color.rgb(69, 80, 92))
            setPadding(0, dp(4), 0, 0)
        })
    }

    private fun makeCsv(tickets: JSONArray): String {
        val headers = listOf("Ticket No", "Complaint Date", "Sub Division Code", "Operator Name", "Mobile Number", "Issue Type", "Issue / Problem", "Priority", "Status", "Resolved By", "Resolved Date", "Resolution", "Remarks")
        val out = StringBuilder()
        out.append(headers.joinToString(",") { csvCell(it) }).append("\r\n")
        for (i in 0 until tickets.length()) {
            val t = tickets.getJSONObject(i)
            val values = listOf("ticketNo", "date", "subDivisionCode", "operatorName", "mobileNumber", "issueType", "issue", "priority", "status", "closedBy", "closedDate", "resolution", "remarks").map { t.optString(it, "") }
            out.append(values.joinToString(",") { csvCell(it) }).append("\r\n")
        }
        return out.toString()
    }

    private fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""

    private fun buildUrl(base: String, key: String, extras: Map<String, String>): String {
        val params = LinkedHashMap<String, String>()
        params["key"] = key
        params.putAll(extras)
        return base + "?" + params.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
    }

    private fun httpGet(api: String): String {
        val conn = URL(api).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        return try {
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}: $body")
            body
        } finally { conn.disconnect() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
