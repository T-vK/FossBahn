package de.openbahn.api

import com.google.net.cronet.okhttptransport.CronetInterceptor
import de.openbahn.api.debug.OpenBahnDebugLog
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.TlsVersion
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * OkHttp tuned for Deutsche Bahn's Akamai edge.
 *
 * Android: prefer Chromium [CronetInterceptor] — platform Conscrypt fingerprints are
 * OPS_BLOCKED, and neither `jdk.tls.namedGroups` nor `SSLParameters.setNamedGroups`
 * exist on Android Conscrypt (`applied=false` in production logs).
 *
 * JVM/desktop tests: advertise Chrome-like `supported_groups` via the OpenJDK property
 * and optional `setNamedGroups` (see public-transport/db-vendo-client#46).
 */
internal object BahnOkHttp {
    /** Classical group order that unblocks int.bahn.de from OpenJDK / curl on this network. */
    val PREFERRED_NAMED_GROUPS = listOf("x25519", "secp256r1", "secp384r1")

    private const val JDK_NAMED_GROUPS_PROP = "jdk.tls.namedGroups"

    private val installed = AtomicBoolean(false)
    private val socketGroupsLogged = AtomicBoolean(false)

    /**
     * Must run before the first [SSLContext] is created for Bahn hosts on the JVM.
     * Safe to call repeatedly. No-op for Android Conscrypt (property is ignored).
     */
    fun installTlsFingerprint() {
        if (!installed.compareAndSet(false, true)) return
        val value = PREFERRED_NAMED_GROUPS.joinToString(",")
        val previous = System.getProperty(JDK_NAMED_GROUPS_PROP)
        if (previous.isNullOrBlank()) {
            System.setProperty(JDK_NAMED_GROUPS_PROP, value)
            OpenBahnDebugLog.d("BahnTls", "set $JDK_NAMED_GROUPS_PROP=$value")
        } else {
            OpenBahnDebugLog.d(
                "BahnTls",
                "keep existing $JDK_NAMED_GROUPS_PROP=$previous (wanted $value)",
            )
        }
    }

    private val trustManager: X509TrustManager by lazy {
        installTlsFingerprint()
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private val baseSslSocketFactory: SSLSocketFactory by lazy {
        installTlsFingerprint()
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), null)
        }.socketFactory
    }

    private val connectionSpec: ConnectionSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
        .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
        .cipherSuites(
            CipherSuite.TLS_AES_128_GCM_SHA256,
            CipherSuite.TLS_AES_256_GCM_SHA384,
            CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
        )
        .build()

    fun createClient(): OkHttpClient {
        installTlsFingerprint()
        val builder = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)

        val context = BahnTls.appContextOrNull()
        if (context != null) {
            val cronet = BahnCronet.engineOrNull(context)
            if (cronet != null) {
                // Preserve Content-Type on the body first; Cronet interceptor must be last.
                builder.addInterceptor { chain ->
                    val req = chain.request()
                    OpenBahnDebugLog.d(
                        "BahnTls",
                        "outgoing ${req.method} ${req.url} bodyCt=${req.body?.contentType()} " +
                            "hdrCt=${req.header("Content-Type")} accept=${req.header("Accept")}",
                    )
                    chain.proceed(req)
                }
                builder.addInterceptor(PreserveContentTypeInterceptor())
                builder.addInterceptor(CronetInterceptor.newBuilder(cronet).build())
                OpenBahnDebugLog.d("BahnTls", "OkHttp using Cronet transport (Chromium TLS)")
                return builder.build()
            }
            // Do not wrap SSLSocket / force HTTP/1.1 — that makes Conscrypt look even less
            // like a normal Android app and stays OPS_BLOCKED.
            OpenBahnDebugLog.w(
                "BahnTls",
                "Cronet missing; using platform OkHttp/Conscrypt (likely OPS_BLOCKED on Bahn)",
            )
            return builder.build()
        }

        // JVM / unit & live-api tests: OpenJDK honors jdk.tls.namedGroups.
        return builder
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionSpecs(listOf(connectionSpec))
            .sslSocketFactory(NamedGroupsSslSocketFactory(baseSslSocketFactory), trustManager)
            .build()
    }

    private class NamedGroupsSslSocketFactory(
        private val delegate: SSLSocketFactory,
    ) : SSLSocketFactory() {
        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

        override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket =
            tune(delegate.createSocket(s, host, port, autoClose))

        override fun createSocket(host: String, port: Int): Socket =
            tune(delegate.createSocket(host, port))

        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
            tune(delegate.createSocket(host, port, localHost, localPort))

        override fun createSocket(host: InetAddress, port: Int): Socket =
            tune(delegate.createSocket(host, port))

        override fun createSocket(
            address: InetAddress,
            port: Int,
            localAddress: InetAddress,
            localPort: Int,
        ): Socket = tune(delegate.createSocket(address, port, localAddress, localPort))

        private fun tune(socket: Socket): Socket {
            val ssl = socket as? SSLSocket ?: return socket
            val applied = applyNamedGroups(ssl)
            if (socketGroupsLogged.compareAndSet(false, true)) {
                OpenBahnDebugLog.d(
                    "BahnTls",
                    "socket named groups applied=$applied groups=${PREFERRED_NAMED_GROUPS.joinToString()} http=1.1",
                )
            }
            return ssl
        }

        /**
         * Newer JDKs expose [SSLParameters.setNamedGroups].
         * Android Conscrypt does not — callers should use Cronet instead.
         */
        private fun applyNamedGroups(ssl: SSLSocket): Boolean {
            return try {
                val params: SSLParameters = ssl.sslParameters
                val method = SSLParameters::class.java.methods.firstOrNull {
                    it.name == "setNamedGroups" &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0].isArray
                } ?: return false
                method.invoke(params, PREFERRED_NAMED_GROUPS.toTypedArray())
                ssl.sslParameters = params
                true
            } catch (e: Throwable) {
                OpenBahnDebugLog.w("BahnTls", "socket named groups failed: ${e.message}")
                false
            }
        }
    }
}
