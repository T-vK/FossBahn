package de.openbahn.navigator.update

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.openbahn.api.debug.OpenBahnDebugLog
import de.openbahn.navigator.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Polls for updates while the app is in the foreground, and schedules [AppUpdateWorker]
 * for background checks when auto-update is enabled.
 */
class AppUpdateMonitor(
    private val context: Context,
    private val userPreferences: UserPreferencesRepository,
    private val coordinator: AppUpdateCoordinator,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            userPreferences.autoUpdateEnabled
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    if (!enabled) {
                        AppUpdateWorker.cancel(context)
                        coordinator.resetForDisabled()
                        AppUpdateNotifier(context).cancel()
                        return@collectLatest
                    }
                    AppUpdateWorker.schedule(context)
                    AppUpdateWorker.runOnce(context)
                    OpenBahnDebugLog.d(
                        TAG,
                        "Auto-update enabled (foreground poll every ${CHECK_INTERVAL_MS / 1000}s + WorkManager every 15 min)",
                    )
                    ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (isActive) {
                            coordinator.checkForUpdate()
                            delay(CHECK_INTERVAL_MS)
                        }
                    }
                }
        }
    }

    companion object {
        private const val TAG = "AppUpdate"
        const val CHECK_INTERVAL_MS = 5_000L
    }
}
