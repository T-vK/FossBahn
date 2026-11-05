package de.openbahn.api

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
 * Akamai classifies TLS ClientHello fingerprints (especially `supported_groups`).
 * Stock JVM/Android defaults often get `OPS_BLOCKED` / HTML Access Denied, while
 * browsers and DB Navigator work. Advertising the classical Chrome-like group
 * order `x25519,secp256r1,secp384r1` unblocks the public vendo endpoints
 * (reproduced locally with curl `--curves` / `jdk.tls.namedGroups`; see
 * public-transport/db-vendo-client#46 and BetterBahn#231).
 */
internal object BahnOkHttp {
    /** Same order that unblocks int.bahn.de / app.services-bahn.de from this network. */
    val PREFERRED_NAMED_GROUPS = listOf("x25519", "secp256r1", "secp384r1")

    private const val JDK_NAMED_GROUPS_PROP = "jdk.tls.namedGroups"

    private val installed = AtomicBoolean(false)
    private val socketGroupsLogged = AtomicBoolean(false)

    /**
     * Must run before the first [SSLContext] is created for Bahn hosts.
     * Safe to call repeatedly. Sets the JVM named-groups property (honored by
     * OpenJDK / many Android runtimes) and records the fingerprint in logs.
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
        return OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionSpecs(listOf(connectionSpec))
            .sslSocketFactory(NamedGroupsSslSocketFactory(baseSslSocketFactory), trustManager)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
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
         * Android 14+ / newer JDKs expose [SSLParameters.setNamedGroups].
         * Older JVMs rely on [jdk.tls.namedGroups] set in [installTlsFingerprint].
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
