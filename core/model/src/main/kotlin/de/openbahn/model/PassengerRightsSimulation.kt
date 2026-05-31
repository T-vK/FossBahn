package de.openbahn.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

/**
 * Debug / QA overlay to test passenger-rights evaluation without a real delayed train.
 * Not used in release logic unless explicitly enabled via preferences.
 */
@Serializable
data class PassengerRightsSimulationConfig(
    val enabled: Boolean = false,
    /** Delay applied at the final rail destination (minutes). */
    val arrivalDelayMinutes: Int = 0,
    val isLastConnectionOfDay: Boolean = false,
    val hasPublicAlternativeInWindow: Boolean = true,
    /** Treat journey as Deutschlandticket for rule selection. */
    val forceDeutschlandTicket: Boolean = false,
    val simulateCancellation: Boolean = false,
    /** When true, delay the first rail leg's destination so a transfer is missed (needs ≥2 legs). */
    val simulateMissedTransfer: Boolean = false,
) {
    companion object {
        val Disabled = PassengerRightsSimulationConfig()
    }
}

enum class PassengerRightsSimulationPreset {
    OFF,
    DTICKET_DELAY_60,
    DTICKET_DELAY_120,
    EU_LONG_DISTANCE_60,
    LAST_CONNECTION_TAXI,
}

fun PassengerRightsSimulationPreset.toConfig(): PassengerRightsSimulationConfig = when (this) {
    PassengerRightsSimulationPreset.OFF -> PassengerRightsSimulationConfig.Disabled
    PassengerRightsSimulationPreset.DTICKET_DELAY_60 -> PassengerRightsSimulationConfig(
        enabled = true,
        arrivalDelayMinutes = 65,
        forceDeutschlandTicket = true,
    )
    PassengerRightsSimulationPreset.DTICKET_DELAY_120 -> PassengerRightsSimulationConfig(
        enabled = true,
        arrivalDelayMinutes = 125,
        forceDeutschlandTicket = true,
    )
    PassengerRightsSimulationPreset.EU_LONG_DISTANCE_60 -> PassengerRightsSimulationConfig(
        enabled = true,
        arrivalDelayMinutes = 65,
    )
    PassengerRightsSimulationPreset.LAST_CONNECTION_TAXI -> PassengerRightsSimulationConfig(
        enabled = true,
        arrivalDelayMinutes = 75,
        isLastConnectionOfDay = true,
        hasPublicAlternativeInWindow = false,
        forceDeutschlandTicket = true,
    )
}

/** Applies [config] stop times and flags onto a journey for rights testing. */
fun Journey.applyPassengerRightsSimulation(config: PassengerRightsSimulationConfig): Journey {
    if (!config.enabled) return this
    var result = this
    if (config.arrivalDelayMinutes > 0) {
        result = result.withSimulatedArrivalDelay(config.arrivalDelayMinutes)
    }
    if (config.simulateMissedTransfer && result.railLegs().size >= 2) {
        result = result.withSimulatedMissedTransfer(minTransferMinutes = 10)
    }
    if (config.simulateCancellation) {
        result = result.withSimulatedCancellation()
    }
    return result
}

/** Sets destination delay on the last rail leg and updates journey arrival. */
fun Journey.withSimulatedArrivalDelay(delayMinutes: Int): Journey {
    if (delayMinutes <= 0) return this
    val railIndices = legs.mapIndexedNotNull { index, leg -> index.takeIf { !leg.isWalking } }
    if (railIndices.isEmpty()) return this
    val lastRailIndex = railIndices.last()
    val updatedLegs = legs.mapIndexed { index, leg ->
        if (index != lastRailIndex) return@mapIndexed leg
        leg.copy(
            destination = leg.destination.withSimulatedDelayMinutes(delayMinutes),
        )
    }
    return result.copy(
        legs = updatedLegs,
        arrival = updatedLegs[lastRailIndex].destination.prognosedTime
            ?: updatedLegs[lastRailIndex].destination.scheduledTime,
    )
}

/** Delays the first rail leg's arrival so the next leg's scheduled departure is missed. */
fun Journey.withSimulatedMissedTransfer(minTransferMinutes: Int = 10): Journey {
    val railIndices = legs.mapIndexedNotNull { index, leg -> index.takeIf { !leg.isWalking } }
    if (railIndices.size < 2) return this
    val firstRailIndex = railIndices.first()
    val secondRailIndex = railIndices[1]
    val firstLeg = legs[firstRailIndex]
    val secondLeg = legs[secondRailIndex]
    val arrival = parseIsoLocal(firstLeg.destination.scheduledTime) ?: return this
    val nextDeparture = parseIsoLocal(secondLeg.origin.scheduledTime) ?: return this
    val gapMinutes = java.time.Duration.between(arrival, nextDeparture).toMinutes().toInt()
    val neededDelay = (gapMinutes - minTransferMinutes + 5).coerceAtLeast(15)
    val updatedLegs = legs.mapIndexed { index, leg ->
        when (index) {
            firstRailIndex -> leg.copy(
                destination = leg.destination.withSimulatedDelayMinutes(neededDelay),
            )
            else -> leg
        }
    }
    return copy(legs = updatedLegs)
}

fun Journey.withSimulatedCancellation(): Journey {
    val railIndices = legs.mapIndexedNotNull { index, leg -> index.takeIf { !leg.isWalking } }
    if (railIndices.isEmpty()) return this
    val targetIndex = railIndices.first()
    val updatedLegs = legs.mapIndexed { index, leg ->
        if (index != targetIndex) return@mapIndexed leg
        leg.copy(
            origin = leg.origin.copy(cancelled = true),
            destination = leg.destination.copy(cancelled = true),
        )
    }
    return copy(legs = updatedLegs)
}

fun StopEvent.withSimulatedDelayMinutes(delayMinutes: Int): StopEvent {
    if (delayMinutes <= 0) return this
    val prognosed = addMinutesToIsoLocal(scheduledTime, delayMinutes) ?: scheduledTime
    return copy(
        delayMinutes = delayMinutes,
        prognosedTime = prognosed,
    )
}

private fun addMinutesToIsoLocal(iso: String, minutes: Int): String? {
    val base = parseIsoLocal(iso) ?: return null
    return base.plusMinutes(minutes.toLong()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
}

private fun parseIsoLocal(iso: String): LocalDateTime? = runCatching {
    LocalDateTime.parse(iso.take(19), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
}.getOrNull()
