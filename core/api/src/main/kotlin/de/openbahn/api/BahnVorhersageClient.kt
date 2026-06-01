package de.openbahn.api

import de.openbahn.api.debug.OpenBahnDebugLog
import de.openbahn.model.Journey
import de.openbahn.model.RatedJourney
import de.openbahn.model.StopEvent
import de.openbahn.model.StopTimelinessPrediction
import de.openbahn.model.TransferPrediction
import de.openbahn.model.tripRouteStops
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Transfer success and punctuality via Bahn-Vorhersage.
 *
 * Default: [DEFAULT_MOBILE_BASE_URL] (`POST …/journeys`) — same public rating API as bahnvorhersage.de.
 * Override with `bahnVorhersageApiUrl` in `gradle.properties` for a self-hosted `/rate-journeys/` predictor.
 */
class BahnVorhersageClient(
    private val baseUrl: String = DEFAULT_MOBILE_BASE_URL,
    private val httpClient: HttpClient = DbVendoClient.createDefaultClient(),
) {
    suspend fun rateJourney(
        journey: Journey,
        options: JourneyRatingOptions = JourneyRatingOptions(),
        tripRoutes: Map<String, List<StopEvent>> = emptyMap(),
    ): RatedJourney = rateJourneys(listOf(journey), options, tripRoutes).first()

    suspend fun rateJourneys(
        journeys: List<Journey>,
        options: JourneyRatingOptions = JourneyRatingOptions(),
        tripRoutes: Map<String, List<StopEvent>> = emptyMap(),
    ): List<RatedJourney> {
        if (journeys.isEmpty()) return emptyList()
        if (baseUrl.isBlank()) {
            return journeys.map { heuristicRating(it, options) }
        }
        if (usesMobileV2Api()) {
            rateFromMobileV2(journeys, options, tripRoutes)?.let { return it }
        } else {
            return journeys.map { journey ->
                rateFromPredictorApi(journey, options)?.enrichWithHeuristics(journey, options)
                    ?: heuristicRating(journey, options)
            }
        }
        return journeys.map { heuristicRating(it, options) }
    }

    private fun usesMobileV2Api(): Boolean =
        baseUrl.contains("/mobile/v2", ignoreCase = true)

    private suspend fun rateFromMobileV2(
        journeys: List<Journey>,
        options: JourneyRatingOptions,
        tripRoutes: Map<String, List<StopEvent>>,
    ): List<RatedJourney>? {
        val trips = buildTripPayload(journeys, tripRoutes)
        return try {
            val body = BahnVorhersageFptfMapper.buildRateRequest(journeys, trips)
            val url = baseUrl.trimEnd('/') + "/journeys"
            val responseText: String = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.bodyAsText()
            BahnVorhersageFptfMapper.parseRatedJourneys(responseText, journeys, options)
        } catch (e: Exception) {
            OpenBahnDebugLog.w("BahnVorhersage", "mobile v2 rating failed: ${e.message}")
            null
        }
    }

    private fun buildTripPayload(
        journeys: List<Journey>,
        tripRoutes: Map<String, List<StopEvent>>,
    ): Map<String, List<StopEvent>> {
        val result = mutableMapOf<String, List<StopEvent>>()
        journeys.forEach { journey ->
            journey.legs.forEachIndexed { legIndex, leg ->
                if (leg.isWalking) return@forEachIndexed
                val tripId = leg.tripId?.trim().orEmpty().ifEmpty {
                    syntheticTripId(journey.id, legIndex)
                }
                if (tripId in result) return@forEachIndexed
                val route = tripRoutes[leg.tripId?.trim().orEmpty()]
                    ?.takeIf { it.size >= 2 }
                    ?: leg.tripRouteStops().takeIf { it.size >= 2 }
                    ?: listOf(leg.origin, leg.destination)
                if (route.size >= 2) {
                    result[tripId] = route
                }
            }
        }
        return result
    }

    private fun heuristicRating(journey: Journey, options: JourneyRatingOptions): RatedJourney {
        val transfers = if (BahnVorhersageRequestBuilder.hasTransferEvents(journey)) {
            BahnVorhersageHeuristic.estimate(journey, options.minTransferMinutes)
        } else {
            emptyList()
        }
        val stopTimeliness = BahnVorhersageHeuristic.buildStopTimeliness(
            journey,
            options.punctualityToleranceMinutes,
            options.minTransferMinutes,
        )
        return RatedJourney(
            journey = journey,
            predictions = transfers,
            stopTimeliness = stopTimeliness,
            punctualityProbability = stopTimeliness.lastOrNull {
                val lastRail = journey.legs.indexOfLast { !it.isWalking }
                it.legIndex == lastRail && it.intermediateIndex == null && it.isArrival
            }?.probability,
            punctualityIsEstimate = true,
            minTransferMinutesUsed = options.minTransferMinutes,
            punctualityToleranceMinutes = options.punctualityToleranceMinutes,
        )
    }

    private fun RatedJourney.enrichWithHeuristics(journey: Journey, options: JourneyRatingOptions): RatedJourney {
        val needsPunctuality = punctualityProbability == null
        val needsTransfers = predictions.isEmpty() && BahnVorhersageRequestBuilder.hasTransferEvents(journey)
        val needsStops = stopTimeliness.isEmpty()
        if (!needsPunctuality && !needsTransfers && !needsStops) {
            return withMetadata(options)
        }
        val fallback = heuristicRating(journey, options)
        return copy(
            predictions = predictions.ifEmpty { fallback.predictions },
            stopTimeliness = stopTimeliness.ifEmpty { fallback.stopTimeliness },
            punctualityProbability = punctualityProbability ?: fallback.punctualityProbability,
            punctualityIsEstimate = punctualityIsEstimate || fallback.punctualityIsEstimate,
        ).withMetadata(options)
    }

    private fun RatedJourney.withMetadata(options: JourneyRatingOptions): RatedJourney = copy(
        minTransferMinutesUsed = options.minTransferMinutes,
        punctualityToleranceMinutes = options.punctualityToleranceMinutes,
    )

    /** Self-hosted columnar `/rate-journeys/` predictor (legacy URL). */
    private suspend fun rateFromPredictorApi(
        journey: Journey,
        options: JourneyRatingOptions,
    ): RatedJourney? {
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
            val transferPredictions = scores.mapIndexed { index, score ->
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
            }
            val punctuality = punctualityFromApi(journey, response, offset, options)
            val stopTimeliness = stopTimelinessFromApi(
                journey = journey,
                response = response,
                offset = offset,
                options = options,
                transferPredictions = transferPredictions,
            )
            RatedJourney(
                journey = journey,
                predictions = transferPredictions,
                stopTimeliness = stopTimeliness,
                punctualityProbability = punctuality,
                punctualityIsEstimate = false,
            )
        } catch (e: Exception) {
            OpenBahnDebugLog.w("BahnVorhersage", "rate-journeys API failed for ${journey.id}: ${e.message}")
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
        return PredictionScoring.probabilityExactlyOnTime(arrivalDistribution, offset)
    }

    private fun stopTimelinessFromApi(
        journey: Journey,
        response: RateJourneysResponse,
        offset: Int,
        options: JourneyRatingOptions,
        transferPredictions: List<TransferPrediction>,
    ): List<StopTimelinessPrediction> {
        val base = BahnVorhersageHeuristic.buildStopTimeliness(
            journey,
            options.punctualityToleranceMinutes,
            options.minTransferMinutes,
        ).toMutableList()
        val distributions = response.predictions.orEmpty()
        if (distributions.isEmpty()) {
            return base.map { it.copy(isEstimate = false) }
        }
        transferPredictions.forEachIndexed { index, prediction ->
            val distribution = prediction.delayDistribution ?: distributions.getOrNull(index) ?: return@forEachIndexed
            val leg = journey.legs.getOrNull(prediction.legIndex) ?: return@forEachIndexed
            val nextLeg = journey.legs.getOrNull(prediction.legIndex + 1) ?: return@forEachIndexed
            if (leg.isWalking || nextLeg.isWalking) return@forEachIndexed

            val arrivalProb = PredictionScoring.probabilityExactlyOnTime(distribution, offset)
            replaceStopProbability(base, prediction.legIndex, intermediateIndex = null, isArrival = true, arrivalProb, isEstimate = false)

            val transferMins = transferMinutesAt(journey, prediction.legIndex)
            val departureProb = when {
                transferMins != null && options.minTransferMinutes != null ->
                    prediction.successProbability
                        ?: PredictionScoring.transferSuccessFromDistribution(
                            distribution,
                            offset,
                            transferMins,
                            options.minTransferMinutes,
                        )
                else -> prediction.successProbability
            }
            if (departureProb != null) {
                replaceStopProbability(
                    base,
                    prediction.legIndex + 1,
                    intermediateIndex = null,
                    isArrival = false,
                    departureProb,
                    isEstimate = false,
                )
            }
        }
        val lastRail = journey.legs.indexOfLast { !it.isWalking }
        val finalDistribution = distributions.lastOrNull()
        if (lastRail >= 0 && finalDistribution != null) {
            val finalProb = PredictionScoring.probabilityExactlyOnTime(finalDistribution, offset)
            replaceStopProbability(base, lastRail, intermediateIndex = null, isArrival = true, finalProb, isEstimate = false)
        }
        val hasMl = base.any { !it.isEstimate }
        return if (hasMl) base.filter { !it.isEstimate } else base
    }

    private fun replaceStopProbability(
        list: MutableList<StopTimelinessPrediction>,
        legIndex: Int,
        intermediateIndex: Int?,
        isArrival: Boolean,
        probability: Double,
        isEstimate: Boolean,
    ) {
        val idx = list.indexOfFirst {
            it.legIndex == legIndex &&
                it.intermediateIndex == intermediateIndex &&
                it.isArrival == isArrival
        }
        if (idx < 0) return
        list[idx] = list[idx].copy(
            probability = probability.coerceIn(0.0, 1.0),
            isEstimate = isEstimate,
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
        /** Public rating API used by bahnvorhersage.de (mobile app / web). */
        const val DEFAULT_MOBILE_BASE_URL = "https://bahnvorhersage.de/api/mobile/v2"

        /** Disable ML: set `bahnVorhersageApiUrl=` empty in gradle.properties. */
        const val DEFAULT_BASE_URL = DEFAULT_MOBILE_BASE_URL

        internal fun syntheticTripId(journeyId: String, legIndex: Int): String =
            "openbahn-${journeyId}-leg$legIndex"
    }
}
