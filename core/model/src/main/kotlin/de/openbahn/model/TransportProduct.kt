package de.openbahn.model

import kotlinx.serialization.Serializable

/** Transport modes matching bahn.de / db-vendo produktgattungen. */
enum class TransportProduct(val vendoCode: String, val displayNameEn: String, val displayNameDe: String) {
    ICE("ICE", "ICE", "ICE"),
    IC_EC("EC_IC", "IC/EC", "IC/EC"),
    IR("IR", "IR", "IR"),
    REGIONAL("REGIONAL", "Regional", "Regional"),
    SBAHN("SBAHN", "S-Bahn", "S-Bahn"),
    BUS("BUS", "Bus", "Bus"),
    FERRY("SCHIFF", "Ferry", "Fähre"),
    UBAHN("UBAHN", "U-Bahn", "U-Bahn"),
    TRAM("TRAM", "Tram", "Tram"),
    ON_DEMAND("ANRUFPFLICHTIG", "On-demand", "Anrufpflichtig");

    companion object {
        val ALL = entries.toSet()
    }
}

@Serializable
data class JourneySearchOptions(
    val departureTime: String? = null,
    val arrivalSearch: Boolean = false,
    val maxTransfers: Int? = null,
    val minTransferMinutes: Int? = null,
    val bikeCarriage: Boolean = false,
    val directOnly: Boolean = false,
    val deutschlandTicketOwned: Boolean = false,
    val deutschlandTicketConnectionsOnly: Boolean = false,
    val fastRoutesOnly: Boolean = true,
    val products: Set<TransportProduct> = TransportProduct.ALL,
    val viaStops: List<ViaStop> = emptyList(),
    val accessibility: AccessibilityFilter = AccessibilityFilter.NONE,
    val routingMode: RoutingMode = RoutingMode.STANDARD,
    val locale: String = "de",
)

enum class AccessibilityFilter {
    NONE,
    WHEELCHAIR,
    NO_ELEVATOR,
}

enum class RoutingMode {
    STANDARD,
    LEAST_WALKING,
}

@Serializable
data class ViaStop(
    val locationId: String,
    val stayMinutes: Int? = null,
    val segmentProducts: Set<TransportProduct>? = null,
)
