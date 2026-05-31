package de.openbahn.navigator.tracking

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.openbahn.api.debug.OpenBahnDebugLog
import java.util.concurrent.TimeUnit
import org.koin.java.KoinJavaComponent.getKoin

class DelayTrackingWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            getKoin().get<TrackedJourneyDelayCheckUseCase>().run()
            Result.success()
        } catch (e: Exception) {
            OpenBahnDebugLog.w(TAG, "doWork failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "DelayTrackingWorker"
        private const val WORK_NAME_PERIODIC = "delay_tracking"
        private const val WORK_NAME_ONCE = "delay_tracking_once"

        private fun networkConstraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DelayTrackingWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(networkConstraints())
                    .build(),
            )
        }

        fun runOnce(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONCE,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<DelayTrackingWorker>()
                    .setConstraints(networkConstraints())
                    .build(),
            )
        }
    }
}
