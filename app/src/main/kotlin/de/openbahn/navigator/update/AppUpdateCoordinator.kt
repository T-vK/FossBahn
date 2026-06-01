package de.openbahn.navigator.update

import android.app.Activity
import android.content.Context
import de.openbahn.api.debug.OpenBahnDebugLog
import de.openbahn.navigator.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Holds a downloaded APK and exposes it to the UI. Install must run from a foreground [Activity]
 * so the package installer is interactive (not from [Context] with FLAG_ACTIVITY_NEW_TASK).
 */
class AppUpdateCoordinator(
    private val context: Context,
    private val releaseResolver: AppReleaseResolver = AppReleaseResolver(),
    private val installer: AppUpdateInstaller = AppUpdateInstaller(context),
    private val notifier: AppUpdateNotifier = AppUpdateNotifier(context),
) {
    data class PendingUpdate(
        val apkFile: File,
        val versionName: String,
        val versionCode: Int,
    )

    private val _pendingUpdate = MutableStateFlow<PendingUpdate?>(null)
    val pendingUpdate: StateFlow<PendingUpdate?> = _pendingUpdate.asStateFlow()

    private var lastDeferredVersionCode: Int = 0
    private var downloadInProgress = false

    suspend fun checkForUpdate() {
        if (downloadInProgress) return
        val latest = releaseResolver.fetchLatestApk(context.packageName) ?: return
        val installedCode = BuildConfig.VERSION_CODE
        if (latest.versionCode <= installedCode) {
            if (_pendingUpdate.value != null) clearPending()
            return
        }
        if (latest.versionCode == lastDeferredVersionCode) return
        if (_pendingUpdate.value?.versionCode == latest.versionCode) return

        OpenBahnDebugLog.d(
            TAG,
            "New version ${latest.versionName} (${latest.versionCode}) > installed $installedCode",
        )
        downloadInProgress = true
        try {
            val apk = installer.downloadApk(latest) ?: return
            val pending = PendingUpdate(apk, latest.versionName, latest.versionCode)
            _pendingUpdate.value = pending
            notifier.showUpdateReady(latest.versionName)
            OpenBahnDebugLog.d(TAG, "Update ready: ${latest.versionName} (${apk.length()} bytes)")
        } finally {
            downloadInProgress = false
        }
    }

    fun installFrom(activity: Activity): Boolean {
        val pending = _pendingUpdate.value ?: return false
        if (!installer.canInstallPackages()) {
            installer.openInstallPermissionSettings(activity)
            return false
        }
        val started = installer.promptInstall(activity, pending.apkFile)
        if (started) {
            lastDeferredVersionCode = pending.versionCode
            clearPending()
        }
        return started
    }

    /** User chose "Later" — do not prompt again for this version until a newer one appears. */
    fun deferUpdate() {
        _pendingUpdate.value?.let { lastDeferredVersionCode = it.versionCode }
        clearPending()
    }

    fun clearPending() {
        _pendingUpdate.value = null
    }

    fun resetForDisabled() {
        lastDeferredVersionCode = 0
        clearPending()
    }

    companion object {
        private const val TAG = "AppUpdate"
    }
}
