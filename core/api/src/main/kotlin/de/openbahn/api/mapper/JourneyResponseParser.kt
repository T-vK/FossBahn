package de.openbahn.api.mapper

import de.openbahn.api.DbApiBlockedException
import de.openbahn.api.DbApiException
import de.openbahn.api.DbParseException
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

        val items = extractVerbindungElements(root)
        return items.mapNotNull { mapVerbindung(it) }
    }

    private fun extractVerbindungElements(root: JsonObject): List<JsonElement> {
        root["verbindungen"]?.jsonArray?.let { top ->
            if (top.isNotEmpty()) return top.map(::flattenVerbindungElement)
        }
        root["journeys"]?.jsonArray?.let { journeys ->
            if (journeys.isNotEmpty()) return journeys.map(::flattenVerbindungElement)
        }

        val intervalKeys = listOf("intervalle", "tagesbestPreisIntervalle")
        for (key in intervalKeys) {
            val fromIntervals = root[key]?.jsonArray.orEmpty().flatMap { interval ->
                interval.jsonObject["verbindungen"]?.jsonArray.orEmpty()
            }.map(::flattenVerbindungElement)
            if (fromIntervals.isNotEmpty()) return fromIntervals
        }

        return root["verbindungen"]?.jsonArray?.map(::flattenVerbindungElement).orEmpty()
    }

    /** Matches db-vendo-client: spread `verbindung` wrapper into the connection object. */
    private fun flattenVerbindungElement(element: JsonElement): JsonElement {
        val obj = element.jsonObject
        val inner = obj["verbindung"]?.jsonObject ?: return element
        return buildJsonObject {
            inner.forEach { (key, value) -> put(key, value) }
            obj.forEach { (key, value) ->
                if (key != "verbindung") put(key, value)
            }
        }
    }

    private fun mapVerbindung(element: JsonElement): Journey? {
        val raw = element.jsonObject
        val v = raw["verbindung"]?.jsonObject ?: raw
        val abschnitte = v["verbindungsAbschnitte"]?.jsonArray
            ?: v["segmente"]?.jsonArray
            ?: return null
        val legs = abschnitte.mapNotNull { mapAbschnitt(it.jsonObject) }
        if (legs.isEmpty()) return null

        val duration = intVal(v, "verbindungsDauerInSeconds")?.div(60)
            ?: parseDurationMinutes(text(v, "reiseDauer"))
            ?: 60

        return Journey(
            id = text(v, "verbindungsId")
                ?: text(v, "tripId")
                ?: text(v, "id")
                ?: UUID.randomUUID().toString(),
            legs = legs,
            durationMinutes = duration,
            transfers = intVal(v, "umstiegsAnzahl")
                ?: intVal(v, "umstiege")
                ?: (legs.size - 1).coerceAtLeast(0),
            departure = text(v, "abfahrtsZeit") ?: legs.first().origin.scheduledTime,
            arrival = text(v, "ankunftsZeit") ?: legs.last().destination.scheduledTime,
            priceHint = priceHint(v),
            refreshToken = text(v, "ctxRecon") ?: text(v, "kontext"),
            deutschlandTicketValid = bool(v, "dticketGueltig"),
            remarks = mapHinweise(v["hinweise"]?.jsonArray),
        )
    }

    private fun mapAbschnitt(a: JsonObject): Leg? {
        val halte = a["halte"]?.jsonArray?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }.orEmpty()
        val firstHalt = halte.firstOrNull()
        val lastHalt = halte.lastOrNull()

        val depName = text(a, "abfahrtsOrt")
            ?: text(firstHalt, "name")
            ?: return null
        val arrName = text(a, "ankunftsOrt")
            ?: text(lastHalt, "name")
            ?: return null
        val depTime = text(a, "abfahrtsZeitpunkt")
            ?: text(firstHalt, "abfahrtsZeitpunkt")
            ?: text(firstHalt, "abgangsDatum")
            ?: return null
        val arrTime = text(a, "ankunftsZeitpunkt")
            ?: text(lastHalt, "ankunftsZeitpunkt")
            ?: text(lastHalt, "ankunftsDatum")
            ?: return null
        val vm = a["verkehrsmittel"]?.jsonObject
        return Leg(
            origin = StopEvent(
                name = depName,
                id = text(a, "abfahrtsOrtExtId") ?: text(firstHalt, "extId"),
                platform = text(a, "gleis") ?: text(firstHalt, "gleis"),
                scheduledTime = depTime,
            ),
            destination = StopEvent(
                name = arrName,
                id = text(a, "ankunftsOrtExtId") ?: text(lastHalt, "extId"),
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
            else -> null
        }
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
