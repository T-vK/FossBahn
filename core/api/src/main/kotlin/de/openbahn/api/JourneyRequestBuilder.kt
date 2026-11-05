package de.openbahn.api

import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.Location
import de.openbahn.model.LocationType
import de.openbahn.model.TransportProduct
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** bahn.de / db-vendo halt identifier for journey requests (abfahrtsHalt / ankunftsHalt). */
fun Location.haltIdForJourney(): String = haltIdForJourney(
    locationId = id,
    evaNumber = evaNumber,
    latitude = latitude,
    longitude = longitude,
    type = type,
    label = name,
)

/** Stable bahn.de halt id for `/angebote/fahrplan` (avoids volatile `@p=` / `@i=` params from `/orte`). */
fun haltIdForJourney(
    locationId: String,
    evaNumber: String? = null,
    latitude: Double? = null,
    longitude: Double? = null,
    type: LocationType = LocationType.STATION,
    label: String? = null,
): String {
    if (type == LocationType.ADDRESS && latitude != null && longitude != null) {
        return haltIdForCoordinates(latitude, longitude, label ?: locationId)
    }
    val eva = evaNumber?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        ?: EVA_IN_HALT_ID.find(locationId)?.groupValues?.getOrNull(1)
        ?: locationId.takeIf { it.length >= 6 && it.all(Char::isDigit) }
    if (eva != null) return "A=1@L=$eva@"
    if (locationId.startsWith("A=1@")) return locationId
    if (latitude != null && longitude != null) return haltIdForCoordinates(latitude, longitude)
    return locationId
}

/** Coordinate halt id (bahn.de `@X`/`@Y` are micro-degrees). */
fun haltIdForCoordinates(latitude: Double, longitude: Double, label: String? = null): String {
    val x = (longitude * COORD_SCALE).toLong()
    val y = (latitude * COORD_SCALE).toLong()
    val encoded = label?.takeIf { it.isNotBlank() }?.let { encodeHaltName(it) }
    return if (encoded != null) "A=1@O=$encoded@X=$x@Y=$y@" else "A=1@X=$x@Y=$y@"
}

private const val COORD_SCALE = 1_000_000.0

private fun encodeHaltName(name: String): String =
    name.replace('@', ' ').replace(' ', '+')

private val EVA_IN_HALT_ID = Regex("""@L=(\d+)@""")

internal object JourneyRequestBuilder {
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    fun build(
        from: Location,
        to: Location,
        options: JourneySearchOptions,
        whenTime: LocalDateTime,
        pagingReference: String? = null,
    ): JsonObject = buildJsonObject {
        pagingReference?.let { put("pagingReference", it) }
        put("abfahrtsHalt", from.haltIdForJourney())
        put("ankunftsHalt", to.haltIdForJourney())
        put("anfrageZeitpunkt", whenTime.format(timeFormatter))
        put("ankunftSuche", if (options.arrivalSearch) "ANKUNFT" else "ABFAHRT")
        options.maxTransfers?.let { put("maxUmstiege", it) }
        options.minTransferMinutes?.let { put("minUmstiegszeit", it) }
        put("bikeCarriage", options.bikeCarriage)
        put(
            "deutschlandTicketVorhanden",
            options.deutschlandTicketOwned || options.deutschlandTicketConnectionsOnly,
        )
        put("nurDeutschlandTicketVerbindungen", options.deutschlandTicketConnectionsOnly)
        put("schnelleVerbindungen", options.fastRoutesOnly)
        put("sitzplatzOnly", false)
        put("reservierungsKontingenteVorhanden", false)
        put("klasse", if (options.firstClass) "KLASSE_1" else "KLASSE_2")
        putJsonArray("reisende") {
            add(
                buildJsonObject {
                    put("typ", "ERWACHSENER")
                    put("anzahl", 1)
                    putJsonArray("alter") { }
                    putJsonArray("ermaessigungen") { }
                },
            )
        }
        if (options.directOnly) put("maxUmstiege", 0)
        val products = options.products.ifEmpty { TransportProduct.ALL }
        putJsonArray("produktgattungen") {
            products.forEach { add(it.vendoCode) }
        }
        if (options.viaStops.isNotEmpty()) {
            putJsonArray("zwischenhalte") {
                options.viaStops.forEach { via ->
                    add(
                        buildJsonObject {
                            put("id", haltIdForJourney(via.locationId))
                            via.stayMinutes?.let { put("aufenthaltsdauer", it) }
                        },
                    )
                }
            }
        }
        when (options.accessibility) {
            de.openbahn.model.AccessibilityFilter.WHEELCHAIR ->
                put("barrierefrei", "ROLLSTUHL")
            de.openbahn.model.AccessibilityFilter.NO_ELEVATOR ->
                put("barrierefrei", "OHNE_AUFZUG")
            else -> Unit
        }
        when (options.routingMode) {
            de.openbahn.model.RoutingMode.LEAST_WALKING ->
                put("routingMode", "WENIG_FUSSWEG")
            else -> Unit
        }
    }

