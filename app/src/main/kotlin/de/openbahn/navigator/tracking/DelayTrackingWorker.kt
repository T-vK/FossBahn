package de.openbahn.navigator.tracking

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.openbahn.navigator.OpenBahnApplication
import de.openbahn.navigator.R
import de.openbahn.navigator.data.OpenBahnDatabase
import de.openbahn.navigator.data.TrackedJourneyRepository
import de.openbahn.navigator.data.UserPreferencesRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.getKoin

class DelayTrackingWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = androidx.room.Room.databaseBuilder(
            applicationContext,
            OpenBahnDatabase::class.java,
            "openbahn.db",
        ).build()
        val repo = TrackedJourneyRepository(database.trackedJourneyDao())
        repo.pruneArrived()
        val client = de.openbahn.api.DbVendoClient()
        val refreshUseCase = TrackedJourneyRefreshUseCase(client, repo)
        val active = repo.getActiveForWorker()
        val incrementMinutes = runBlocking {
            getKoin().get<UserPreferencesRepository>().delayNotificationIncrementMinutes.first()
        }

        active.forEach { tracked ->
            val token = tracked.refreshToken?.takeIf { it.isNotBlank() } ?: return@forEach
            val delayMinutes = refreshUseCase.refreshAndCheckDelayNotification(
                entityId = tracked.id,
                refreshToken = token,
                notificationIncrementMinutes = incrementMinutes,
            ) ?: return@forEach
            notifyDelay(tracked.fromName, tracked.toName, delayMinutes)
        }
        client.close()
        return Result.success()
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
        private const val WORK_NAME = "delay_tracking"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DelayTrackingWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
