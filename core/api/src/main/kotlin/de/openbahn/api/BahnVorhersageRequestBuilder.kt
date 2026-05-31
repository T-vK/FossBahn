package de.openbahn.api

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import de.openbahn.model.TransportProduct
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Builds columnar payloads for bahnvorhersage.de `/rate-journeys/`.
 * See https://gitlab.com/bahnvorhersage/bahnvorhersage
 */
internal object BahnVorhersageRequestBuilder {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val isoLocal = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    fun build(journey: Journey, minTransferMinutes: Int? = null) = buildJsonObject {
        val events = buildTransferEvents(journey, minTransferMinutes)
        if (events.isEmpty()) return@buildJsonObject

        putJsonArray("number") { events.forEach { add(it.number) } }
        putJsonArray("lat") { events.forEach { add(it.lat) } }
        putJsonArray("lon") { events.forEach { add(it.lon) } }
        putJsonArray("stop_sequence") { events.forEach { add(it.stopSequence) } }
        putJsonArray("distance_traveled") { events.forEach { add(it.distanceTraveled) } }
        putJsonArray("dwell_time_schedule") {
            events.forEach { if (it.dwellSchedule == null) add(JsonNull) else add(it.dwellSchedule) }
        }
        putJsonArray("dwell_time_prognosed") {
            events.forEach { if (it.dwellPrognosed == null) add(JsonNull) else add(it.dwellPrognosed) }
        }
        putJsonArray("bearing") { events.forEach { add(0) } }
        putJsonArray("delay_prognosed") { events.forEach { add(it.delayPrognosed) } }
        putJsonArray("minute_of_day") { events.forEach { add(it.minuteOfDay) } }
        putJsonArray("minutes_to_prognosed_time") { events.forEach { add(it.minutesToPrognosed) } }
        putJsonArray("weekday") { events.forEach { add(it.weekday) } }
        putJsonArray("is_regional") { events.forEach { add(it.isRegional) } }
        putJsonArray("is_arrival") { events.forEach { add(it.isArrival) } }
        putJsonArray("operator") { events.forEach { add(it.operator) } }
        putJsonArray("category") { events.forEach { add(it.category) } }
        putJsonArray("line") { events.forEach { add(it.line) } }
        putJsonArray("prognosed_transfer_time") {
            events.forEach {
                if (it.prognosedTransferMinutes == null) add(JsonNull) else add(it.prognosedTransferMinutes)
            }
        }
        putJsonArray("minimal_transfer_time") {
            events.forEach {
                if (it.minimalTransferMinutes == null) add(JsonNull) else add(it.minimalTransferMinutes)
            }
        }
    }

    fun hasTransferEvents(journey: Journey): Boolean = journey.legs.size >= 2

    private data class RateEvent(
        val number: Int,
        val lat: Double,
        val lon: Double,
        val stopSequence: Int,
        val distanceTraveled: Int,
        val dwellSchedule: Int?,
        val dwellPrognosed: Int?,
        val delayPrognosed: Int,
        val minuteOfDay: Int,
        val minutesToPrognosed: Int,
        val weekday: Int,
        val isRegional: Boolean,
        val isArrival: Boolean,
        val operator: String,
        val category: String,
        val line: String,
        val prognosedTransferMinutes: Int? = null,
        val minimalTransferMinutes: Int? = null,
    )

    /** One arrival + one departure row per transfer (required for transfer_scores). */
    private fun buildTransferEvents(journey: Journey, minTransferMinutes: Int?): List<RateEvent> {
        if (journey.legs.size < 2) return emptyList()
        val events = mutableListOf<RateEvent>()
        var distance = 0
        var seq = 0
        journey.legs.forEachIndexed { index, leg ->
            if (index == journey.legs.lastIndex) return@forEachIndexed
            val next = journey.legs[index + 1]
            val transferMins = transferMinutes(leg.destination, next.origin)
            val arr = stopEvent(
                leg = leg,
                stop = leg.destination,
                isArrival = true,
                stopSequence = seq++,
                distance = distance.also { distance += 40_000 },
            ) ?: return@forEachIndexed
            val dep = stopEvent(
                leg = next,
                stop = next.origin,
                isArrival = false,
                stopSequence = seq++,
                distance = distance.also { distance += 40_000 },
                prognosedTransferMinutes = transferMins?.toInt()?.coerceAtLeast(0),
                minimalTransferMinutes = transferMins?.let {
                    resolveMinimalTransferMinutes(it, minTransferMinutes)
                },
            ) ?: return@forEachIndexed
            events.add(arr)
            events.add(dep)
        }
        return events
    }

