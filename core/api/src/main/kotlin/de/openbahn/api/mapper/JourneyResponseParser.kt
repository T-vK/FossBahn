package de.openbahn.api.mapper

import de.openbahn.api.DbApiBlockedException
import de.openbahn.api.DbApiException
import de.openbahn.api.DbParseException
import de.openbahn.api.debug.OpenBahnDebugLog
import de.openbahn.api.debug.FahrplanDiagnostics
import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import de.openbahn.model.TransportProduct
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * Lenient parser for bahn.de `/angebote/fahrplan` responses.
 * Avoids strict DTO decoding so API schema drift (numeric platforms, nested `verbindung`, etc.)
 * does not break journey search.
 */
internal object JourneyResponseParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** Outer wrapper fields must not replace inner arrays with empty lists (common in live bahn.de responses). */
    private val mergePreserveNonEmptyArrayKeys = setOf(
        "verbindungsAbschnitte",
        "segmente",
        "halte",
    )

    fun parse(text: String): List<Journey> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw DbParseException("Empty response body")
        if (trimmed.contains("OPS_BLOCKED")) throw DbApiBlockedException("Journey search blocked")

        val root = try {
            json.parseToJsonElement(trimmed)
        } catch (e: Exception) {
            throw DbParseException("Invalid JSON from Deutsche Bahn", e)
        }

        return when (root) {
            is JsonObject -> parseRootObject(root)
            is JsonArray -> root.mapNotNull { mapVerbindung(it) }
            else -> throw DbParseException("Unexpected JSON root type")
        }
    }

    private fun parseRootObject(root: JsonObject): List<Journey> {
        root["fehlerNachricht"]?.jsonObject?.let { err ->
            val code = text(err, "code") ?: "API_ERROR"
            if (code == "OPS_BLOCKED") throw DbApiBlockedException("Journey search blocked")
            throw DbApiException(code)
        }

        if (text(root, "status") == "ERROR") {
            when (text(root, "code")) {
                "OPS_BLOCKED" -> throw DbApiBlockedException("Journey search blocked")
                null -> throw DbApiException("API_ERROR")
                else -> throw DbApiException(text(root, "code")!!)
            }
        }

        val (items, source) = extractVerbindungElements(root)
        val journeys = items.mapNotNull { mapVerbindung(it) }
        val skipped = items.size - journeys.size
        OpenBahnDebugLog.d(
            "JourneyParser",
            "${FahrplanDiagnostics.summarizeFahrplanRoot(root)} source=$source " +
                "rawConnections=${items.size} parsedJourneys=${journeys.size} skipped=$skipped",
        )
        if (items.isNotEmpty() && journeys.isEmpty()) {
            val sample = items.firstOrNull()?.jsonObject
            val merged = sample?.let { mergeConnectionObjects(it["verbindung"]?.jsonObject ?: it, it) }
            val abschnitte = merged?.let { abschnitteArray(it)?.size } ?: 0
            OpenBahnDebugLog.w(
                "JourneyParser",
                "API returned ${items.size} connection(s) but none mapped to journeys — " +
                    "sampleAbschnitte=$abschnitte keys=${merged?.keys?.take(12)?.joinToString()}",
            )
        }
        return journeys
    }

    private fun extractVerbindungElements(root: JsonObject): Pair<List<JsonElement>, String> {
        root["verbindungen"]?.jsonArray?.let { top ->
            if (top.isNotEmpty()) return top.map(::flattenVerbindungElement) to "verbindungen"
        }
        root["journeys"]?.jsonArray?.let { journeys ->
            if (journeys.isNotEmpty()) return journeys.map(::flattenVerbindungElement) to "journeys"
        }

        val intervalKeys = listOf("intervalle", "tagesbestPreisIntervalle")
        for (key in intervalKeys) {
            val fromIntervals = root[key]?.jsonArray.orEmpty().flatMap { interval ->
                interval.jsonObject["verbindungen"]?.jsonArray.orEmpty()
            }.map(::flattenVerbindungElement)
            if (fromIntervals.isNotEmpty()) return fromIntervals to key
        }

        return (root["verbindungen"]?.jsonArray?.map(::flattenVerbindungElement).orEmpty()) to "none"
    }

    /** Matches db-vendo-client: spread `verbindung` wrapper into the connection object. */
    private fun flattenVerbindungElement(element: JsonElement): JsonElement {
        val obj = element.jsonObject
        val inner = obj["verbindung"]?.jsonObject ?: return element
        return mergeConnectionObjects(inner, obj)
    }

    private fun mergeConnectionObjects(primary: JsonObject, overlay: JsonObject): JsonObject =
        buildJsonObject {
            primary.forEach { (key, value) -> put(key, value) }
            overlay.forEach { (key, value) ->
                if (key == "verbindung") return@forEach
                if (key in mergePreserveNonEmptyArrayKeys) {
                    val incoming = runCatching { value.jsonArray }.getOrNull()
                    val existing = primary[key]?.jsonArray
                    when {
                        incoming != null && incoming.isNotEmpty() -> put(key, incoming)
                        existing != null && existing.isNotEmpty() -> put(key, existing)
                        else -> put(key, value)
                    }
                } else {
                    put(key, value)
                }
            }
        }

    private fun mapVerbindung(element: JsonElement): Journey? {
        val raw = element.jsonObject
        val inner = raw["verbindung"]?.jsonObject
        val v = if (inner != null) mergeConnectionObjects(inner, raw) else raw
        val abschnitte = abschnitteArray(v)
        val legs = abschnitte?.mapNotNull { mapAbschnitt(it.jsonObject) }.orEmpty().ifEmpty {
            listOfNotNull(mapVerbindungSummaryLeg(v))
        }
        if (legs.isEmpty()) return null

        val duration = intVal(v, "verbindungsDauerInSeconds")?.div(60)
            ?: parseDurationMinutes(text(v, "reiseDauer"))
            ?: 60

        return buildJourney(v, legs, duration)
    }

    private fun abschnitteArray(v: JsonObject): JsonArray? =
        v["verbindungsAbschnitte"]?.jsonArray?.takeIf { it.isNotEmpty() }
            ?: v["segmente"]?.jsonArray?.takeIf { it.isNotEmpty() }
            ?: v["verbindungsAbschnitte"]?.jsonArray
            ?: v["segmente"]?.jsonArray

    /** Single-leg fallback when the API omits section arrays but provides trip-level times. */
    private fun mapVerbindungSummaryLeg(v: JsonObject): Leg? {
        val depName = text(v, "abfahrtsOrt") ?: text(v, "origin") ?: return null
        val arrName = text(v, "ankunftsOrt") ?: text(v, "destination") ?: return null
        val depTime = timeText(v, "abfahrtsZeit", "abfahrtsZeitpunkt") ?: return null
        val arrTime = timeText(v, "ankunftsZeit", "ankunftsZeitpunkt") ?: return null
        return Leg(
            origin = StopEvent(
                name = depName,
                id = text(v, "abfahrtsOrtExtId"),
                scheduledTime = depTime,
            ),
            destination = StopEvent(
                name = arrName,
                id = text(v, "ankunftsOrtExtId"),
                scheduledTime = arrTime,
            ),
            lineName = null,
            product = null,
            operator = null,
            loadFactor = null,
            bikeAllowed = null,
            tripId = text(v, "tripId"),
        )
    }

    private fun buildJourney(v: JsonObject, legs: List<Leg>, duration: Int): Journey =
        Journey(
            id = text(v, "verbindungsId")
                ?: text(v, "tripId")
                ?: text(v, "id")
                ?: UUID.randomUUID().toString(),
            legs = legs,
            durationMinutes = duration,
            transfers = intVal(v, "umstiegsAnzahl")
                ?: intVal(v, "umstiege")
                ?: (legs.size - 1).coerceAtLeast(0),
            departure = timeText(v, "abfahrtsZeit", "abfahrtsZeitpunkt") ?: legs.first().origin.scheduledTime,
            arrival = timeText(v, "ankunftsZeit", "ankunftsZeitpunkt") ?: legs.last().destination.scheduledTime,
            priceHint = priceHint(v),
            refreshToken = text(v, "ctxRecon") ?: text(v, "kontext"),
            deutschlandTicketValid = bool(v, "dticketGueltig"),
            remarks = mapHinweise(v["hinweise"]?.jsonArray),
        )

    private fun mapAbschnitt(a: JsonObject): Leg? {
        val halte = a["halte"]?.jsonArray?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }.orEmpty()
        val firstHalt = halte.firstOrNull()
        val lastHalt = halte.lastOrNull()

        val depName = stationName(a, "abfahrtsOrt", firstHalt) ?: return null
        val arrName = stationName(a, "ankunftsOrt", lastHalt) ?: return null
        val depTime = timeText(
            a,
            "abfahrtsZeitpunkt",
            "abgangsZeitpunkt",
            "abfahrtsZeit",
        ) ?: timeText(firstHalt, "abfahrtsZeitpunkt", "abgangsDatum", "abgangsZeitpunkt", "abfahrtsZeit")
            ?: return null
        val arrTime = timeText(
            a,
            "ankunftsZeitpunkt",
            "ankunftsDatum",
            "ankunftsZeit",
        ) ?: timeText(lastHalt, "ankunftsZeitpunkt", "ankunftsDatum", "ankunftsZeit")
            ?: return null
        val vm = a["verkehrsmittel"]?.jsonObject
        return Leg(
            origin = StopEvent(
                name = depName,
                id = stationExtId(a, "abfahrtsOrt", "abfahrtsOrtExtId", firstHalt),
                platform = text(a, "gleis") ?: text(firstHalt, "gleis"),
                scheduledTime = depTime,
            ),
            destination = StopEvent(
                name = arrName,
                id = stationExtId(a, "ankunftsOrt", "ankunftsOrtExtId", lastHalt),
                scheduledTime = arrTime,
            ),
            lineName = lineLabel(vm),
            product = text(vm, "produktGattung")?.let(::mapProduct),
            operator = text(vm, "betreiber"),
            loadFactor = text(vm, "auslastung"),
            bikeAllowed = bool(vm, "fahrradErlaubt"),
            tripId = text(a, "journeyId"),
        )
    }

    private fun stationName(section: JsonObject, ortKey: String, halt: JsonObject?): String? =
        text(section, ortKey)
            ?: section[ortKey]?.jsonObject?.let { text(it, "name") ?: text(it, "bezeichnung") }
            ?: text(halt, "name")
            ?: halt?.get("ort")?.jsonObject?.let { text(it, "name") }

    private fun stationExtId(
        section: JsonObject,
        ortKey: String,
        extIdKey: String,
        halt: JsonObject?,
    ): String? =
        text(section, extIdKey)
            ?: section[ortKey]?.jsonObject?.let { text(it, "extId") ?: text(it, "id") }
            ?: text(halt, "extId")

    private fun lineLabel(vm: JsonObject?): String? {
        if (vm == null) return null
        return text(vm, "name")
            ?: text(vm, "kurzText")
            ?: text(vm, "mittelText")
            ?: text(vm, "linienNummer")?.let { "Line $it" }
    }

    private fun mapProduct(code: String): TransportProduct? =
        TransportProduct.entries.find { it.vendoCode.equals(code, ignoreCase = true) }

    private fun mapHinweise(hinweise: JsonArray?): List<String> =
        hinweise.orEmpty().mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNull
                is JsonObject -> text(element, "text")
                    ?: text(element, "kurzText")
                    ?: text(element, "nachrichtKurz")
                else -> null
            }
        }

    private fun priceHint(v: JsonObject): String? {
        val preis = v["preis"]?.jsonObject ?: v["angebotsPreis"]?.jsonObject ?: return null
        text(preis, "text")?.let { return it }
        val amount = preis["betrag"]?.jsonPrimitive?.doubleOrNull
            ?: preis["preis"]?.jsonPrimitive?.doubleOrNull
        val currency = text(preis, "waehrung") ?: "EUR"
        return amount?.let { "$it $currency" }
    }

    private fun text(obj: JsonObject?, key: String): String? {
        if (obj == null) return null
        return when (val el = obj[key] ?: return null) {
            is JsonPrimitive -> el.contentOrNull
                ?: el.intOrNull?.toString()
                ?: el.doubleOrNull?.toString()
            is JsonObject -> text(el, "name")
                ?: text(el, "bezeichnung")
                ?: text(el, "label")
            else -> null
        }
    }

    private fun timeText(obj: JsonObject?, vararg keys: String): String? {
        if (obj == null) return null
        for (key in keys) {
            when (val el = obj[key] ?: continue) {
                is JsonPrimitive -> {
                    el.contentOrNull?.let { return it }
                    el.intOrNull?.toString()?.let { return it }
                }
                is JsonObject -> {
                    text(el, "zeitpunkt")?.let { return it }
                    text(el, "zeit")?.let { return it }
                    text(el, "value")?.let { return it }
                }
                else -> Unit
            }
        }
        return null
    }

    private fun bool(obj: JsonObject?, key: String): Boolean? {
        if (obj == null) return null
        val p = obj[key]?.jsonPrimitive ?: return null
        return p.booleanOrNull
            ?: p.contentOrNull?.toBooleanStrictOrNull()
    }

    private fun intVal(obj: JsonObject?, key: String): Int? {
        if (obj == null) return null
        val p = obj[key]?.jsonPrimitive ?: return null
        return p.intOrNull ?: p.contentOrNull?.toIntOrNull()
    }

    private fun parseDurationMinutes(isoDuration: String?): Int? {
        if (isoDuration == null) return null
        val match = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?").find(isoDuration) ?: return null
        val hours = match.groupValues[1].toIntOrNull() ?: 0
        val minutes = match.groupValues[2].toIntOrNull() ?: 0
        return hours * 60 + minutes
    }
}
