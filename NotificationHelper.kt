package com.example.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_LOCATION_SERVICE = "family_location_service_channel"
        const val CHANNEL_LOCATION_REQUESTS = "family_location_requests_channel"
        const val CHANNEL_MESSAGES = "family_messages_channel"
        const val NOTIFICATION_ID_SERVICE = 1001
        const val NOTIFICATION_ID_REQUEST = 1002
        const val NOTIFICATION_ID_MESSAGE = 1003
        const val ACTION_STOP_SERVICE = "com.example.familyguardian.ACTION_STOP_SERVICE"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_LOCATION_SERVICE,
                "اشتراک‌گذاری موقعیت مکانی (Location Service)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "نمایش اعلان همیشگی هنگام استفاده فعال از موقعیت مکانی در پس‌زمینه"
            }

            val requestChannel = NotificationChannel(
                CHANNEL_LOCATION_REQUESTS,
                "درخواست‌های موقعیت مکانی والدین (Location Requests)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "اعلان فوری هنگام درخواست موقعیت توسط والدین"
                enableVibration(true)
            }

            val messageChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "پیام‌های خانواده (Family Messages)",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "پیام‌های متنی ارسال شده میان والدین و فرزندان"
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(requestChannel)
            notificationManager.createNotificationChannel(messageChannel)
        }
    }

    fun buildForegroundLocationNotification(): Notification {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_LOCATION_SERVICE)
            .setContentTitle("Family Guardian - پایش فعال موقعیت")
            .setContentText("موقعیت مکانی دستگاه برای سرپرست خانواده در دسترس است.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun showLocationRequestNotification(parentName: String = "والدین", note: String = "") {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            1,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = if (note.isNotBlank()) "پیام: $note" else "سرپرست درخواست به‌روزرسانی موقعیت مکانی کرده است."

        val notification = NotificationCompat.Builder(context, CHANNEL_LOCATION_REQUESTS)
            .setContentTitle("📍 درخواست موقعیت مکانی از طرف $parentName")
            .setContentText(bodyText)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_REQUEST, notification)
    }

    fun showMessageNotification(senderName: String, text: String) {
        val launchIntent = Intent(context, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            2,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setContentTitle("💬 پیام جدید از $senderName")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_MESSAGE, notification)
    }
}
