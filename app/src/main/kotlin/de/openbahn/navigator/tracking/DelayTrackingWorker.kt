package de.openbahn.navigator.tracking

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.openbahn.api.debug.OpenBahnDebugLog
import de.openbahn.navigator.OpenBahnApplication
import de.openbahn.navigator.R
import de.openbahn.navigator.data.TrackedJourneyRepository
import de.openbahn.navigator.data.UserPreferencesRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import org.koin.java.KoinJavaComponent.getKoin

class DelayTrackingWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = getKoin().get<TrackedJourneyRepository>()
            val refreshUseCase = getKoin().get<TrackedJourneyRefreshUseCase>()
            val incrementMinutes = getKoin().get<UserPreferencesRepository>()
                .delayNotificationIncrementMinutes
                .first()

            repo.pruneArrived()
            val active = repo.getActiveForWorker()
            if (active.isEmpty()) return Result.success()

            active.forEach { tracked ->
                val token = tracked.refreshToken?.takeIf { it.isNotBlank() }
                if (token == null) {
                    OpenBahnDebugLog.w(TAG, "skip ${tracked.id}: no refresh token")
                    return@forEach
                }
                val delayMinutes = refreshUseCase.refreshAndCheckDelayNotification(
                    entityId = tracked.id,
                    refreshToken = token,
                    notificationIncrementMinutes = incrementMinutes,
                ) ?: return@forEach
                notifyDelay(tracked.fromName, tracked.toName, delayMinutes)
            }
            Result.success()
        } catch (e: Exception) {
            OpenBahnDebugLog.w(TAG, "doWork failed: ${e.message}")
            Result.retry()
        }
    }

    private fun notifyDelay(from: String, to: String, delayMinutes: Int) {
        if (!canPostNotifications()) return
        val notification = NotificationCompat.Builder(
            applicationContext,
            OpenBahnApplication.CHANNEL_DELAY_ALERTS,
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.delay_notification_title))
            .setContentText(
                applicationContext.getString(
                    R.string.delay_notification_body,
                    from,
                    to,
                    delayMinutes,
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(from.hashCode() + to.hashCode(), notification)
    }

    private fun canPostNotifications(): Boolean =
        ContextCompat.checkSelfPermission(
            applicationContext,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU

    companion object {
        private const val TAG = "DelayTrackingWorker"
        private const val WORK_NAME_PERIODIC = "delay_tracking"
        private const val WORK_NAME_ONCE = "delay_tracking_once"

        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DelayTrackingWorker>(15, TimeUnit.MINUTES).build(),
            )
            runOnce(context)
        }

        /** Runs a refresh soon (e.g. after the user starts tracking). */
        fun runOnce(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONCE,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<DelayTrackingWorker>().build(),
            )
        }
    }
}
