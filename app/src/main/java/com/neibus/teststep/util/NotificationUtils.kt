package com.neibus.teststep.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.neibus.teststep.MainActivity

object NotificationUtils {

    private val notificiationId = 123

    fun createNotification(context: Context, walkCount: Int ,callback: (Int, NotificationCompat.Builder)-> Unit) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val url = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audio = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()

        val channelId= "BetterWalk"
        val channelName= "walk"

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notification Description"
            setShowBadge(true)
            setSound(url, audio)
            enableLights(true)
            lightColor = Color.CYAN
        }

        manager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(context,channelId).apply {
            setSmallIcon(com.google.android.material.R.drawable.ic_clock_black_24dp)
            setContentIntent(createPendingIntent(context))
            setWhen(System.currentTimeMillis())
            setContentTitle("BetterWalk")
            setContentText("현재 걸음수 : $walkCount")
        }

        callback(notificiationId, builder)
    }

    private fun createPendingIntent(context: Context): PendingIntent {
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(context,0,intent,flag)
    }
}