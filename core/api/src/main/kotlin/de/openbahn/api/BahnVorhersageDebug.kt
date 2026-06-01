package de.openbahn.api

import de.openbahn.api.debug.OpenBahnDebugLog
import de.openbahn.model.Journey
import de.openbahn.model.RatedJourney
import de.openbahn.model.StopEvent

/** Structured diagnostics for Bahn-Vorhersage rating (in-app debug log + logcat when enabled). */
internal object BahnVorhersageDebug {
    private const val TAG = "BahnVorhersage"

    fun logRateBatchStart(
        baseUrl: String,
        apiMode: String,
        journeys: List<Journey>,
        options: JourneyRatingOptions,
        tripRoutes: Map<String, List<StopEvent>>,
    ) {
        val railLegs = journeys.sumOf { j -> j.legs.count { !it.isWalking } }
        val withTripId = journeys.sumOf { j -> j.legs.count { !it.isWalking && !it.tripId.isNullOrBlank() } }
        OpenBahnDebugLog.d(
            TAG,
            buildString {
                appendLine("rateJourneys start")
                appendLine("  apiMode=$apiMode")
                appendLine("  baseUrl=${baseUrl.ifBlank { "(empty → heuristic only)" }}")
                appendLine("  journeys=${journeys.size} railLegs=$railLegs legsWithTripId=$withTripId")
                appendLine("  tripRoutes=${tripRoutes.size} stopCounts=${tripRoutes.values.map { it.size }.distinct().sorted()}")
                appendLine("  punctualityToleranceMin=${options.punctualityToleranceMinutes}")
                appendLine("  minTransferMin=${options.minTransferMinutes}")
                if (journeys.isNotEmpty()) {
                    append("  journeyIds=")
                    append(journeys.take(4).joinToString { it.id.take(24) })
                    if (journeys.size > 4) append("…")
                }
            },
        )
    }

    fun logHttpRequest(url: String, requestBytes: Int, journeyCount: Int, tripCount: Int) {
        OpenBahnDebugLog.d(
            TAG,
            "POST $url (request≈${requestBytes}B, journeys=$journeyCount, trips=$tripCount)",
        )
    }

    fun logHttpResponse(status: Int, responseBytes: Int, preview: String) {
        val level = if (status in 200..299) "d" else "w"
        val msg = "HTTP $status (${responseBytes}B) preview=${preview.take(600)}"
        if (level == "d") OpenBahnDebugLog.d(TAG, msg) else OpenBahnDebugLog.w(TAG, msg)
    }

    fun logParseFailure(reason: String, responsePreview: String) {
        OpenBahnDebugLog.w(
            TAG,
            "parse failed: $reason\nresponse=${responsePreview.take(1200)}",
        )
    }

    fun logException(phase: String, e: Throwable) {
        OpenBahnDebugLog.w(TAG, "$phase failed: ${e.message}", e)
    }

    fun logHeuristicFallback(reason: String, journeyCount: Int) {
        OpenBahnDebugLog.w(
            TAG,
            "using heuristic estimates for $journeyCount journey(s): $reason",
        )
    }

    fun logRatedSummary(rated: List<RatedJourney>) {
        rated.forEach { r ->
            val mlStops = r.stopTimeliness.count { !it.isEstimate }
            OpenBahnDebugLog.d(
                TAG,
                "rated ${r.journey.id.take(32)}: estimate=${r.punctualityIsEstimate} " +
                    "mlStops=$mlStops/${r.stopTimeliness.size} transfers=${r.predictions.size} " +
                    "punctuality=${r.punctualityProbability?.let { "%.3f".format(it) } ?: "—"}",
            )
        }
        if (rated.all { it.punctualityIsEstimate }) {
            OpenBahnDebugLog.w(
                TAG,
                "all ${rated.size} rated journey(s) are heuristic-only (API unreachable or no ML fields)",
            )
        }
    }
}
