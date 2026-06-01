package de.openbahn.api

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.RatedJourney
import de.openbahn.model.StopEvent
import de.openbahn.model.StopTimelinessPrediction
import de.openbahn.model.TransferPrediction
import kotlinx.serialization.json.Json
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

/**
 * Maps OpenBahn [Journey] models to/from the FPTF JSON used by
 * `POST https://bahnvorhersage.de/api/mobile/v2/journeys`.
 */
internal object BahnVorhersageFptfMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun buildRateRequest(
        journeys: List<Journey>,
        tripRoutes: Map<String, List<StopEvent>>,
    ): JsonObject {
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
                    add(journeyJson(journey))
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
        val rated = runCatching { json.parseToJsonElement(responseBody).jsonArray }.getOrNull() ?: return null
        if (rated.size != journeys.size) return null
        return rated.mapIndexed { index, element ->
            toRatedJourney(
                journey = journeys[index],
                ratedJson = element.jsonObject,
                options = options,
            )
        }
    }

    private fun toRatedJourney(
        journey: Journey,
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
        var ratedIndex = 0
        journey.legs.forEachIndexed { legIndex, journeyLeg ->
            if (journeyLeg.isWalking) {
                val transferJson = ratedLegs.getOrNull(ratedIndex)?.jsonObject ?: return@forEachIndexed
                ratedIndex++
                parseTransferScore(transferJson, legIndex - 1)?.let { transfers.add(it) }
            } else {
                val railJson = ratedLegs.getOrNull(ratedIndex)?.jsonObject ?: return@forEachIndexed
                ratedIndex++
                applyDelayPrediction(
                    heuristicStops,
                    legIndex,
                    isArrival = false,
                    railJson["departureDelayPrediction"]?.jsonObject,
                    options,
                )
                applyDelayPrediction(
                    heuristicStops,
                    legIndex,
                    isArrival = true,
                    railJson["arrivalDelayPrediction"]?.jsonObject,
                    options,
                )
            }
        }

        val lastRail = journey.legs.indexOfLast { !it.isWalking }
        val punctuality = heuristicStops.firstOrNull {
            it.legIndex == lastRail && it.intermediateIndex == null && it.isArrival
        }?.probability

        return RatedJourney(
            journey = journey,
            predictions = transfers,
            stopTimeliness = heuristicStops,
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
        val probability = PredictionScoring.probabilityDelayAtMost(
            distribution,
            offset,
            JourneyRatingOptions.BAHNVORHERSAGE_DISPLAY_TOLERANCE_MINUTES,
        )
        val idx = stops.indexOfFirst {
            it.legIndex == legIndex && it.intermediateIndex == null && it.isArrival == isArrival
        }
        if (idx >= 0) {
            stops[idx] = stops[idx].copy(probability = probability, isEstimate = false)
        }
    }

    private fun journeyJson(journey: Journey): JsonObject = buildJsonObject {
        put("type", "journey")
        journey.refreshToken?.let { put("refreshToken", it) }
        putJsonArray("legs") {
            journey.legs.forEach { leg ->
                add(legJson(leg))
            }
        }
    }

    private fun legJson(leg: Leg): JsonObject {
        if (leg.isWalking) {
            return buildJsonObject {
                put("type", "transfer")
                putObject("origin", stopJson(leg.origin))
                putObject("destination", stopJson(leg.destination))
                put("departure", timeJson(leg.origin))
                put("arrival", timeJson(leg.destination))
                leg.distanceMeters?.let { put("distance", it) }
                put("mode", "walk")
            }
        }
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
            leg.tripId?.let { put("tripId", it) }
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
        val whenTime = stop.prognosedTime?.takeIf { it.isNotBlank() } ?: stop.scheduledTime
        return buildJsonObject {
            put("type", "stopover")
            putObject("stop", stopJson(stop))
            if (isFirst) {
                put("plannedArrival", JsonNull)
            } else {
                put("plannedArrival", stop.scheduledTime)
            }
            if (isLast) {
                put("plannedDeparture", JsonNull)
            } else {
                put("plannedDeparture", stop.scheduledTime)
            }
            if (!isFirst) {
                put("arrival", JsonPrimitive(whenTime))
            }
            if (!isLast) {
                put("departure", JsonPrimitive(whenTime))
            }
            put("arrivalPlatform", stop.platform.orEmpty())
            put("departurePlatform", stop.platform.orEmpty())
            put("cancelled", stop.cancelled)
        }
    }

    private fun stopJson(stop: StopEvent): JsonObject {
        val (lat, lon) = BahnVorhersageStationData.coordsForStop(stop)
            ?: (52.520 to 13.405)
        val stopId = stop.id?.takeIf { it.isNotBlank() } ?: "eva:${stop.name}"
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
        return raw.take(19)
    }
}
