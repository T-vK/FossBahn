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
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * OkHttp tuned for Deutsche Bahn's Akamai edge.
 *
 * Akamai classifies TLS ClientHello fingerprints (supported_groups / cipher order).
 * Stock Conscrypt/OpenSSL defaults are frequently served HTML "Access Denied" for
 * `/orte` and `/fahrplan` while browsers and DB Navigator still work. Aligning
 * named groups and cipher suites with common Chrome/Android stacks unblocks many
 * of those clients (see public-transport/db-vendo-client#46 and BetterBahn#231).
 */
internal object BahnOkHttp {
    @Volatile
    private var namedGroupsLogged = false

    private val trustManager: X509TrustManager by lazy {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private val baseSslSocketFactory: SSLSocketFactory by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), null)
        }.socketFactory
    }

    /** Prefer classical X25519/P-256/P-384 ordering that has been observed to pass Akamai. */
    private val preferredNamedGroups = arrayOf(
        "x25519",
        "secp256r1",
        "secp384r1",
    )

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

    fun createClient(): OkHttpClient = OkHttpClient.Builder()
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
            try {
                val params: SSLParameters = ssl.sslParameters
                val method = SSLParameters::class.java.methods.firstOrNull {
                    it.name == "setNamedGroups" && it.parameterTypes.size == 1
                }
                if (method != null) {
                    method.invoke(params, preferredNamedGroups)
                    ssl.sslParameters = params
                    if (!namedGroupsLogged) {
                        namedGroupsLogged = true
                        OpenBahnDebugLog.d(
                            "BahnTls",
                            "TLS named groups=${preferredNamedGroups.joinToString()} http=1.1",
                        )
                    }
                }
            } catch (e: Throwable) {
                OpenBahnDebugLog.w("BahnTls", "named groups not applied: ${e.message}")
            }
            return ssl
        }
    }

}
