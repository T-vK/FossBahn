package de.openbahn.model

import kotlinx.serialization.Serializable

@Serializable
data class Location(
    val id: String,
    val name: String,
    val type: LocationType = LocationType.STATION,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val evaNumber: String? = null,
)

enum class LocationType {
    STATION,
    STOP,
    POI,
    ADDRESS,
}

@Serializable
data class StopEvent(
    val name: String,
    val id: String? = null,
    val platform: String? = null,
    val scheduledTime: String,
    val prognosedTime: String? = null,
    val delayMinutes: Int? = null,
    val cancelled: Boolean = false,
    val remarks: List<String> = emptyList(),
)

@Serializable
data class Leg(
    val origin: StopEvent,
    val destination: StopEvent,
    /** Stations between origin and destination (excluding endpoints). */
    val intermediateStops: List<StopEvent> = emptyList(),
    val lineName: String? = null,
    val product: TransportProduct? = null,
    val operator: String? = null,
    val loadFactor: String? = null,
    val bikeAllowed: Boolean? = null,
    val tripId: String? = null,
)

@Serializable
data class Journey(
    val id: String,
    val legs: List<Leg>,
    val durationMinutes: Int,
    val transfers: Int,
    val departure: String,
    val arrival: String,
    val priceHint: String? = null,
    val refreshToken: String? = null,
    val deutschlandTicketValid: Boolean? = null,
    val remarks: List<String> = emptyList(),
)

@Serializable
data class StationBoard(
    val stationName: String,
    val stationId: String,
    val departures: List<BoardEntry> = emptyList(),
    val arrivals: List<BoardEntry> = emptyList(),
)

@Serializable
data class BoardEntry(
    val line: String,
    val direction: String,
    val scheduledTime: String,
    val prognosedTime: String? = null,
    val platform: String? = null,
    val delayMinutes: Int? = null,
    val cancelled: Boolean = false,
    val product: TransportProduct? = null,
    val tripId: String? = null,
    val remarks: List<String> = emptyList(),
)

@Serializable
data class TransferPrediction(
    val legIndex: Int,
    val successProbability: Double?,
    val delayDistribution: List<Double>? = null,
    /** True when scored from transfer time only (no Bahn-Vorhersage API). */
    val isEstimate: Boolean = false,
)

@Serializable
data class RatedJourney(
    val journey: Journey,
    val predictions: List<TransferPrediction> = emptyList(),
)
