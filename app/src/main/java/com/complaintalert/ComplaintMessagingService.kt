package com.complaintalert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ComplaintMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        registerToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {

        val data = message.data

        val ticketNo =
            data["ticketNo"]
                ?: data["ticket"]
                ?: "New Complaint"

        val issue =
            data["issue"]
                ?: data["issueType"]
                ?: "A new LESCO complaint has been registered."

        showNotification(ticketNo, issue)
    }

    private fun registerToken(token: String) {

        val prefs = getSharedPreferences("config", MODE_PRIVATE)

        val url = prefs.getString("url", "") ?: ""
        val key = prefs.getString("key", "") ?: ""
        val userName = prefs.getString("userName", "") ?: ""

        if (url.isBlank() || key.isBlank()) {
            return
        }

        Thread {

            try {

                val fullUrl =
                    url +
                    "?action=registerDevice" +
                    "&key=" + encode(key) +
                    "&token=" + encode(token) +
                    "&userName=" + encode(userName)

                val connection =
                    URL(fullUrl).openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                try {
                    connection.inputStream.bufferedReader().use {
                        it.readText()
                    }
                } finally {
                    connection.disconnect()
                }

            } catch (_: Exception) {
                // Token registration will be retried when FCM refreshes the token.
            }

        }.start()
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    private fun showNotification(
        ticketNo: String,
        issue: String
    ) {

        val channelId = "complaints_high"

        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                channelId,
                "New LESCO Complaints",
                NotificationManager.IMPORTANCE_HIGH
            )

            channel.description =
                "Immediate notifications for new LESCO complaints"

            channel.enableVibration(true)

            channel.vibrationPattern =
                longArrayOf(
                    0,
                    400,
                    200,
                    400,
                    200,
                    600
                )

            channel.setSound(
                android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
            )

            notificationManager.createNotificationChannel(channel)
        }

        val intent =
            Intent(this, MainActivity::class.java).apply {
                flags =
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                ticketNo.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val notification =
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_stat_complaint)
                .setContentTitle("New LESCO Complaint")
                .setContentText("$ticketNo — $issue")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(
                            "Ticket: $ticketNo\n$issue"
                        )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setVibrate(
                    longArrayOf(
                        0,
                        400,
                        200,
                        400
                    )
                )
                .setContentIntent(pendingIntent)
                .build()

        NotificationManagerCompat
            .from(this)
            .notify(
                ticketNo.hashCode(),
                notification
            )
    }
}
