package de.openbahn.api

import android.content.Context
import java.lang.ref.WeakReference

/**
 * Public entry point for Deutsche Bahn TLS setup.
 *
 * On Android, pass the [Application][android.app.Application] context so Bahn HTTP
 * can use Chromium Cronet (Akamai blocks stock Conscrypt ClientHellos).
 * On JVM unit/live tests, [install] without a context keeps the OpenJDK named-groups workaround.
 */
object BahnTls {
    @Volatile
    private var appContextRef: WeakReference<Context>? = null

    /** Named groups used by the JVM OpenJDK workaround (ignored by Android Conscrypt). */
    val preferredNamedGroups: List<String>
        get() = BahnOkHttp.PREFERRED_NAMED_GROUPS

    fun install(context: Context? = null) {
        if (context != null) {
            appContextRef = WeakReference(context.applicationContext)
        }
        BahnOkHttp.installTlsFingerprint()
    }

    internal fun appContextOrNull(): Context? = appContextRef?.get()
}
