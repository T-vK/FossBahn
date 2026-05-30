package de.openbahn.api.debug

import android.util.Log

/**
 * Debug-only logging for journey search diagnostics.
 * Enabled from [de.openbahn.navigator.OpenBahnApplication] when `BuildConfig.DEBUG` is true.
 *
 * Filter logcat: adb logcat -s OpenBahn/DbVendo OpenBahn/JourneyParser OpenBahn/Search
 */
object OpenBahnDebugLog {
    private const val ROOT = "OpenBahn"

    /** Set to true in debug builds to emit verbose search/API logs. */
    @JvmField
    var isEnabled: Boolean = false

    fun d(tag: String, message: String) {
        if (!isEnabled) return
        Log.d("$ROOT/$tag", message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        if (throwable != null) {
            Log.w("$ROOT/$tag", message, throwable)
        } else {
            Log.w("$ROOT/$tag", message)
        }
    }
}
