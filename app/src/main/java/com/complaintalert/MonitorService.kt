package com.complaintalert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MonitorService : Service() {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    // Frequent polling keeps the alert close to real-time without exhausting the phone.
    private val intervalMs = 10_000L
    private val channelId = "complaint_alert_high_v2"

    private val runnable = object : Runnable {
        override fun run() {
            Thread { checkTickets() }.start()
            handler.postDelayed(this, intervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1001, foregroundNotification("Monitoring new LESCO complaints"))
        handler.post(runnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(runnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkTickets() {
        try {
            val prefs = getSharedPreferences("config", MODE_PRIVATE)
            val baseUrl = prefs.getString("url", "") ?: ""
            val key = prefs.getString("key", "") ?: ""
            if (baseUrl.isBlank() || key.isBlank()) return

            val root = JSONObject(httpGet(buildUrl(baseUrl, "pending", key)))
            if (!root.optBoolean("success", false)) return
            val tickets = root.optJSONArray("tickets") ?: JSONArray()
            val current = HashSet<String>()
            for (i in 0 until tickets.length()) {
                val no = tickets.getJSONObject(i).optString("ticketNo", "")
                if (no.isNotBlank()) current.add(no)
            }

            val stored = prefs.getString("knownPending", "") ?: ""
            val initialized = prefs.getBoolean("monitorInitialized", false)
            val known = if (!initialized) emptySet() else stored.split("|").filter { it.isNotBlank() }.toSet()

            if (initialized) {
                for (i in 0 until tickets.length()) {
                    val ticket = tickets.getJSONObject(i)
                    val no = ticket.optString("ticketNo", "")
                    if (no.isNotBlank() && !known.contains(no)) showComplaintNotification(ticket)
                }
            }

            prefs.edit().putString("knownPending", current.joinToString("|"))
                .putBoolean("monitorInitialized", true).apply()
        } catch (_: Exception) {
            // Keep the foreground service alive during temporary network/API failures.
        }
    }

    private fun showComplaintNotification(ticket: JSONObject) {
        val ticketNo = ticket.optString("ticketNo", "New Ticket")
        val subDivision = ticket.optString("subDivisionCode", "")
        val issueType = ticket.optString("issueType", "")
        val issue = ticket.optString("issue", "")
        val priority = ticket.optString("priority", "Normal")
        val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pending = PendingIntent.getActivity(this, ticketNo.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_complaint)
            .setContentTitle("New LESCO Complaint")
            .setContentText("$ticketNo • $issueType")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Ticket: $ticketNo\nSub Division: $subDivision\nIssue: $issueType\nPriority: $priority\n$issue"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pending)
            .build()
        getSystemService(NotificationManager::class.java).notify(ticketNo.hashCode(), notification)
    }

    private fun foregroundNotification(message: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 1002, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_complaint)
            .setContentTitle("LESCO Complaint Alert")
            .setContentText(message)
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "LESCO Complaint Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Immediate alerts for new LESCO IT support complaints"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 350, 180, 500, 180, 700)
                enableLights(true)
                setShowBadge(true)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildUrl(base: String, action: String, key: String): String = base + "?action=" + URLEncoder.encode(action, "UTF-8") + "&key=" + URLEncoder.encode(key, "UTF-8")

    private fun httpGet(api: String): String {
        val conn = URL(api).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"; conn.connectTimeout = 12000; conn.readTimeout = 12000
        return try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
    }
}
