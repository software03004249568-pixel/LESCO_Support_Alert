package com.complaintalert

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ComplaintMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val p=getSharedPreferences("config",MODE_PRIVATE); val url=p.getString("url","")?: ""; val key=p.getString("key","")?: ""; val user=p.getString("userName","")?: ""
        if(url.isBlank()||key.isBlank()) return
        Thread { try { val u=java.net.URL(url+"?action=registerDevice&key="+java.net.URLEncoder.encode(key,"UTF-8")+"&token="+java.net.URLEncoder.encode(token,"UTF-8")+"&userName="+java.net.URLEncoder.encode(user,"UTF-8")); (u.openConnection() as java.net.HttpURLConnection).apply{requestMethod="GET";connectTimeout=10000;readTimeout=10000;inputStream.close();disconnect()} } catch(_:Exception){} }.start()
    }
    override fun onMessageReceived(m: RemoteMessage){ val d=m.data; show(d["ticketNo"]?:"New Complaint",d["issue"]?:"A new LESCO complaint has been registered.") }
    private fun show(ticket:String,issue:String){ val id="complaints_high"; val nm=getSystemService(NOTIFICATION_SERVICE) as NotificationManager; if(Build.VERSION.SDK_INT>=26){val c=NotificationChannel(id,"New LESCO Complaints",NotificationManager.IMPORTANCE_HIGH);c.enableVibration(true);c.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());nm.createNotificationChannel(c)}; val pi=PendingIntent.getActivity(this,9001,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); val n=NotificationCompat.Builder(this,id).setSmallIcon(R.drawable.ic_stat_complaint).setContentTitle("New LESCO Complaint").setContentText("$ticket — $issue").setStyle(NotificationCompat.BigTextStyle().bigText("Ticket: $ticket\n$issue")).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setVibrate(longArrayOf(0,400,200,400)).setContentIntent(pi).build(); NotificationManagerCompat.from(this).notify(ticket.hashCode(),n)}
}
