package de.openbahn.api

import de.openbahn.api.debug.OpenBahnDebugLog
import de.openbahn.model.Journey
import de.openbahn.model.RatedJourney
import de.openbahn.model.TransferPrediction
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Transfer success probabilities via Bahn-Vorhersage (optional) or a local heuristic.
 *
 * The bahnvorhersage.de website does **not** expose a public API for third-party apps.
 * Set [baseUrl] to a self-hosted predictor (see https://gitlab.com/bahnvorhersage/bahnvorhersage)
 * or leave empty to use transfer-time estimates only.
 */
class BahnVorhersageClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: HttpClient = DbVendoClient.createDefaultClient(),
) {
    suspend fun rateJourney(journey: Journey): RatedJourney {
        if (!BahnVorhersageRequestBuilder.hasTransferEvents(journey)) {
            return RatedJourney(journey = journey, predictions = emptyList())
        }
        if (baseUrl.isNotBlank()) {
            rateFromApi(journey)?.let { return it }
        }
        return RatedJourney(
            journey = journey,
            predictions = BahnVorhersageHeuristic.estimate(journey),
        )
    }

    private suspend fun rateFromApi(journey: Journey): RatedJourney? {
        return try {
            val body = BahnVorhersageRequestBuilder.build(journey)
            if (body.isEmpty()) return null
            val response: RateJourneysResponse = httpClient.post("$baseUrl/rate-journeys/") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.body()
            val scores = response.transferScores.orEmpty()
            if (scores.isEmpty() || scores.all { it == null }) return null
            RatedJourney(
                journey = journey,
                predictions = scores.mapIndexed { index, score ->
                    TransferPrediction(
                        legIndex = index,
                        successProbability = score,
                        delayDistribution = response.predictions?.getOrNull(index),
                        isEstimate = false,
                    )
                },
            )
        } catch (e: Exception) {
            OpenBahnDebugLog.w("BahnVorhersage", "rateJourney API failed for ${journey.id}: ${e.message}")
            null
        }
    }

    fun close() = httpClient.close()

    @Serializable
    private data class RateJourneysResponse(
        val predictions: List<List<Double>>? = null,
        @SerialName("transfer_scores") val transferScores: List<Double?>? = null,
        val offset: Int? = null,
    )

    companion object {
        /** No public API — use heuristic unless a self-hosted predictor URL is configured. */
        const val DEFAULT_BASE_URL = ""
    }
}
