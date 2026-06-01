package de.openbahn.api

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.RatedJourney
import de.openbahn.model.StopEvent
import de.openbahn.model.StopTimelinessPrediction
import de.openbahn.model.TransferPrediction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Maps OpenBahn [Journey] models to/from the FPTF JSON used by
 * `POST https://bahnvorhersage.de/api/mobile/v2/journeys`.
 */
internal object BahnVorhersageFptfMapper {
    private val json = Json { ignoreUnknownKeys = true }
    private val berlin = ZoneId.of("Europe/Berlin")
    private val localDateTime = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val offsetDateTime = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun buildRateRequest(
        journeys: List<Journey>,
        tripRoutes: Map<String, List<StopEvent>>,
    ): JsonObject {
        val expandedByJourney = journeys.associateWith { expandLegsForRating(it.legs) }
        val tripsJson = buildJsonObject {
            tripRoutes.forEach { (tripId, stops) ->
                putJsonObject(tripId) {
                    put("id", tripId)
                    putJsonArray("stopovers") {
                        stops.forEach { stop ->
                            add(stopoverJson(stop, stops))
                        }
                    }
                }
            }
        }
        return buildJsonObject {
            putJsonArray("journeys") {
                journeys.forEach { journey ->
                    add(journeyJson(journey, expandedByJourney.getValue(journey)))
                }
            }
            put("trips", tripsJson)
        }
    }

    fun parseRatedJourneys(
        responseBody: String,
        journeys: List<Journey>,
        options: JourneyRatingOptions,
    ): List<RatedJourney>? {
        val root = runCatching { json.parseToJsonElement(responseBody) }.getOrElse { e ->
            BahnVorhersageDebug.logParseFailure("invalid JSON: ${e.message}", responseBody)
            return null
        }
        val rated = root as? JsonArray
        if (rated == null) {
            BahnVorhersageDebug.logParseFailure(
                "root is ${root::class.simpleName}, expected array",
                responseBody,
            )
            return null
        }
        if (rated.size != journeys.size) {
            BahnVorhersageDebug.logParseFailure(
                "array size ${rated.size} != journeys ${journeys.size}",
                responseBody,
            )
            return null
        }
        return rated.mapIndexed { index, element ->
            val obj = element as? JsonObject
                ?: run {
                    BahnVorhersageDebug.logParseFailure(
                        "journey[$index] is not an object",
                        responseBody,
                    )
                    return null
                }
            toRatedJourney(
                journey = journeys[index],
                expandedLegs = expandLegsForRating(journeys[index].legs),
                ratedJson = obj,
                options = options,
            )
        }
    }

    private fun toRatedJourney(
        journey: Journey,
        expandedLegs: List<RatingLeg>,
        ratedJson: JsonObject,
        options: JourneyRatingOptions,
    ): RatedJourney {
        val heuristicStops = BahnVorhersageHeuristic.buildStopTimeliness(
            journey,
            options.punctualityToleranceMinutes,
            options.minTransferMinutes,
        ).toMutableList()
        val transfers = mutableListOf<TransferPrediction>()
        val ratedLegs: List<JsonElement> = ratedJson["legs"]?.jsonArray?.toList() ?: emptyList()
        expandedLegs.forEachIndexed { expandedIndex, ratingLeg ->
            val ratedLeg = ratedLegs.getOrNull(expandedIndex)?.jsonObject ?: return@forEachIndexed
            when {
                ratingLeg.leg.isWalking -> {
                    parseTransferScore(ratedLeg, ratingLeg.sourceLegIndex)?.let { transfers.add(it) }
                }
                else -> {
                    val legIndex = ratingLeg.sourceLegIndex
                    applyDelayPrediction(
                        heuristicStops,
                        legIndex,
                        isArrival = false,
                        ratedLeg["departureDelayPrediction"]?.jsonObject,
                        options,
                    )
                    applyDelayPrediction(
                        heuristicStops,
                        legIndex,
                        isArrival = true,
                        ratedLeg["arrivalDelayPrediction"]?.jsonObject,
                        options,
                    )
                }
            }
        }

        val lastRail = journey.legs.indexOfLast { !it.isWalking }
        val hasMlStops = heuristicStops.any { !it.isEstimate }
        val stopTimeliness = if (hasMlStops) {
            heuristicStops.filter { !it.isEstimate }
        } else {
            heuristicStops
        }
        val punctuality = stopTimeliness.firstOrNull {
            it.legIndex == lastRail && it.intermediateIndex == null && it.isArrival
        }?.probability

        return RatedJourney(
            journey = journey,
            predictions = transfers,
            stopTimeliness = stopTimeliness,
            punctualityProbability = punctuality,
            punctualityIsEstimate = false,
            minTransferMinutesUsed = options.minTransferMinutes,
            punctualityToleranceMinutes = options.punctualityToleranceMinutes,
        )
    }

    private fun parseTransferScore(transferJson: JsonObject, legIndexBeforeTransfer: Int): TransferPrediction? {
        if (legIndexBeforeTransfer < 0) return null
        val scoreElement = transferJson["transferScore"] ?: return null
        val score = when {
            scoreElement is JsonPrimitive && scoreElement.isString ->
                if (scoreElement.content == "cancelled") 0.0 else null
            else -> scoreElement.jsonPrimitive.content.toDoubleOrNull()
        } ?: return null
        return TransferPrediction(
            legIndex = legIndexBeforeTransfer,
            successProbability = score.coerceIn(0.0, 1.0),
            isEstimate = false,
        )
    }