    /**
     * Mobile (`app.services-bahn.de`) journey body used by DB Navigator / db-vendo `dbnav`.
     * Used as a fallback when the web (`int.bahn.de`) endpoint is WAF-blocked.
     */
    fun buildMobile(
        from: Location,
        to: Location,
        options: JourneySearchOptions,
        whenTime: LocalDateTime,
        pagingReference: String? = null,
    ): JsonObject = buildJsonObject {
        put("autonomeReservierung", false)
        putJsonArray("einstiegsTypList") { add("STANDARD") }
        put(
            "fahrverguenstigungen",
            buildJsonObject {
                put(
                    "deutschlandTicketVorhanden",
                    options.deutschlandTicketOwned || options.deutschlandTicketConnectionsOnly,
                )
                put("nurDeutschlandTicketVerbindungen", options.deutschlandTicketConnectionsOnly)
            },
        )
        put("klasse", if (options.firstClass) "KLASSE_1" else "KLASSE_2")
        put(
            "reisendenProfil",
            buildJsonObject {
                putJsonArray("reisende") {
                    add(
                        buildJsonObject {
                            putJsonArray("ermaessigungen") { add("KEINE_ERMAESSIGUNG KLASSENLOS") }
                            put("reisendenTyp", "ERWACHSENER")
                        },
                    )
                }
            },
        )
        put("reservierungsKontingenteVorhanden", false)
        val products = options.products.ifEmpty { TransportProduct.ALL }
        put(
            "reiseHin",
            buildJsonObject {
                put(
                    "wunsch",
                    buildJsonObject {
                        put("abgangsLocationId", from.haltIdForJourney())
                        put("zielLocationId", to.haltIdForJourney())
                        put("alternativeHalteBerechnung", true)
                        put("fahrradmitnahme", options.bikeCarriage)
                        if (options.directOnly) put("maxUmstiege", 0)
                        else options.maxTransfers?.let { put("maxUmstiege", it) }
                        options.minTransferMinutes?.let { put("minUmstiegsdauer", it) }
                        if (!options.fastRoutesOnly) put("economic", true)
                        putJsonArray("verkehrsmittel") {
                            // Mobile profile accepts ALL or a product list; ALL matches DB Navigator.
                            if (products.containsAll(TransportProduct.ALL)) add("ALL")
                            else products.forEach { add(it.vendoCode) }
                        }
                        put(
                            "zeitWunsch",
                            buildJsonObject {
                                put("reiseDatum", whenTime.format(timeFormatter))
                                put("zeitPunktArt", if (options.arrivalSearch) "ANKUNFT" else "ABFAHRT")
                            },
                        )
                        pagingReference?.let { put("context", it) }
                        if (options.viaStops.isNotEmpty()) {
                            putJsonArray("viaLocations") {
                                options.viaStops.forEach { via ->
                                    add(
                                        buildJsonObject {
                                            put("locationId", haltIdForJourney(via.locationId))
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            },
        )
    }
}
