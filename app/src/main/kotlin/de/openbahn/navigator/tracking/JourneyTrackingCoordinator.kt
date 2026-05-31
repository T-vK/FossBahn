package de.openbahn.navigator.tracking

import android.content.Context
import de.openbahn.navigator.data.TrackedJourneyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JourneyTrackingCoordinator(
    private val context: Context,
    private val repository: Lazy<TrackedJourneyRepository>,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun onActiveJourneysChanged() {
        scope.launch { syncBackgroundMonitoring() }
    }

    fun restoreOnLaunch() {
        scope.launch { syncBackgroundMonitoring() }
    }

    private suspend fun syncBackgroundMonitoring() {
        repository.value.pruneArrivedInternal()
        val active = repository.value.getActiveForWorker()
        if (active.isEmpty()) {
            JourneyTrackingForegroundService.stop(appContext)
            return
        }
        DelayTrackingWorker.schedule(appContext)
        DelayTrackingWorker.runOnce(appContext)
        JourneyTrackingForegroundService.start(appContext)
    }
}
