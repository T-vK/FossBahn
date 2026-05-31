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
 * Transfer success and punctuality via Bahn-Vorhersage (optional) or local heuristics.
 *
 * The bahnvorhersage.de website does **not** expose a public API for third-party apps.
 * Set [baseUrl] to a self-hosted predictor (see https://gitlab.com/bahnvorhersage/bahnvorhersage)
 * or leave empty to use estimates only.
 */
class BahnVorhersageClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: HttpClient = DbVendoClient.createDefaultClient(),
) {
    suspend fun rateJourney(
        journey: Journey,
        options: JourneyRatingOptions = JourneyRatingOptions(),
    ): RatedJourney {
        if (baseUrl.isNotBlank()) {
            rateFromApi(journey, options)?.let { return it.enrichWithHeuristics(journey, options) }
        }
        return heuristicRating(journey, options)
    }

    private fun heuristicRating(journey: Journey, options: JourneyRatingOptions): RatedJourney {
        val transfers = if (BahnVorhersageRequestBuilder.hasTransferEvents(journey)) {
            BahnVorhersageHeuristic.estimate(journey, options.minTransferMinutes)
        } else {
            emptyList()
        }
        return RatedJourney(
            journey = journey,
            predictions = transfers,
            punctualityProbability = BahnVorhersageHeuristic.estimatePunctuality(
                journey,
                options.punctualityToleranceMinutes,
            ),
            punctualityIsEstimate = true,
            minTransferMinutesUsed = options.minTransferMinutes,
            punctualityToleranceMinutes = options.punctualityToleranceMinutes,
        )
    }

    private fun RatedJourney.enrichWithHeuristics(journey: Journey, options: JourneyRatingOptions): RatedJourney {
        val needsPunctuality = punctualityProbability == null
        val needsTransfers = predictions.isEmpty() && BahnVorhersageRequestBuilder.hasTransferEvents(journey)
        if (!needsPunctuality && !needsTransfers) {
            return withMetadata(options)
        }
        val fallback = heuristicRating(journey, options)
        return copy(
            predictions = predictions.ifEmpty { fallback.predictions },
            punctualityProbability = punctualityProbability ?: fallback.punctualityProbability,
            punctualityIsEstimate = punctualityIsEstimate || fallback.punctualityIsEstimate,
        ).withMetadata(options)
    }

    private fun RatedJourney.withMetadata(options: JourneyRatingOptions): RatedJourney = copy(
        minTransferMinutesUsed = options.minTransferMinutes,
        punctualityToleranceMinutes = options.punctualityToleranceMinutes,
    )

    private suspend fun rateFromApi(journey: Journey, options: JourneyRatingOptions): RatedJourney? {
        if (!BahnVorhersageRequestBuilder.hasTransferEvents(journey)) return null
        return try {
            val body = BahnVorhersageRequestBuilder.build(journey, options.minTransferMinutes)
            if (body.isEmpty()) return null
            val response: RateJourneysResponse = httpClient.post("$baseUrl/rate-journeys/") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.body()
            val scores = response.transferScores.orEmpty()
            if (scores.isEmpty() || scores.all { it == null }) return null
            val offset = response.offset ?: 0
            RatedJourney(
                journey = journey,
                predictions = scores.mapIndexed { index, score ->
                    val distribution = response.predictions?.getOrNull(index)
                    val transferMins = transferMinutesAt(journey, index)
                    val adjustedScore = when {
                        distribution != null && options.minTransferMinutes != null && transferMins != null ->
                            PredictionScoring.transferSuccessFromDistribution(
                                distribution = distribution,
                                offset = offset,
                                prognosedTransferMinutes = transferMins,
                                minTransferMinutes = options.minTransferMinutes,
                            )
                        else -> score
                    }
                    TransferPrediction(
                        legIndex = index,
                        successProbability = adjustedScore,
                        delayDistribution = distribution,
                        isEstimate = false,
                    )
                },
                punctualityProbability = punctualityFromApi(journey, response, offset, options),
                punctualityIsEstimate = false,
            )
        } catch (e: Exception) {
            OpenBahnDebugLog.w("BahnVorhersage", "rateJourney API failed for ${journey.id}: ${e.message}")
            null
        }
    }

    private fun transferMinutesAt(journey: Journey, legIndex: Int): Long? {
        if (legIndex < 0 || legIndex >= journey.legs.lastIndex) return null
        return BahnVorhersageRequestBuilder.transferMinutesBetween(
            journey.legs[legIndex].destination,
            journey.legs[legIndex + 1].origin,
        )
    }

    private fun punctualityFromApi(
        journey: Journey,
        response: RateJourneysResponse,
        offset: Int,
        options: JourneyRatingOptions,
    ): Double? {
        val distributions = response.predictions.orEmpty()
        if (distributions.isEmpty()) return null
        val arrivalDistribution = distributions.lastOrNull() ?: return null
        return PredictionScoring.probabilityDelayAtMost(
            arrivalDistribution,
            offset,
            options.punctualityToleranceMinutes,
        )
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
