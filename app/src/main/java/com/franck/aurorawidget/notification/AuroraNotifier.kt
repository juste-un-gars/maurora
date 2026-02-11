/**
 * @file AuroraNotifier.kt
 * @description Sends aurora alert notifications when visibility exceeds user threshold.
 */
package com.franck.aurorawidget.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.franck.aurorawidget.MainActivity
import com.franck.aurorawidget.R
import com.franck.aurorawidget.data.preferences.UserPreferences
import com.franck.aurorawidget.data.preferences.UserSettings
import timber.log.Timber

object AuroraNotifier {

    private const val CHANNEL_ID = "aurora_alerts"
    private const val NOTIFICATION_ID = 1001
    private const val COOLDOWN_MS = 3 * 60 * 60 * 1000L // 3 hours

    /**
     * Creates the notification channel. Must be called once at app startup.
     */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        Timber.d("Notification channel created: %s", CHANNEL_ID)
    }

    /**
     * Checks if a notification should be sent and sends it if conditions are met.
     * @param context Application context
     * @param visibilityScore Combined visibility score (0-100), or null if unavailable
     * @param auroraProbability Raw aurora probability (0-100)
     * @param settings Current user settings (threshold, enabled, cooldown)
     * @param prefs UserPreferences instance to update last notification time
     * @return true if notification was sent
     */
    suspend fun notifyIfNeeded(
        context: Context,
        visibilityScore: Double?,
        auroraProbability: Double,
        settings: UserSettings,
        prefs: UserPreferences
    ): Boolean {
        if (!settings.notificationsEnabled) return false

        // Use visibility score if available, otherwise fall back to raw aurora probability
        val score = visibilityScore ?: auroraProbability

        if (score < settings.notificationThreshold) {
            Timber.d("Score %.1f%% below threshold %d%%, no notification", score, settings.notificationThreshold)
            return false
        }

        // Cooldown check
        val now = System.currentTimeMillis()
        val elapsed = now - settings.lastNotificationTime
        if (elapsed < COOLDOWN_MS) {
            Timber.d("Cooldown active (%d min remaining), skipping notification",
                (COOLDOWN_MS - elapsed) / 60_000)
            return false
        }

        sendNotification(context, score)
        prefs.saveLastNotificationTime(now)
        Timber.d("Aurora alert sent: %.1f%%", score)
        return true
    }

    private fun sendNotification(context: Context, score: Double) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.notification_title)
        val text = context.getString(R.string.notification_text, score.toInt())

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_aurora_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Timber.w("Notification permission not granted")
        }
    }
}
