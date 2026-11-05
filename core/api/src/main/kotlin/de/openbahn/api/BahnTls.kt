package de.openbahn.api

/**
 * Public entry point to install the Deutsche Bahn Akamai TLS fingerprint
 * before any HTTP client is created. Prefer calling from [android.app.Application.onCreate].
 */
object BahnTls {
    /** Named groups that unblock bahn.de / services-bahn.de from blocked ClientHellos. */
    val preferredNamedGroups: List<String>
        get() = BahnOkHttp.PREFERRED_NAMED_GROUPS

    fun install() {
        BahnOkHttp.installTlsFingerprint()
    }
}
