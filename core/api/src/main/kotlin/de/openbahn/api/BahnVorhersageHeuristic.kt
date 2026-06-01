package de.openbahn.api

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.OnTimeToleranceSettings
import de.openbahn.model.StopEvent
import de.openbahn.model.StopTimelinessPrediction
import de.openbahn.model.TransferPrediction
import de.openbahn.model.TransportProduct
import de.openbahn.model.delayMinutesFromTimes
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Fallback scores when the Bahn-Vorhersage API is unreachable or returns no predictions. */
internal object BahnVorhersageHeuristic {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val isoLocal = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    fun buildStopTimeliness(
        journey: Journey,
        onTimeTolerance: OnTimeToleranceSettings = OnTimeToleranceSettings(),
        minTransferMinutes: Int? = null,
    ): List<StopTimelinessPrediction> {
        val results = mutableListOf<StopTimelinessPrediction>()
        var legOrdinal = 0

        journey.legs.forEachIndexed { legIndex, leg ->
            if (leg.isWalking) return@forEachIndexed

            val minutesIntoTrip = minutesFromJourneyStart(journey, leg.origin.scheduledTime) ?: (legOrdinal * 45)

            results.add(
                StopTimelinessPrediction(
                    legIndex = legIndex,
                    intermediateIndex = null,
                    isArrival = false,
                    probability = punctualityForStop(
                        stop = leg.origin,
                        leg = leg,
                        toleranceMinutes = onTimeTolerance.departureMinutes,
                        minutesIntoTrip = minutesIntoTrip,
                    ).coerceIn(0.05, 0.98),
                    isEstimate = true,
                ),
            )

            leg.intermediateStops.forEachIndexed { viaIndex, stop ->
                val viaMinutes = minutesFromJourneyStart(journey, stop.scheduledTime) ?: (minutesIntoTrip + 15)
                results.add(
                    StopTimelinessPrediction(
                        legIndex = legIndex,
                        intermediateIndex = viaIndex,
                        isArrival = true,
                        probability = punctualityForStop(
                            stop = stop,
                            leg = leg,
                            toleranceMinutes = onTimeTolerance.viaStopMinutes,
                            minutesIntoTrip = viaMinutes,
                            isIntermediateVia = true,
                        ).coerceIn(0.05, 0.98),
                        isEstimate = true,
                    ),
                )
            }

            val arrivalMinutes = minutesFromJourneyStart(journey, leg.destination.scheduledTime)
                ?: (minutesIntoTrip + (leg.durationMinutes ?: 30))
            results.add(
                StopTimelinessPrediction(
                    legIndex = legIndex,
                    intermediateIndex = null,
                    isArrival = true,
                    probability = punctualityForStop(
                        stop = leg.destination,
                        leg = leg,
                        toleranceMinutes = onTimeTolerance.arrivalMinutes,
                        minutesIntoTrip = arrivalMinutes,
                        isLegEndpoint = true,
                    ).coerceIn(0.05, 0.98),
                    isEstimate = true,
                ),
            )

            legOrdinal++
        }
        return results
    }

    fun estimatePunctuality(journey: Journey, onTimeTolerance: OnTimeToleranceSettings): Double? {
        val stops = buildStopTimeliness(journey, onTimeTolerance, minTransferMinutes = null)
        val lastRailLeg = journey.legs.indexOfLast { !it.isWalking }
        if (lastRailLeg < 0) return null
        return stops.firstOrNull {
            it.legIndex == lastRailLeg && it.intermediateIndex == null && it.isArrival
        }?.probability
    }

    fun estimate(journey: Journey, minTransferMinutes: Int? = null): List<TransferPrediction> {
        if (journey.legs.size < 2) return emptyList()
        return (0 until journey.legs.lastIndex).mapNotNull { index ->
            val leg = journey.legs[index]
            val next = journey.legs[index + 1]
            if (leg.isWalking || next.isWalking) return@mapNotNull null
            val transferMins = BahnVorhersageRequestBuilder.transferMinutesBetween(leg.destination, next.origin)
            TransferPrediction(
                legIndex = index,
                successProbability = scoreForTransferMinutes(transferMins, minTransferMinutes),
                isEstimate = true,
            )
        }
    }

