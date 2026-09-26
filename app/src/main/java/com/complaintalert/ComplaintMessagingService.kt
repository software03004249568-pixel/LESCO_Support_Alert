package com.complaintalert

import android.app.Notification
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

class ComplaintMessagingService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID = "complaints_high_v4"
        private const val CHANNEL_NAME = "New LESCO Complaints"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onNewToken(token: String) {

        val prefs = getSharedPreferences("config", MODE_PRIVATE)

        val url = prefs.getString("url", "") ?: ""
        val key = prefs.getString("key", "") ?: ""
        val user = prefs.getString("userName", "") ?: ""

        if (url.isBlank() || key.isBlank()) {
            return
        }

        Thread {

            try {

                val apiUrl =
                    url +
                    "?action=registerDevice" +
                    "&key=" + java.net.URLEncoder.encode(key, "UTF-8") +
                    "&token=" + java.net.URLEncoder.encode(token, "UTF-8") +
                    "&userName=" + java.net.URLEncoder.encode(user, "UTF-8")

                val connection =
                    java.net.URL(apiUrl).openConnection()
                            as java.net.HttpURLConnection

                try {

                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    connection.inputStream.bufferedReader().use {
                        it.readText()
                    }

                } finally {

                    connection.disconnect()
                }

            } catch (_: Exception) {
                // Token registration failure should not crash the app.
            }

        }.start()
    }

    override fun onMessageReceived(message: RemoteMessage) {

        val data = message.data

        val ticketNo =
            data["ticketNo"]
                ?: message.notification?.title
                ?: "New Complaint"

        val subDivision =
            data["subDivisionCode"]
                ?: ""

        val issueType =
            data["issueType"]
                ?: ""

        val issue =
            data["issue"]
                ?: message.notification?.body
                ?: "A new LESCO complaint has been registered."

        val priority =
            data["priority"]
                ?: "Normal"

        showNotification(
            ticketNo = ticketNo,
            subDivision = subDivision,
            issueType = issueType,
            issue = issue,
            priority = priority
        )
    }

    private fun showNotification(
        ticketNo: String,
        subDivision: String,
        issueType: String,
        issue: String,
        priority: String
    ) {

        createNotificationChannel()

        val intent =
            Intent(this, MainActivity::class.java).apply {

                flags =
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP

                putExtra("ticketNo", ticketNo)
                putExtra("issueType", issueType)
                putExtra("issue", issue)
            }

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                ticketNo.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val bigText =
            "Ticket: $ticketNo\n" +
            "Sub Division: $subDivision\n" +
            "Issue Type: $issueType\n" +
            "Priority: $priority\n" +
            "Issue: $issue"

        val notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_complaint)

                .setContentTitle(
                    "New LESCO Complaint"
                )

                .setContentText(
                    "$ticketNo — $issueType"
                )

                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(bigText)
                )

                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )

                .setCategory(
                    NotificationCompat.CATEGORY_ALARM
                )

                .setAutoCancel(true)

                .setDefaults(
                    Notification.DEFAULT_ALL
                )

                .setVibrate(
                    longArrayOf(
                        0,
                        400,
                        200,
                        400,
                        200,
                        700
                    )
                )

                .setContentIntent(
                    pendingIntent
                )

                .build()

        try {

            NotificationManagerCompat
                .from(this)
                .notify(
                    ticketNo.hashCode(),
                    notification
                )

        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission is required on Android 13+.
        }
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        val existing =
            manager.getNotificationChannel(CHANNEL_ID)

        if (existing != null) {
            return
        }

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {

                description =
                    "Immediate alerts for new LESCO IT support complaints"

                enableVibration(true)

                vibrationPattern =
                    longArrayOf(
                        0,
                        400,
                        200,
                        400,
                        200,
                        700
                    )

                enableLights(true)

                setShowBadge(true)

                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder()
                        .setUsage(
                            AudioAttributes.USAGE_NOTIFICATION
                        )
                        .build()
                )
            }

        manager.createNotificationChannel(channel)
    }
}
