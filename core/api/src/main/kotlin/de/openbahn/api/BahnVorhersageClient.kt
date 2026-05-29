package de.openbahn.api

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
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray

/**
 * Client for Bahn-Vorhersage delay/transfer predictions.
 * Uses the public API at bahnvorhersage.de (GPL-3.0, https://gitlab.com/bahnvorhersage).
 */
class BahnVorhersageClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: HttpClient = DbVendoClient.createDefaultClient(),
) {
    suspend fun rateJourney(journey: Journey): RatedJourney {
        return try {
            val body = buildRateRequest(journey)
            val response: RateJourneysResponse = httpClient.post("$baseUrl/rate-journeys/") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.body()
            RatedJourney(
                journey = journey,
                predictions = response.transferScores?.mapIndexed { index, score ->
                    TransferPrediction(
                        legIndex = index,
                        successProbability = score,
                        delayDistribution = response.predictions?.getOrNull(index),
                    )
                }.orEmpty(),
            )
        } catch (_: Exception) {
            RatedJourney(journey = journey, predictions = emptyList())
        }
    }

    private fun buildRateRequest(journey: Journey) = buildJsonObject {
        putJsonArray("number") { journey.legs.forEach { add(0) } }
        putJsonArray("line") { journey.legs.forEach { add(it.lineName ?: "") } }
        putJsonArray("category") {
            journey.legs.forEach { add(it.product?.vendoCode ?: "UNKNOWN") }
        }
        putJsonArray("operator") { journey.legs.forEach { add(it.operator ?: "DB") } }
        putJsonArray("delay_prognosed") {
            journey.legs.forEach { add(it.destination.delayMinutes ?: 0) }
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
