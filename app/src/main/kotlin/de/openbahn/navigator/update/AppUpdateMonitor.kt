package de.openbahn.navigator.update

import android.content.Context
import de.openbahn.api.debug.OpenBahnDebugLog
import de.openbahn.navigator.BuildConfig
import de.openbahn.navigator.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppUpdateMonitor(
    context: Context,
    private val userPreferences: UserPreferencesRepository,
    private val releaseClient: GitHubReleaseClient = GitHubReleaseClient(),
    private val installer: AppUpdateInstaller = AppUpdateInstaller(context),
) {
    private var lastHandledVersionCode: Int = 0
    private var downloadInProgress = false

    fun start(scope: CoroutineScope) {
        scope.launch {
            userPreferences.autoUpdateEnabled
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    if (!enabled) {
                        lastHandledVersionCode = 0
                        return@collectLatest
                    }
                    OpenBahnDebugLog.d("AppUpdate", "Auto-update polling started (every 5s)")
                    while (isActive) {
                        checkAndUpdate()
                        delay(CHECK_INTERVAL_MS)
                    }
                }
        }
    }

    private suspend fun checkAndUpdate() {
        if (downloadInProgress) return
        val latest = releaseClient.fetchLatestApk() ?: return
        val installedCode = BuildConfig.VERSION_CODE
        if (latest.versionCode <= installedCode) return
        if (latest.versionCode == lastHandledVersionCode) return

        OpenBahnDebugLog.d(
            "AppUpdate",
            "New version ${latest.versionName} (${latest.versionCode}) > installed $installedCode",
        )
        downloadInProgress = true
        try {
            val apk = installer.downloadApk(latest) ?: return
            lastHandledVersionCode = latest.versionCode
            installer.promptInstall(apk)
        } finally {
            downloadInProgress = false
        }
    }

    companion object {
        const val CHECK_INTERVAL_MS = 5_000L
    }
}
