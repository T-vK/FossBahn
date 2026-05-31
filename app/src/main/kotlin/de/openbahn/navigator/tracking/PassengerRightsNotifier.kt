package de.openbahn.navigator.tracking

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import de.openbahn.navigator.MainActivity
import de.openbahn.navigator.OpenBahnApplication
import de.openbahn.navigator.R
import de.openbahn.rights.model.RightsNotificationSuggestion

class PassengerRightsNotifier(private val context: Context) {
    fun show(journeyId: String, suggestion: RightsNotificationSuggestion) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_JOURNEY_ID, journeyId)
            putExtra(EXTRA_OPEN_RIGHTS, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            journeyId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, OpenBahnApplication.CHANNEL_PASSENGER_RIGHTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(suggestion.title)
            .setContentText(suggestion.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(suggestion.body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(journeyId.hashCode() + RIGHTS_NOTIFICATION_ID_OFFSET, notification)
    }

    companion object {
        const val EXTRA_OPEN_RIGHTS = "open_passenger_rights"
        private const val RIGHTS_NOTIFICATION_ID_OFFSET = 50_000
    }
}
