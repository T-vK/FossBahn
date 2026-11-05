package de.openbahn.api

import android.content.Context
import de.openbahn.api.debug.OpenBahnDebugLog
import org.chromium.net.CronetEngine
import org.chromium.net.CronetProvider
import java.util.concurrent.atomic.AtomicReference

/**
 * Builds a Chromium [CronetEngine] for Bahn API calls.
 *
 * Android's platform Conscrypt ClientHello is classified as automation by Akamai
 * (HTTP 452 OPS_BLOCKED / HTML Access Denied). Real Chromium TLS (Cronet) is not.
 * Prefers an enabled platform provider, then the bundled Cronet shipped with the app.
 */
internal object BahnCronet {
    private val engineRef = AtomicReference<CronetEngine?>(null)

    fun engineOrNull(context: Context): CronetEngine? {
        engineRef.get()?.let { return it }
        synchronized(this) {
            engineRef.get()?.let { return it }
            val engine = createEngine(context.applicationContext) ?: return null
            engineRef.set(engine)
            return engine
        }
    }

    private fun createEngine(context: Context): CronetEngine? {
        val providers = CronetProvider.getAllProviders(context)
            .filter { it.isEnabled }
            .sortedByDescending { provider ->
                // Prefer bundled / native Chromium over the Java fallback stub.
                when {
                    provider.name.contains("App-Packaged-Cronet-Provider", ignoreCase = true) -> 3
                    provider.name.contains("Native", ignoreCase = true) -> 2
                    provider.name.contains("HttpEngine", ignoreCase = true) -> 2
                    provider.name.contains("Fallback", ignoreCase = true) -> 0
                    else -> 1
                }
            }
        for (provider in providers) {
            try {
                val engine = provider.createBuilder()
                    .enableHttp2(true)
                    .enableQuic(true)
                    .enableBrotli(true)
                    .build()
                OpenBahnDebugLog.d(
                    "BahnTls",
                    "Cronet engine ready provider=${provider.name} version=${provider.version}",
                )
                return engine
            } catch (e: Throwable) {
                OpenBahnDebugLog.w(
                    "BahnTls",
                    "Cronet provider ${provider.name} failed: ${e.message}",
                )
            }
        }
        OpenBahnDebugLog.w(
            "BahnTls",
            "No Cronet provider available (checked ${providers.size}); Bahn calls may be OPS_BLOCKED",
        )
        return null
    }
}
