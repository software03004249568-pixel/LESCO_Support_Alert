package com.complaintalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("monitoringEnabled", false)
        val url = prefs.getString("url", "") ?: ""
        val key = prefs.getString("key", "") ?: ""

        if (enabled && url.isNotBlank() && key.isNotBlank()) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, MonitorService::class.java)
                )
            } catch (_: Exception) {
                // Android/OEM policy may block a background FGS restart.
                // The normal START_STICKY path and boot receiver remain available.
            }
        }
    }
}
