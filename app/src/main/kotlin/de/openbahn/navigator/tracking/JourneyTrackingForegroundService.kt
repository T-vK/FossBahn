package de.openbahn.navigator.tracking

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import de.openbahn.api.debug.OpenBahnDebugLog
import de.openbahn.navigator.MainActivity
import de.openbahn.navigator.OpenBahnApplication
import de.openbahn.navigator.R
import de.openbahn.navigator.data.TrackedJourneyRepository
import de.openbahn.navigator.data.TrackedJourneyWithJourney
import de.openbahn.navigator.data.UserPreferencesRepository
import de.openbahn.navigator.ui.util.parseJourneyDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.getKoin

class JourneyTrackingForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)
    private var trackingLoopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    private val notificationStrings by lazy { AndroidTrackingNotificationStrings(this) }
    private val notificationFormatter by lazy { TrackingNotificationFormatter(notificationStrings) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildForegroundNotification(content = null))
        trackingLoopJob?.cancel()
        trackingLoopJob = serviceScope.launch {
            runTrackingLoop()
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        trackingLoopJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }

    private suspend fun runTrackingLoop() {
        val repository = getKoin().get<TrackedJourneyRepository>()
        val delayCheck = getKoin().get<TrackedJourneyDelayCheckUseCase>()
        val userPreferences = getKoin().get<UserPreferencesRepository>()
        while (serviceScope.isActive) {
            val active = repository.getActiveWithJourneyForWorker()
            if (active.isEmpty()) break
            updateForegroundNotification(active)
            try {
                delayCheck.run()
            } catch (e: Exception) {
                OpenBahnDebugLog.w(TAG, "tracking cycle failed: ${e.message}")
            }
            // Reload after the delay check so the notification reflects freshly refreshed times.
            val refreshed = repository.getActiveWithJourneyForWorker()
            if (refreshed.isEmpty()) break
            updateForegroundNotification(refreshed)
            val departures = refreshed.mapNotNull { parseJourneyDateTime(it.entity.departureIso) }
            val nearInterval = userPreferences.nearDepartureCheckIntervalSeconds.first()
            val waitMs = TrackingRefreshPolicy.delayUntilNextCheckMillis(
                departureTimes = departures,
                nearDepartureIntervalSeconds = nearInterval,
            )
            delay(waitMs)
        }
    }

    private fun updateForegroundNotification(tracked: List<TrackedJourneyWithJourney>) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(notificationFormatter.format(tracked)))
    }

    private fun buildForegroundNotification(content: TrackingNotificationContent?): Notification {
        val title = content?.title ?: getString(R.string.tracking_foreground_title)
        val collapsedText = content?.lines?.firstOrNull()
            ?: getString(R.string.tracking_foreground_starting)
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, OpenBahnApplication.CHANNEL_JOURNEY_TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(collapsedText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp)
        if (content != null) {
            if (content.lines.size > 1) {
                val inbox = NotificationCompat.InboxStyle().setBigContentTitle(title)
                content.lines.forEach { inbox.addLine(it) }
                builder.setStyle(inbox)
            } else {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
            }
        }
        return builder.build()
    }

    private class AndroidTrackingNotificationStrings(
        private val context: Context,
    ) : TrackingNotificationStrings {
        override fun platformSegment(platform: String): String =
            context.getString(R.string.tracking_foreground_platform, platform)

        override fun transfers(count: Int): String =
            if (count == 0) {
                context.getString(R.string.share_direct)
            } else {
                context.getString(R.string.transfers_count, count)
            }

        override fun multiTitle(count: Int): String =
            context.getString(R.string.tracking_foreground_count_title, count)
    }

    companion object {
        private const val TAG = "JourneyTrackingFgService"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.applicationContext.startForegroundService(
                Intent(context.applicationContext, JourneyTrackingForegroundService::class.java),
            )
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, JourneyTrackingForegroundService::class.java),
            )
        }
    }
}
