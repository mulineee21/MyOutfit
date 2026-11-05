package com.example.myoutfit.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.myoutfit.MainActivity
import com.example.myoutfit.R
import com.example.myoutfit.receiver.OutfitExpirationReceiver
import java.time.Instant

object OutfitNotificationHelper {

    const val CHANNEL_ID = "outfit_24h_expiration_channel"
    const val CHANNEL_NAME = "Caducidad de Outfits 24h"
    const val NOTIFICATION_ID = 24001
    const val ALARM_REQUEST_CODE = 24002

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones automáticas cuando tus fotos diarias de 24h expiran"
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun scheduleExpiration(context: Context, expiresAtIso: String, caption: String = "") {
        val expiresEpochMillis = runCatching {
            Instant.parse(expiresAtIso).toEpochMilli()
        }.getOrNull() ?: return

        scheduleExpiration(context, expiresEpochMillis, caption)
    }

    fun scheduleExpiration(context: Context, expiresEpochMillis: Long, caption: String = "") {
        val now = System.currentTimeMillis()
        if (expiresEpochMillis <= now) {
            showExpirationNotification(context, caption)
            return
        }

        createNotificationChannel(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, OutfitExpirationReceiver::class.java).apply {
            action = OutfitExpirationReceiver.ACTION_OUTFIT_EXPIRED
            putExtra(OutfitExpirationReceiver.EXTRA_CAPTION, caption)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    expiresEpochMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    expiresEpochMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                expiresEpochMillis,
                pendingIntent
            )
        }
    }

    fun showExpirationNotification(context: Context, caption: String = "") {
        createNotificationChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subtitle = if (caption.isNotBlank()) " \"$caption\"" else ""
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⏳ Tu outfit de 24h ha expirado")
            .setContentText("Tu foto diaria$subtitle se ha guardado en tu Calendario Privado. ¡Es momento de subir tu look de hoy!")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Tu publicación diaria$subtitle ha cumplido 24 horas y ha dejado de ser visible públicamente. Ya está guardada a salvo en tu Calendario Privado para tu resumen anual. ¡Toca aquí para compartir tu nuevo estilo de hoy!"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        val manager = NotificationManagerCompat.from(context)
        try {
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Falta de permiso POST_NOTIFICATIONS en Android 13+
        }
    }
}