    private fun applyDelayPrediction(
        stops: MutableList<StopTimelinessPrediction>,
        legIndex: Int,
        isArrival: Boolean,
        prediction: JsonObject?,
        options: JourneyRatingOptions,
    ) {
        if (prediction == null) return
        val distribution = prediction["predictions"]?.jsonArray?.mapNotNull {
            it.jsonPrimitive.content.toDoubleOrNull()
        } ?: return
        if (distribution.isEmpty()) return
        val offset = prediction["offset"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val probability = PredictionScoring.probabilityExactlyOnTime(distribution, offset)
        val idx = stops.indexOfFirst {
            it.legIndex == legIndex && it.intermediateIndex == null && it.isArrival == isArrival
        }
        if (idx >= 0) {
            stops[idx] = stops[idx].copy(probability = probability, isEstimate = false)
        }
    }

    private fun journeyJson(journey: Journey, expandedLegs: List<RatingLeg>): JsonObject = buildJsonObject {
        put("type", "journey")
        journey.refreshToken?.let { put("refreshToken", it) }
        putJsonArray("legs") {
            expandedLegs.forEach { ratingLeg ->
                add(legJson(ratingLeg, journey.id))
            }
        }
    }

    private fun legJson(ratingLeg: RatingLeg, journeyId: String): JsonObject {
        val leg = ratingLeg.leg
        if (leg.isWalking) {
            // bahnvorhersage.api.fptf.leg_or_transfer only treats `walking: true` as a transfer.
            // Transfer duration = departure - arrival (arrival at origin, then depart toward next leg).
            return buildJsonObject {
                put("walking", true)
                putObject("origin", stopJson(leg.origin))
                putObject("destination", stopJson(leg.destination))
                put("arrival", timeJson(leg.origin))
                put("departure", timeJson(leg.destination))
                leg.distanceMeters?.let { put("distance", it) }
            }
        }
        val tripId = ratingTripId(journeyId, ratingLeg.sourceLegIndex, leg)
        return buildJsonObject {
            put("type", "leg")
            putObject("origin", stopJson(leg.origin))
            putObject("destination", stopJson(leg.destination))
            put("plannedDeparture", timeJson(leg.origin, scheduled = true))
            put("plannedArrival", timeJson(leg.destination, scheduled = true))
            put("departure", timeJson(leg.origin))
            put("arrival", timeJson(leg.destination))
            put("departurePlatform", leg.origin.platform.orEmpty())
            put("arrivalPlatform", leg.destination.platform.orEmpty())
            put("cancelled", leg.origin.cancelled || leg.destination.cancelled)
            put("tripId", tripId)
            putObject("line", lineJson(leg))
        }
    }

    private fun lineJson(leg: Leg): JsonObject {
        val category = BahnVorhersageStationData.categoryFromLeg(leg.product, leg.lineName)
        val name = leg.lineName?.takeIf { it.isNotBlank() } ?: leg.lineDetail.orEmpty()
        return buildJsonObject {
            put("id", 0)
            put("name", name.ifBlank { category })
            put("operator", leg.operator?.takeIf { it.isNotBlank() } ?: "DB")
            put("productName", category)
            put("fahrtNr", BahnVorhersageStationData.lineNumber(leg.lineName, leg.lineDetail).toString())
            put("adminCode", "80")
            put("type", "line")
        }
    }

    private fun stopoverJson(stop: StopEvent, allStops: List<StopEvent>): JsonObject {
        val index = allStops.indexOf(stop)
        val isFirst = index <= 0
        val isLast = index >= allStops.lastIndex
        return buildJsonObject {
            put("type", "stopover")
            putObject("stop", stopJson(stop))
            if (isFirst) {
                put("plannedArrival", JsonNull)
            } else {
                put("plannedArrival", timeJson(stop, scheduled = true))
            }
            if (isLast) {
                put("plannedDeparture", JsonNull)
            } else {
                put("plannedDeparture", timeJson(stop, scheduled = true))
            }
            // bahnvorhersage `extract_arrival_departure` indexes these keys unconditionally.
            if (isFirst) {
                put("arrival", JsonNull)
            } else {
                put("arrival", JsonPrimitive(timeJson(stop)))
            }
            if (isLast) {
                put("departure", JsonNull)
            } else {
                put("departure", JsonPrimitive(timeJson(stop)))
            }
            put("arrivalPlatform", stop.platform.orEmpty())
            put("departurePlatform", stop.platform.orEmpty())
            put("cancelled", stop.cancelled)
        }
    }

    private fun stopJson(stop: StopEvent): JsonObject {
        val (lat, lon) = BahnVorhersageStationData.coordsForStop(stop)
            ?: BahnVorhersageStationData.defaultCoordsForName(stop.name)
            ?: (0.0 to 0.0)
        val stopId = BahnVorhersageStationData.evaIdForStop(stop)
        return buildJsonObject {
            put("id", stopId)
            put("name", stop.name)
            put("type", "stop")
            putJsonObject("location") {
                put("id", stopId)
                put("latitude", lat)
                put("longitude", lon)
                put("type", "location")
            }
        }
    }

    private fun JsonObjectBuilder.putObject(key: String, value: JsonObject) {
        put(key, value as JsonElement)
    }

    private fun timeJson(stop: StopEvent, scheduled: Boolean = false): String {
        val raw = if (scheduled) {
            stop.scheduledTime
        } else {
            stop.prognosedTime?.takeIf { it.isNotBlank() } ?: stop.scheduledTime
        }
        if (raw.isBlank()) return raw
        return runCatching {
            LocalDateTime.parse(raw.take(19), localDateTime)
                .atZone(berlin)
                .format(offsetDateTime)
        }.getOrElse { raw.take(19) }
    }
}
