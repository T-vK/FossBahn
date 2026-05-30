package de.openbahn.navigator.ui.util

import android.net.Uri
import de.openbahn.model.Journey
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Opens bahn.de ticket purchase for a journey using the API ctxRecon token when available.
 */
fun journeyBookingUri(journey: Journey): Uri? {
    val token = journey.refreshToken?.takeIf { it.isNotBlank() } ?: return null
    val encoded = URLEncoder.encode(token, StandardCharsets.UTF_8.name())
    return Uri.parse("https://www.bahn.de/buchung/start?lang=de&country=DE&sbm=true&ctxRecon=$encoded")
}
