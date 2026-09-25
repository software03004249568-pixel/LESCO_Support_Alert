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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MonitorService : Service() {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val intervalMs = 60_000L
    private val channelId = "complaint_monitor"

    private val runnable = object : Runnable {
        override fun run() {
            Thread { checkTickets() }.start()
            handler.postDelayed(this, intervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(
            1001,
            foregroundNotification("Complaint monitoring is running")
        )
        handler.post(runnable)
    }

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

            val api =
                baseUrl +
                "?action=tickets&key=" +
                URLEncoder.encode(key, "UTF-8") +
                "&limit=20"

            val text = httpGet(api)
            val root = JSONObject(text)

            if (!root.optBoolean("success", false)) return

            val tickets = root.optJSONArray("tickets") ?: return
            if (tickets.length() == 0) return

            val newest = tickets.getJSONObject(0)
            val newestTicket = newest.optString("ticketNo", "")
            if (newestTicket.isBlank()) return

            val lastTicket = prefs.getString("lastTicket", "") ?: ""

            // First run: establish baseline without generating a false alert.
            if (lastTicket.isBlank()) {
                prefs.edit().putString("lastTicket", newestTicket).apply()
                return
            }

            if (newestTicket != lastTicket) {
                prefs.edit().putString("lastTicket", newestTicket).apply()
                showComplaintNotification(newest)
            }

        } catch (_: Exception) {
            // Network failures do not stop monitoring.
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

    private fun showComplaintNotification(ticket: JSONObject) {
        val ticketNo = ticket.optString("ticketNo", "New Ticket")
        val subDivision = ticket.optString("subDivisionCode", "")
        val issueType = ticket.optString("issueType", "")
        val issue = ticket.optString("issue", "")
        val priority = ticket.optString("priority", "Normal")

        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            2002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification =
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("ONE COMPLAINT REGISTERED")
                .setContentText("$ticketNo • $subDivision • $issueType")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Ticket: $ticketNo\n" +
                        "Sub Division: $subDivision\n" +
                        "Issue Type: $issueType\n" +
                        "Priority: $priority\n" +
                        "Issue: $issue"
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()

        getSystemService(NotificationManager::class.java)
            .notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun foregroundNotification(message: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            1002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Complaint Alert")
            .setContentText(message)
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Complaint Monitoring",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "LESCO IT Support Ticket monitoring"
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
