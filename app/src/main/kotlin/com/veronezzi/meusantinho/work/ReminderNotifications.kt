package com.veronezzi.meusantinho.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.veronezzi.meusantinho.R
import com.veronezzi.meusantinho.domain.model.Round

/**
 * The election-day notification (ARCHITECTURE.md 4.8). Its text is generic on purpose: no
 * candidate, number or pick ever appears in a notification (risk R8), so it is safe to show on
 * the lock screen. Tapping it opens the app, behind the optional app lock.
 */
object ReminderNotifications {
    const val CHANNEL_ID = "election_reminders"
    private const val NOTIFICATION_ID_BASE = 7000

    /** Creates the "Lembretes da eleição" channel (idempotent). */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.reminder_channel_name))
            .setDescription(context.getString(R.string.reminder_channel_description))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /**
     * Posts the reminder for [round]. Returns false, without posting, when the user did not grant
     * POST_NOTIFICATIONS (Android 13+; the UI asks for it when the reminder is turned on) or
     * turned the app's notifications off.
     */
    fun show(context: Context, round: Round): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        ensureChannel(context)
        val title = context.getString(
            if (round == Round.SECOND) R.string.reminder_title_second_round else R.string.reminder_title_first_round,
        )
        val text = context.getString(R.string.reminder_text)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(launchIntent(context))
            .build()
        return try {
            manager.notify(NOTIFICATION_ID_BASE + round.number, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    private fun launchIntent(context: Context): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
