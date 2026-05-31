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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildForegroundNotification(trackedCount = null))
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
            val active = repository.getActiveForWorker()
            if (active.isEmpty()) break
            updateForegroundNotification(active.size)
            try {
                delayCheck.run()
            } catch (e: Exception) {
                OpenBahnDebugLog.w(TAG, "tracking cycle failed: ${e.message}")
            }
            val departures = active.mapNotNull { parseJourneyDateTime(it.departureIso) }
            val nearInterval = userPreferences.nearDepartureCheckIntervalSeconds.first()
            val waitMs = TrackingRefreshPolicy.delayUntilNextCheckMillis(
                departureTimes = departures,
                nearDepartureIntervalSeconds = nearInterval,
            )
            delay(waitMs)
        }
    }

    private fun updateForegroundNotification(trackedCount: Int) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(trackedCount))
    }

    private fun buildForegroundNotification(trackedCount: Int?): Notification {
        val count = trackedCount ?: 0
        val title = getString(R.string.tracking_foreground_title)
        val text = if (count > 0) {
            getString(R.string.tracking_foreground_body, count)
        } else {
            getString(R.string.tracking_foreground_starting)
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, OpenBahnApplication.CHANNEL_JOURNEY_TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp)
            .build()
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