    private fun stopEvent(
        leg: Leg,
        stop: StopEvent,
        isArrival: Boolean,
        stopSequence: Int,
        distance: Int,
        prognosedTransferMinutes: Int? = null,
        minimalTransferMinutes: Int? = null,
    ): RateEvent? {
        val (lat, lon) = coordsFromStop(stop.id, stop.name) ?: return null
        val timeStr = stop.prognosedTime ?: stop.scheduledTime
        val instant = parseInstant(timeStr) ?: return null
        val zdt = instant.atZone(berlin)
        return RateEvent(
            number = lineNumber(leg.lineName),
            lat = lat,
            lon = lon,
            stopSequence = stopSequence,
            distanceTraveled = distance,
            dwellSchedule = null,
            dwellPrognosed = null,
            delayPrognosed = (stop.delayMinutes ?: 0).coerceAtLeast(0),
            minuteOfDay = zdt.hour * 60 + zdt.minute,
            minutesToPrognosed = minutesUntil(instant).coerceIn(0, 24 * 60),
            weekday = zdt.dayOfWeek.value,
            isRegional = leg.product?.let { isRegionalProduct(it) } ?: true,
            isArrival = isArrival,
            operator = leg.operator?.takeIf { it.isNotBlank() } ?: "DB",
            category = leg.product?.vendoCode ?: categoryFromLine(leg.lineName),
            line = leg.lineName?.takeIf { it.isNotBlank() } ?: "UNKNOWN",
            prognosedTransferMinutes = prognosedTransferMinutes,
            minimalTransferMinutes = minimalTransferMinutes,
        )
    }

    private fun resolveMinimalTransferMinutes(transferMins: Long, userMinTransferMinutes: Int?): Int =
        userMinTransferMinutes?.coerceIn(1, 60) ?: when {
            transferMins >= 15 -> 5
            transferMins >= 8 -> 4
            else -> 3
        }

    fun transferMinutesBetween(arrival: StopEvent, departure: StopEvent): Long? {
        val arr = parseInstant(arrival.prognosedTime ?: arrival.scheduledTime) ?: return null
        val dep = parseInstant(departure.prognosedTime ?: departure.scheduledTime) ?: return null
        return Duration.between(arr, dep).toMinutes()
    }

    private fun transferMinutes(arrival: StopEvent, departure: StopEvent): Long? =
        transferMinutesBetween(arrival, departure)

    private fun coordsFromStop(id: String?, name: String): Pair<Double, Double>? {
        coordsFromHaltId(id)?.let { return it }
        return defaultCoordsForName(name)
    }

    private fun coordsFromHaltId(id: String?): Pair<Double, Double>? {
        if (id.isNullOrBlank()) return null
        val m = Regex("""@X=(\d+)@Y=(\d+)@""").find(id) ?: return null
        val lon = m.groupValues[1].toDoubleOrNull()?.div(1_000_000.0) ?: return null
        val lat = m.groupValues[2].toDoubleOrNull()?.div(1_000_000.0) ?: return null
        return lat to lon
    }

    private fun defaultCoordsForName(name: String): Pair<Double, Double>? = when {
        name.contains("Berlin Hbf", ignoreCase = true) -> 52.525 to 13.369
        name.contains("Hamburg Hbf", ignoreCase = true) -> 53.553 to 10.006
        name.contains("München Hbf", ignoreCase = true) || name.contains("Munich", ignoreCase = true) ->
            48.140 to 11.558
        name.contains("Köln Hbf", ignoreCase = true) || name.contains("Cologne", ignoreCase = true) ->
            50.943 to 6.958
        name.contains("Frankfurt", ignoreCase = true) -> 50.107 to 8.662
        else -> null
    }

    private fun lineNumber(lineName: String?): Int {
        if (lineName.isNullOrBlank()) return 0
        return Regex("""(\d{1,5})""").findAll(lineName).lastOrNull()?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun categoryFromLine(lineName: String?): String {
        val u = lineName?.uppercase().orEmpty()
        return when {
            u.startsWith("ICE") -> "ICE"
            u.startsWith("IC") || u.startsWith("EC") -> "IC"
            u.startsWith("RE") -> "RE"
            u.startsWith("RB") -> "RB"
            u.startsWith("S") -> "S"
            u.startsWith("U") -> "U"
            u.startsWith("BUS") -> "BUS"
            else -> "UNKNOWN"
        }
    }

    private fun isRegionalProduct(product: TransportProduct): Boolean =
        product !in setOf(TransportProduct.ICE, TransportProduct.IC_EC, TransportProduct.IR)

    private fun parseInstant(raw: String): Instant? = try {
        LocalDateTime.parse(raw.take(19), isoLocal).atZone(berlin).toInstant()
    } catch (_: Exception) {
        null
    }

    private fun minutesUntil(instant: Instant): Int =
        Duration.between(Instant.now(), instant).toMinutes().toInt()
}
