package com.complaintalert

import android.app.AlarmManager
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

    private val handler =
        android.os.Handler(
            android.os.Looper.getMainLooper()
        )

    private val intervalMs = 10_000L

    private val alertChannelId =
        "complaint_alert_high_v3"

    private val serviceChannelId =
        "complaint_monitor_service"

    private val runnable =
        object : Runnable {

            override fun run() {

                Thread {
                    checkTickets()
                }.start()

                handler.postDelayed(
                    this,
                    intervalMs
                )
            }
        }

    override fun onCreate() {

        super.onCreate()

        getSharedPreferences(
            "config",
            MODE_PRIVATE
        )
            .edit()
            .putBoolean(
                "monitoringEnabled",
                true
            )
            .apply()

        createChannel()

        startForeground(
            1001,
            foregroundNotification(
                "Monitoring new LESCO complaints"
            )
        )

        handler.post(runnable)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        getSharedPreferences(
            "config",
            MODE_PRIVATE
        )
            .edit()
            .putBoolean(
                "monitoringEnabled",
                true
            )
            .apply()

        return START_STICKY
    }

    override fun onTaskRemoved(
        rootIntent: Intent?
    ) {

        try {

            val restartIntent =
                Intent(
                    this,
                    RestartReceiver::class.java
                )

            val pi =
                PendingIntent.getBroadcast(
                    this,
                    2001,
                    restartIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
                )

            val alarm =
                getSystemService(
                    ALARM_SERVICE
                ) as AlarmManager

            alarm.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 2000L,
                pi
            )

        } catch (_: Exception) {
        }

        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {

        handler.removeCallbacks(
            runnable
        )

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null

    private fun checkTickets() {

        try {

            val prefs =
                getSharedPreferences(
                    "config",
                    MODE_PRIVATE
                )

            val baseUrl =
                prefs.getString(
                    "url",
                    ""
                ) ?: ""

            val key =
                prefs.getString(
                    "key",
                    ""
                ) ?: ""

            if (
                baseUrl.isBlank() ||
                key.isBlank()
            ) {
                return
            }

            val root =
                JSONObject(
                    httpGet(
                        buildUrl(
                            baseUrl,
                            "pending",
                            key
                        )
                    )
                )

            if (
                !root.optBoolean(
                    "success",
                    false
                )
            ) {
                return
            }

            val tickets =
                root.optJSONArray(
                    "tickets"
                ) ?: JSONArray()

            val current =
                HashSet<String>()

            for (
                i in 0 until tickets.length()
            ) {

                val no =
                    tickets
                        .getJSONObject(i)
                        .optString(
                            "ticketNo",
                            ""
                        )

                if (
                    no.isNotBlank()
                ) {
                    current.add(no)
                }
            }

            val stored =
                prefs.getString(
                    "knownPending",
                    ""
                ) ?: ""

            val initialized =
                prefs.getBoolean(
                    "monitorInitialized",
                    false
                )

            val known =
                if (!initialized) {
                    emptySet()
                } else {
                    stored
                        .split("|")
                        .filter {
                            it.isNotBlank()
                        }
                        .toSet()
                }

            if (initialized) {

                for (
                    i in 0 until tickets.length()
                ) {

                    val ticket =
                        tickets.getJSONObject(i)

                    val no =
                        ticket.optString(
                            "ticketNo",
                            ""
                        )

                    if (
                        no.isNotBlank() &&
                        !known.contains(no)
                    ) {

                        showComplaintNotification(
                            ticket
                        )
                    }
                }
            }

            prefs.edit()
                .putString(
                    "knownPending",
                    current.joinToString("|")
                )
                .putBoolean(
                    "monitorInitialized",
                    true
                )
                .apply()

        } catch (_: Exception) {
            // Keep monitoring alive during temporary API/network failures.
        }
    }

    private fun showComplaintNotification(
        ticket: JSONObject
    ) {

        val ticketNo =
            ticket.optString(
                "ticketNo",
                "New Ticket"
            )

        val subDivision =
            ticket.optString(
                "subDivisionCode",
                ""
            )

        val issueType =
            ticket.optString(
                "issueType",
                ""
            )

        val issue =
            ticket.optString(
                "issue",
                ""
            )

        val priority =
            ticket.optString(
                "priority",
                "Normal"
            )

        val intent =
            Intent(
                this,
                MainActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP

                putExtra(
                    "ticketNo",
                    ticketNo
                )

                putExtra(
                    "issueType",
                    issueType
                )

                putExtra(
                    "issue",
                    issue
                )
            }

        val pending =
            PendingIntent.getActivity(
                this,
                ticketNo.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val notification =
            NotificationCompat.Builder(
                this,
                alertChannelId
            )
                .setSmallIcon(
                    R.drawable.ic_stat_complaint
                )

                .setContentTitle(
                    "New LESCO Complaint"
                )

                .setContentText(
                    "$ticketNo • $issueType"
                )

                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(
                            "Ticket: $ticketNo\n" +
                            "Sub Division: $subDivision\n" +
                            "Issue: $issueType\n" +
                            "Priority: $priority\n" +
                            "Issue: $issue"
                        )
                )

                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )

                .setCategory(
                    NotificationCompat.CATEGORY_ALARM
                )

                .setAutoCancel(true)

                .setDefaults(
                    NotificationCompat.DEFAULT_ALL
                )

                .setContentIntent(
                    pending
                )

                .build()

        try {

            if (
                Build.VERSION.SDK_INT < 33 ||
                androidx.core.content.ContextCompat
                    .checkSelfPermission(
                        this,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {

                getSystemService(
                    NotificationManager::class.java
                ).notify(
                    ticketNo.hashCode(),
                    notification
                )
            }

        } catch (_: SecurityException) {
        }
    }

    private fun foregroundNotification(
        message: String
    ): Notification {

        val intent =
            Intent(
                this,
                MainActivity::class.java
            )

        val pending =
            PendingIntent.getActivity(
                this,
                1002,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat.Builder(
            this,
            serviceChannelId
        )
            .setSmallIcon(
                R.drawable.ic_stat_complaint
            )
            .setContentTitle(
                "LESCO Complaint Alert"
            )
            .setContentText(
                message
            )
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }

    private fun createChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            val alerts =
                NotificationChannel(
                    alertChannelId,
                    "New Complaint Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {

                    description =
                        "Immediate alerts for new LESCO IT support complaints"

                    enableVibration(true)

                    vibrationPattern =
                        longArrayOf(
                            0,
                            350,
                            180,
                            500,
                            180,
                            700
                        )

                    enableLights(true)

                    setShowBadge(true)
                }

            val service =
                NotificationChannel(
                    serviceChannelId,
                    "Monitoring Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {

                    description =
                        "Background monitoring status"

                    setShowBadge(false)
                }

            manager.createNotificationChannel(
                alerts
            )

            manager.createNotificationChannel(
                service
            )
        }
    }

    private fun buildUrl(
        base: String,
        action: String,
        key: String
    ): String {

        return base +
                "?action=" +
                URLEncoder.encode(
                    action,
                    "UTF-8"
                ) +
                "&key=" +
                URLEncoder.encode(
                    key,
                    "UTF-8"
                )
    }

    private fun httpGet(
        api: String
    ): String {

        val conn =
            URL(api)
                .openConnection()
                    as HttpURLConnection

        conn.requestMethod = "GET"
        conn.connectTimeout = 12000
        conn.readTimeout = 12000

        return try {

            conn.inputStream
                .bufferedReader()
                .use {
                    it.readText()
                }

        } finally {

            conn.disconnect()
        }
    }
}
