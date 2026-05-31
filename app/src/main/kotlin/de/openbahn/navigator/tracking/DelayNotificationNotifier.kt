package de.openbahn.navigator.tracking

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.openbahn.navigator.MainActivity
import de.openbahn.navigator.OpenBahnApplication
import de.openbahn.navigator.R

class DelayNotificationNotifier(private val context: Context) {

    fun showDelay(trackedJourneyId: String, from: String, to: String, delayMinutes: Int) {
        if (!canPostNotifications()) return
        val contentIntent = PendingIntent.getActivity(
            context,
            trackedJourneyId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                action = TrackingNotificationIntent.ACTION_OPEN_TRACKED_JOURNEY
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(TrackingNotificationIntent.EXTRA_TRACKED_JOURNEY_ID, trackedJourneyId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, OpenBahnApplication.CHANNEL_DELAY_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.delay_notification_title))
            .setContentText(
                context.getString(R.string.delay_notification_body, from, to, delayMinutes),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(trackedJourneyId.hashCode(), notification)
    }

    private fun canPostNotifications(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU
}
