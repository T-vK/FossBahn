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
    private val context: Context,
    private val userPreferences: UserPreferencesRepository,
    private val releaseResolver: AppReleaseResolver = AppReleaseResolver(),
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
                    OpenBahnDebugLog.d(
                        "AppUpdate",
                        "Auto-update polling started (F-Droid repo + GitHub, every 5s)",
                    )
                    while (isActive) {
                        checkAndUpdate()
                        delay(CHECK_INTERVAL_MS)
                    }
                }
        }
    }

    private suspend fun checkAndUpdate() {
        if (downloadInProgress) return
        val latest = releaseResolver.fetchLatestApk(context.packageName) ?: return
        val installedCode = BuildConfig.VERSION_CODE
        if (latest.versionCode <= installedCode) return
        if (latest.versionCode == lastHandledVersionCode) return

        OpenBahnDebugLog.d(
            "AppUpdate",
            "New version ${latest.versionName} (${latest.versionCode}) > installed $installedCode " +
                "from ${latest.downloadUrl}",
        )
        downloadInProgress = true
        try {
            val apk = installer.downloadApk(latest) ?: return
            if (installer.promptInstall(apk)) {
                lastHandledVersionCode = latest.versionCode
            } else {
                OpenBahnDebugLog.w("AppUpdate", "Install prompt not shown; will retry on next poll")
            }
        } finally {
            downloadInProgress = false
        }
    }

    companion object {
        const val CHECK_INTERVAL_MS = 5_000L
    }
}
