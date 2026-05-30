package de.openbahn.navigator.tracking

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.openbahn.api.DbVendoClient
import de.openbahn.navigator.OpenBahnApplication
import de.openbahn.navigator.R
import de.openbahn.navigator.data.OpenBahnDatabase
import de.openbahn.navigator.data.TrackedJourneyRepository
import java.util.concurrent.TimeUnit

class DelayTrackingWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = androidx.room.Room.databaseBuilder(
            applicationContext,
            de.openbahn.navigator.data.OpenBahnDatabase::class.java,
            "openbahn.db",
        ).build()
        val repo = TrackedJourneyRepository(database.trackedJourneyDao())
        repo.pruneArrived()
        val client = DbVendoClient()
        val active = repo.getActiveForWorker()

        active.forEach { tracked ->
            val token = tracked.refreshToken ?: return@forEach
            val refreshed = client.refreshJourney(token) ?: return@forEach
            val maxDelay = refreshed.legs.maxOfOrNull { leg ->
                maxOf(leg.origin.delayMinutes ?: 0, leg.destination.delayMinutes ?: 0)
            } ?: 0
            if (maxDelay >= tracked.notifyOnDelayMinutes) {
                notifyDelay(tracked.fromName, tracked.toName, maxDelay)
            }
        }
        client.close()
        return Result.success()
    }

    private fun notifyDelay(from: String, to: String, delayMinutes: Int) {
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