    private fun punctualityForStop(
        stop: StopEvent,
        leg: Leg,
        toleranceMinutes: Int,
        minutesIntoTrip: Int,
        isLegEndpoint: Boolean = false,
        isIntermediateVia: Boolean = false,
    ): Double {
        if (stop.cancelled) return 0.05
        val delay = stop.delayMinutes
            ?: delayMinutesFromTimes(stop.scheduledTime, stop.prognosedTime)
            ?: 0
        val baseline = productBaseline(leg.product, leg.lineName)
        // Intermediate vias: light drift only (pass-through, not journey-end risk).
        val driftPenalty = when {
            isIntermediateVia -> 1.0
            minutesIntoTrip >= 180 -> 0.88
            minutesIntoTrip >= 120 -> 0.92
            minutesIntoTrip >= 60 -> 0.96
            else -> 1.0
        }
        val endpointPenalty = if (!isIntermediateVia && isLegEndpoint && minutesIntoTrip >= 90) 0.94 else 1.0

        val core = when {
            delay > toleranceMinutes -> {
                val over = delay - toleranceMinutes
                when {
                    over >= 20 -> 0.10
                    over >= 15 -> 0.18
                    over >= 10 -> 0.28
                    over >= 5 -> 0.40
                    else -> 0.52
                }
            }
            delay > 0 -> {
                val headroom = toleranceMinutes - delay
                (baseline * (0.55 + headroom * 0.06)).coerceIn(0.15, 0.88)
            }
            else -> baseline
        }
        return (core * driftPenalty * endpointPenalty).coerceIn(0.05, 0.98)
    }

    private fun productBaseline(product: TransportProduct?, lineName: String?): Double {
        val line = lineName?.uppercase().orEmpty()
        return when (product) {
            TransportProduct.ICE -> 0.84
            TransportProduct.IC_EC -> 0.78
            TransportProduct.IR -> 0.72
            TransportProduct.REGIONAL -> 0.64
            TransportProduct.SBAHN -> 0.68
            TransportProduct.UBAHN -> 0.70
            TransportProduct.BUS -> 0.58
            TransportProduct.TRAM -> 0.60
            TransportProduct.FERRY -> 0.75
            TransportProduct.ON_DEMAND -> 0.55
            null -> when {
                line.startsWith("ICE") -> 0.84
                line.startsWith("IC") || line.startsWith("EC") -> 0.78
                line.startsWith("RE") -> 0.66
                line.startsWith("RB") -> 0.62
                line.startsWith("S") -> 0.68
                line.startsWith("BUS") -> 0.58
                else -> 0.70
            }
            else -> 0.70
        }
    }

    private fun minutesFromJourneyStart(journey: Journey, stopScheduled: String): Int? {
        val start = journey.legs.firstOrNull { !it.isWalking }?.origin?.scheduledTime ?: journey.departure
        val startInstant = parseInstant(start) ?: return null
        val stopInstant = parseInstant(stopScheduled) ?: return null
        return Duration.between(startInstant, stopInstant).toMinutes().toInt().coerceAtLeast(0)
    }

    private fun parseInstant(raw: String): Instant? = try {
        LocalDateTime.parse(raw.take(19), isoLocal).atZone(berlin).toInstant()
    } catch (_: Exception) {
        null
    }

    private fun scoreForTransferMinutes(transferMins: Long?, minTransferMinutes: Int?): Double? {
        if (transferMins == null) return null
        val slack = if (minTransferMinutes != null) transferMins - minTransferMinutes else transferMins
        return when {
            slack < 0 -> 0.12
            slack >= 25 -> 0.93
            slack >= 18 -> 0.85
            slack >= 12 -> 0.72
            slack >= 8 -> 0.55
            slack >= 5 -> 0.38
            else -> 0.22
        }
    }
}
