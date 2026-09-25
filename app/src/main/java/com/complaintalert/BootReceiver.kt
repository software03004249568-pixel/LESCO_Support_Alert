package com.complaintalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)
        val url = prefs.getString("url", "") ?: ""
        val key = prefs.getString("key", "") ?: ""

        if (url.isNotBlank() && key.isNotBlank()) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitorService::class.java)
            )
        }
    }
}
