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
 * Client for Bahn-Vorhersage delay/transfer predictions.
 * Uses the public API at bahnvorhersage.de (GPL-3.0, https://gitlab.com/bahnvorhersage).
 */
class BahnVorhersageClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: HttpClient = DbVendoClient.createDefaultClient(),
) {
    suspend fun rateJourney(journey: Journey): RatedJourney {
        if (!BahnVorhersageRequestBuilder.hasTransferEvents(journey)) {
            return RatedJourney(journey = journey, predictions = emptyList())
        }
        return try {
            val body = BahnVorhersageRequestBuilder.build(journey)
            if (body.isEmpty()) {
                OpenBahnDebugLog.w("BahnVorhersage", "empty rate request for journey ${journey.id}")
                return RatedJourney(journey = journey, predictions = emptyList())
            }
            val response: RateJourneysResponse = httpClient.post("$baseUrl/rate-journeys/") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.body()
            val scores = response.transferScores.orEmpty()
            RatedJourney(
                journey = journey,
                predictions = scores.mapIndexed { index, score ->
                    TransferPrediction(
                        legIndex = index,
                        successProbability = score,
                        delayDistribution = response.predictions?.getOrNull(index),
                    )
                },
            )
        } catch (e: Exception) {
            OpenBahnDebugLog.w("BahnVorhersage", "rateJourney failed for ${journey.id}: ${e.message}")
            RatedJourney(journey = journey, predictions = emptyList())
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
        const val DEFAULT_BASE_URL = "https://bahnvorhersage.de/api"
    }
}
