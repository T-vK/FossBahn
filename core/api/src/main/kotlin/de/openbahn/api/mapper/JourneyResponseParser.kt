package de.openbahn.api.mapper

import de.openbahn.api.DbApiBlockedException
import de.openbahn.api.DbApiException
import de.openbahn.api.DbParseException
import de.openbahn.api.debug.OpenBahnDebugLog
import de.openbahn.api.debug.FahrplanDiagnostics
import de.openbahn.model.Journey
import de.openbahn.model.JourneySearchResult
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
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

    private val berlinZone = ZoneId.of("Europe/Berlin")
    private val isoLocalFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    /** Parses a single connection from `/reiseloesung/verbindung` (refresh / live). */
    fun parseRefresh(response: JsonObject): Journey? {
        val element = when {
            response["verbindung"] != null -> response["verbindung"]!!
            response["verbindungen"]?.jsonArray?.isNotEmpty() == true ->
                response["verbindungen"]!!.jsonArray.first()
            abschnitteArray(response) != null -> response
            else -> null
        } ?: return null
        return mapVerbindung(element)
    }

    fun parse(text: String): JourneySearchResult {
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
            is JsonArray -> JourneySearchResult(journeys = root.mapNotNull { mapVerbindung(it) })
            else -> throw DbParseException("Unexpected JSON root type")
        }
    }

    private fun parseRootObject(root: JsonObject): JourneySearchResult {
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
            val flat = merged?.let { flattenAbschnitte(it) }.orEmpty()
            val topCount = merged?.let { abschnitteArray(it)?.size } ?: 0
            val sampleKeys = flat.firstOrNull()?.keys?.joinToString() ?: merged?.keys?.take(12)?.joinToString()
            OpenBahnDebugLog.w(
                "JourneyParser",
                "API returned ${items.size} connection(s) but none mapped to journeys — " +
                    "sampleTopAbschnitte=$topCount flattenedLegs=${flat.size} abschnittKeys=$sampleKeys",
            )
        }
        val (earlier, later) = extractPaging(root)
        return JourneySearchResult(
            journeys = journeys,
            pagingEarlier = earlier,
            pagingLater = later,
        )
    }

    private fun extractPaging(root: JsonObject): Pair<String?, String?> {
        root["verbindungReference"]?.jsonObject?.let { ref ->
            return text(ref, "earlier") to text(ref, "later")
        }
        val single = text(root, "pagingReference")
        return null to single
    }

    private data class ParsedStopTimes(
        val scheduled: String,
        val prognosed: String? = null,
        val delayMinutes: Int? = null,
    )

    /** Which bahn.de halt/section time fields apply (departure vs arrival vs either). */
    private enum class HaltTimeRole { DEPARTURE, ARRIVAL, EITHER }

    private fun parseStopTimes(obj: JsonObject?, role: HaltTimeRole = HaltTimeRole.EITHER): ParsedStopTimes? {
        if (obj == null) return null
        val scheduled = timeText(obj, *scheduledKeys(role)) ?: return null
        val prognosed = timeText(obj, *prognosedKeys(role))
            ?: timeText(obj["prognose"]?.jsonObject, *prognosedKeys(HaltTimeRole.EITHER))
        val delay = intVal(obj, "verspaetung", "verspätung", "delay", "verspaetungMinuten")
            ?: intVal(obj["prognose"]?.jsonObject, "verspaetung", "verspätung", "delay")
            ?: computeDelayMinutes(scheduled, prognosed)
        return ParsedStopTimes(scheduled, prognosed, delay)
    }

    /** Prefer the candidate with realtime delay data (bahn.de often puts delays on abfahrt/ankunft, not halte). */
    private fun bestStopTimes(vararg candidates: ParsedStopTimes?): ParsedStopTimes? =
        candidates.filterNotNull().maxWithOrNull(
            compareBy<ParsedStopTimes> { it.delayMinutes ?: 0 }
                .thenBy { if (it.prognosed != null && it.prognosed != it.scheduled) 1 else 0 },
        )

    private fun scheduledKeys(role: HaltTimeRole): Array<String> = when (role) {
        HaltTimeRole.DEPARTURE -> arrayOf(
            "sollzeit",
            "abfahrtsZeitpunkt",
            "abgangsZeitpunkt",
            "abgangsDatum",
            "abfahrtsZeit",
            "zeitpunkt",
            "zeit",
        )
        HaltTimeRole.ARRIVAL -> arrayOf(
            "sollzeit",
            "ankunftsZeitpunkt",
            "ankunftsDatum",
            "ankunftsZeit",
            "ankunft",
            "zeitpunkt",
            "zeit",
        )
        HaltTimeRole.EITHER -> arrayOf(
            "sollzeit",
            "abfahrtsZeitpunkt",
            "ankunftsZeitpunkt",
            "abgangsZeitpunkt",
            "ankunftsDatum",
            "zeitpunkt",
            "zeit",
        )
    }

    private fun prognosedKeys(role: HaltTimeRole): Array<String> = when (role) {
        HaltTimeRole.DEPARTURE -> arrayOf(
            "prognosezeit",
            "prognoseZeit",
            "istzeit",
            "istZeit",
            "ezZeit",
            "ezAbfahrtsZeitpunkt",
            "ezAbgangsZeitpunkt",
            "ezAbgangsDatum",
            "prognoseAbfahrtsZeitpunkt",
        )
        HaltTimeRole.ARRIVAL -> arrayOf(
            "prognosezeit",
            "prognoseZeit",
            "istzeit",
            "istZeit",
            "ezZeit",
            "ezAnkunftsZeitpunkt",
            "ezAnkunftsDatum",
            "prognoseAnkunftsZeitpunkt",
        )
        HaltTimeRole.EITHER -> arrayOf(
            "prognosezeit",
            "prognoseZeit",
            "istzeit",
            "istZeit",
            "ezZeit",
            "ezAbfahrtsZeitpunkt",
            "ezAnkunftsZeitpunkt",
            "ezAbgangsZeitpunkt",
            "ezAnkunftsDatum",
            "prognoseAbfahrtsZeitpunkt",
            "prognoseAnkunftsZeitpunkt",
        )
    }

    private fun computeDelayMinutes(scheduled: String, prognosed: String?): Int? {
        if (prognosed == null || prognosed == scheduled) return null
        val s = parseInstantMillis(scheduled) ?: return null
        val p = parseInstantMillis(prognosed) ?: return null
        val mins = ((p - s) / 60_000).toInt()
        return mins.takeIf { it > 0 }
    }

    private fun parseInstantMillis(iso: String): Long? {
        val trimmed = iso.trim()
        trimmed.toLongOrNull()?.let { raw ->
            return when {
                raw > 1_000_000_000_000L -> raw
                raw > 1_000_000_000L -> raw * 1000
                else -> null
            }
        }
        return try {
            Instant.parse(trimmed).toEpochMilli()
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(trimmed.take(19), isoLocalFormatter).atZone(berlinZone).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun stopEventFromParsed(
        name: String,
        id: String?,
        platform: String?,
        times: ParsedStopTimes,
    ) = StopEvent(
        name = name,
        id = id,
        platform = platform,
        scheduledTime = times.scheduled,
        prognosedTime = times.prognosed,
        delayMinutes = times.delayMinutes,
    )

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
        val legs = flattenAbschnitte(v).mapNotNull { mapAbschnitt(it) }.ifEmpty {
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

    /** Expand wrapper rows and nested `verbindungsAbschnitte` (live bahn.de often uses one outer row). */
    private fun flattenAbschnitte(connection: JsonObject): List<JsonObject> {
        val top = abschnitteArray(connection) ?: return emptyList()
        return top.flatMap { expandAbschnittElement(it) }
    }

    private fun expandAbschnittElement(element: JsonElement): List<JsonObject> {
        val obj = runCatching { element.jsonObject }.getOrNull() ?: return emptyList()
        val unwrapped = obj["verbindungsAbschnitt"]?.jsonObject
            ?: obj["abschnitt"]?.jsonObject
            ?: obj
        val nested = unwrapped["verbindungsAbschnitte"]?.jsonArray
            ?: unwrapped["segmente"]?.jsonArray
        if (nested != null && nested.isNotEmpty()) {
            return nested.flatMap { expandAbschnittElement(it) }
        }
        return listOf(unwrapped)
    }

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
            departure = legs.first().origin.prognosedTime ?: legs.first().origin.scheduledTime,
            arrival = legs.last().destination.prognosedTime ?: legs.last().destination.scheduledTime,
            priceHint = priceHint(v),
            refreshToken = text(v, "ctxRecon") ?: text(v, "kontext"),
            deutschlandTicketValid = bool(v, "dticketGueltig"),
            remarks = mapHinweise(v["hinweise"]?.jsonArray),
        )

    private fun mapAbschnitt(a: JsonObject): Leg? {
        val halte = halteArray(a)
        val firstHalt = halte.firstOrNull()
        val lastHalt = halte.lastOrNull()
        val startHalt = haltObject(a, "startHalt", "start", "abfahrtsHalt")
        val zielHalt = haltObject(a, "zielHalt", "ziel", "ankunftsHalt", "zielBahnhof")
        val vm = a["verkehrsmittel"]?.jsonObject

        val depName = stationName(a, "abfahrtsOrt", firstHalt)
            ?: stationName(a, "abgangsOrt", firstHalt)
            ?: stationNameFromHalt(startHalt)
            ?: return null
        val arrName = stationName(a, "ankunftsOrt", lastHalt)
            ?: stationName(a, "ankunftsBahnhof", lastHalt)
            ?: stationNameFromHalt(zielHalt)
            ?: return null
        val depTimes = sectionDepartureTimes(a, firstHalt, startHalt) ?: return null
        val arrTimes = sectionArrivalTimes(a, lastHalt, zielHalt) ?: return null
        val depPlatform = text(a, "gleis")
            ?: text(a, "abfahrtsGleis")
            ?: text(firstHalt, "gleis")
            ?: text(startHalt, "gleis")
        val arrPlatform = text(a, "ankunftsGleis")
            ?: text(a, "zielGleis")
            ?: text(lastHalt, "gleis")
            ?: text(zielHalt, "gleis")
        return Leg(
            origin = stopEventFromParsed(
                name = depName,
                id = stationExtId(a, "abfahrtsOrt", "abfahrtsOrtExtId", firstHalt)
                    ?: stationExtIdFromHalt(startHalt)
                    ?: text(firstHalt, "id"),
                platform = depPlatform,
                times = depTimes,
            ),
            destination = stopEventFromParsed(
                name = arrName,
                id = stationExtId(a, "ankunftsOrt", "ankunftsOrtExtId", lastHalt)
                    ?: stationExtIdFromHalt(zielHalt)
                    ?: text(lastHalt, "id"),
                platform = arrPlatform,
                times = arrTimes,
            ),
            intermediateStops = mapIntermediateStops(halte, depName, arrName),
            lineName = lineLabel(vm),
            product = text(vm, "produktGattung")?.let(::mapProduct),
            operator = text(vm, "betreiber"),
            loadFactor = text(vm, "auslastung"),
            bikeAllowed = bool(vm, "fahrradErlaubt"),
            tripId = text(a, "journeyId"),
        )
    }

    private fun mapIntermediateStops(
        halte: List<JsonObject>,
        depName: String,
        arrName: String,
    ): List<StopEvent> {
        if (halte.size <= 2) return emptyList()
        return halte.drop(1).dropLast(1).mapNotNull { halt ->
            val name = stationNameFromHalt(halt) ?: return@mapNotNull null
            if (name.equals(depName, ignoreCase = true) || name.equals(arrName, ignoreCase = true)) {
                return@mapNotNull null
            }
            run {
                val times = bestStopTimes(
                    parseStopTimes(halt, HaltTimeRole.DEPARTURE),
                    parseStopTimes(halt, HaltTimeRole.ARRIVAL),
                )
                    ?: timeText(halt, "abfahrtsZeitpunkt", "ankunftsZeitpunkt", "ankunftsZeit")
                        ?.let { ParsedStopTimes(scheduled = it) }
                    ?: return@mapNotNull null
                stopEventFromParsed(
                    name = name,
                    id = stationExtIdFromHalt(halt) ?: text(halt, "id"),
                    platform = text(halt, "gleis"),
                    times = times,
                )
            }
        }
    }

    private fun halteArray(section: JsonObject): List<JsonObject> =
        section["halte"]?.jsonArray?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            ?: section["halt"]?.jsonArray?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            ?: section["stops"]?.jsonArray?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            ?: emptyList()

    private fun sectionDepartureTimes(
        section: JsonObject,
        firstHalt: JsonObject?,
        startHalt: JsonObject?,
    ): ParsedStopTimes? = bestStopTimes(
        parseStopTimes(section["abfahrt"]?.jsonObject, HaltTimeRole.DEPARTURE),
        parseStopTimes(startHalt, HaltTimeRole.DEPARTURE),
        parseStopTimes(firstHalt, HaltTimeRole.DEPARTURE),
        timeText(section, "abfahrtsZeitpunkt", "abgangsZeitpunkt", "abfahrtsZeit", "abgangsZeit", "abfahrt")
            ?.let { scheduled ->
                ParsedStopTimes(
                    scheduled = scheduled,
                    prognosed = timeText(
                        section,
                        *prognosedKeys(HaltTimeRole.DEPARTURE),
                    ),
                    delayMinutes = intVal(section, "verspaetung", "verspätung", "delay"),
                )
            },
    )

    private fun sectionArrivalTimes(
        section: JsonObject,
        lastHalt: JsonObject?,
        zielHalt: JsonObject?,
    ): ParsedStopTimes? = bestStopTimes(
        parseStopTimes(section["ankunft"]?.jsonObject, HaltTimeRole.ARRIVAL),
        parseStopTimes(zielHalt, HaltTimeRole.ARRIVAL),
        parseStopTimes(lastHalt, HaltTimeRole.ARRIVAL),
        timeText(section, "ankunftsZeitpunkt", "ankunftsDatum", "ankunftsZeit", "ankunft")
            ?.let { scheduled ->
                ParsedStopTimes(
                    scheduled = scheduled,
                    prognosed = timeText(
                        section,
                        *prognosedKeys(HaltTimeRole.ARRIVAL),
                    ),
                    delayMinutes = intVal(section, "verspaetung", "verspätung", "delay"),
                )
            },
    )

    private fun haltObject(section: JsonObject, vararg keys: String): JsonObject? {
        for (key in keys) {
            section[key]?.jsonObject?.let { return it }
        }
        return null
    }

    private fun stationNameFromHalt(halt: JsonObject?): String? {
        if (halt == null) return null
        return text(halt, "name")
            ?: text(halt, "bezeichnung")
            ?: text(halt, "bahnhofsName")
            ?: nameFromHaltId(text(halt, "id"))
    }

    private fun stationExtIdFromHalt(halt: JsonObject?): String? {
        if (halt == null) return null
        return text(halt, "extId")
            ?: text(halt, "evaNr")
            ?: text(halt, "id")?.takeIf { it.all(Char::isDigit) }
    }

    private fun stationName(section: JsonObject, ortKey: String, halt: JsonObject?): String? =
        text(section, ortKey)
            ?: section[ortKey]?.jsonObject?.let { text(it, "name") ?: text(it, "bezeichnung") }
            ?: text(halt, "name")
            ?: text(halt, "bezeichnung")
            ?: halt?.get("ort")?.jsonObject?.let { text(it, "name") }
            ?: nameFromHaltId(text(halt, "id"))
            ?: nameFromHaltId(text(section, ortKey))
            ?: text(section, if (ortKey == "abfahrtsOrt") "abfahrtsOrtExtId" else "ankunftsOrtExtId")
                ?.let { eva -> if (eva.length >= 6) "Station $eva" else null }

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
            is JsonPrimitive -> {
                if (el.booleanOrNull != null) return null
                el.contentOrNull
                    ?: el.intOrNull?.toString()
                    ?: el.doubleOrNull?.toString()
            }
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
                is JsonPrimitive -> parseTimePrimitive(el)?.let { return it }
                is JsonObject ->
                    timeText(
                        el,
                        "zeitpunkt",
                        "zeit",
                        "sollzeit",
                        "istzeit",
                        "prognosezeit",
                        "abfahrtsZeitpunkt",
                        "ankunftsZeitpunkt",
                        "abfahrtszeit",
                        "ankunftszeit",
                        "abgangsZeitpunkt",
                        "dateTime",
                        "value",
                    )?.let { return it }
                else -> Unit
            }
        }
        return null
    }

    private fun parseTimePrimitive(p: JsonPrimitive): String? {
        p.contentOrNull?.let { raw ->
            if (raw.contains('T')) return raw
            raw.toLongOrNull()?.let { return formatEpochMillis(it) }
        }
        val millis = p.longOrNull ?: p.intOrNull?.toLong()
        if (millis != null && millis > 1_000_000_000_000L) return formatEpochMillis(millis)
        return p.intOrNull?.toString()
    }

    private fun formatEpochMillis(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(berlinZone).format(isoLocalFormatter)

    /** bahn.de halt id: `A=1@O=Hamburg Hbf@X=...` */
    private fun nameFromHaltId(haltId: String?): String? {
        if (haltId.isNullOrBlank()) return null
        val match = Regex("""@O=([^@]+)@""").find(haltId) ?: return null
        return match.groupValues[1].replace('+', ' ')
    }

    private fun bool(obj: JsonObject?, key: String): Boolean? {
        if (obj == null) return null
        val p = obj[key]?.jsonPrimitive ?: return null
        return p.booleanOrNull
            ?: p.contentOrNull?.toBooleanStrictOrNull()
    }

    private fun intVal(obj: JsonObject?, vararg keys: String): Int? {
        if (obj == null) return null
        for (key in keys) {
            val p = obj[key]?.jsonPrimitive ?: continue
            p.intOrNull?.let { return it }
            p.doubleOrNull?.toInt()?.let { return it }
            p.contentOrNull?.let { raw ->
                raw.toIntOrNull()?.let { return it }
                Regex("""\+?(\d+)""").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun parseDurationMinutes(isoDuration: String?): Int? {
        if (isoDuration == null) return null
        val match = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?").find(isoDuration) ?: return null
        val hours = match.groupValues[1].toIntOrNull() ?: 0
        val minutes = match.groupValues[2].toIntOrNull() ?: 0
        return hours * 60 + minutes
    }
}
