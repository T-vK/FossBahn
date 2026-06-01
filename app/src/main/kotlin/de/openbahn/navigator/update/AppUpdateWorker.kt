package de.openbahn.navigator.update

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

/** Checks for APK updates while the app is in the background (WorkManager, not UI coroutines). */
class AppUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        getKoin().get<AppUpdateCoordinator>().checkForUpdate()
        Result.success()
    } catch (e: Exception) {
        OpenBahnDebugLog.w(TAG, "background update check failed: ${e.message}")
        Result.retry()
    }

    companion object {
        private const val TAG = "AppUpdate"
        private const val WORK_PERIODIC = "app_update_periodic"
        private const val WORK_ONCE = "app_update_once"

        private fun networkConstraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AppUpdateWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(networkConstraints())
                    .build(),
            )
        }

        fun runOnce(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_ONCE,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AppUpdateWorker>()
                    .setConstraints(networkConstraints())
                    .build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(WORK_ONCE)
        }
    }
}
