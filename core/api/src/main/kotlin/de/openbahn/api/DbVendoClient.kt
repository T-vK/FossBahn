package de.openbahn.api

import de.openbahn.api.dto.DbJourneyResponse
import de.openbahn.api.dto.DbLocationResponse
import de.openbahn.api.dto.DbOrt
import de.openbahn.api.dto.DbStationBoardResponse
import de.openbahn.api.debug.FahrplanDiagnostics
import de.openbahn.api.debug.OpenBahnDebugLog
import de.openbahn.api.mapper.JourneyMapper
import de.openbahn.api.mapper.JourneyResponseParser
import de.openbahn.api.mapper.LocationMapper
import de.openbahn.api.mapper.StationBoardMapper
import de.openbahn.api.mapper.JourneyBoardMatcher
import de.openbahn.model.BoardEntry
import de.openbahn.model.Journey
import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.JourneySearchResult
import de.openbahn.model.Leg
import de.openbahn.model.Location
import de.openbahn.model.StopEvent
import de.openbahn.model.StationBoard
import de.openbahn.model.TransportProduct
import de.openbahn.model.boardLocation
import de.openbahn.model.withBoardRealtime
import de.openbahn.model.withRealtimeFrom
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerializationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Kotlin client for the public bahn.de vendo API (no API key required).
 * Based on https://github.com/public-transport/db-vendo-client dbweb profile.
 */
class DbVendoClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: HttpClient = createDefaultClient(),
) {
    private val tripRouteFetcher = TripRouteFetcher(this)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun searchLocations(query: String, locale: String = "de"): List<Location> {
        OpenBahnDebugLog.d("DbVendo", "searchLocations q=\"$query\" locale=$locale")
        val locations = try {
            val text = readApiText(
                httpClient.get("$baseUrl/reiseloesung/orte") {
                    parameter("suchbegriff", query)
                    parameter("typ", "ALL")
                    parameter("max", 12)
                    parameter("locale", locale)
                },
                label = "orte",
            )
            parseLocationResponse(text)
        } catch (e: DbApiBlockedException) {
            OpenBahnDebugLog.w("DbVendo", "web /orte blocked, trying mobile location search")
            searchLocationsMobile(query)
        }
        OpenBahnDebugLog.d(
            "DbVendo",
            "searchLocations q=\"$query\" -> ${locations.size} hit(s): " +
                locations.take(4).joinToString { FahrplanDiagnostics.describeLocation(it) },
        )
        return locations
    }

    /** Stops and stations near a coordinate (bahn.de `/reiseloesung/orte/nearby`). */
    suspend fun searchLocationsNearby(
        latitude: Double,
        longitude: Double,
        locale: String = "de",
        maxResults: Int = 12,
    ): List<Location> {
        OpenBahnDebugLog.d("DbVendo", "searchLocationsNearby lat=$latitude lon=$longitude")
        val text = readApiText(
            httpClient.get("$baseUrl/reiseloesung/orte/nearby") {
                parameter("lat", latitude)
                parameter("long", longitude)
                parameter("maxNo", maxResults)
                parameter("locale", locale)
            },
            label = "orte/nearby",
        )
        val locations = parseLocationResponse(text)
        OpenBahnDebugLog.d("DbVendo", "searchLocationsNearby -> ${locations.size} hit(s)")
        return locations
    }

    suspend fun searchJourneys(
        from: Location,
        to: Location,
        options: JourneySearchOptions = JourneySearchOptions(),
        whenTime: LocalDateTime = LocalDateTime.now(),
        pagingReference: String? = null,
    ): JourneySearchResult {
        val normalizedWhen = whenTime.withNano(0)
        val apiWhen = JourneySearchTime.forApiRequest(whenTime, options.arrivalSearch)
        var raw = postFahrplan(from, to, options, apiWhen, pagingReference)
        var result = parseJourneyResponse(raw)
        // The empty-response retry only exists to recover a departure time we had to nudge out
        // of the past. It must never override a time the user explicitly chose (a future
        // departure or any arrival time): re-querying at "now + 3 min" made every empty result
        // look like a search for the current time instead of the selected one.
        val nudgedPastDeparture = !options.arrivalSearch && apiWhen != normalizedWhen
        if (pagingReference == null &&
            nudgedPastDeparture &&
            result.journeys.isEmpty() &&
            FahrplanDiagnostics.isEmptySuccessBody(raw)
        ) {
            val retryWhen = JourneySearchTime.nowBerlin().plusMinutes(3)
            OpenBahnDebugLog.w(
                "DbVendo",
                "empty fahrplan for ${from.name} -> ${to.name} at $apiWhen (body=${raw.trim()}), retry at $retryWhen",
            )
            raw = postFahrplan(from, to, options, retryWhen, null)
            result = parseJourneyResponse(raw)
        }
        OpenBahnDebugLog.d(
            "DbVendo",
            "searchJourneys ${from.name} -> ${to.name} at $whenTime -> ${result.journeys.size} journey(s) " +
                "earlier=${result.pagingEarlier != null} later=${result.pagingLater != null}",
        )
        return result
    }

    /** Raw POST /angebote/fahrplan body (for live integration diagnostics). */
    internal suspend fun postFahrplan(
        from: Location,
        to: Location,
        options: JourneySearchOptions,
        whenTime: LocalDateTime,
        pagingReference: String? = null,
    ): String {
        val body = JourneyRequestBuilder.build(from, to, options, whenTime, pagingReference)
        val bodyText = json.encodeToString(JsonObject.serializer(), body)
        val anfrageZeitpunkt = body["anfrageZeitpunkt"]?.jsonPrimitive?.contentOrNull
        OpenBahnDebugLog.d(
            "DbVendo",
            "postFahrplan ${FahrplanDiagnostics.describeLocation(from)} -> " +
                "${FahrplanDiagnostics.describeLocation(to)} anfrageZeitpunkt=$anfrageZeitpunkt",
        )
        return try {
            postWebFahrplan(bodyText)
        } catch (e: DbApiBlockedException) {
            OpenBahnDebugLog.w("DbVendo", "web /fahrplan blocked, trying mobile API")
            postMobileFahrplan(from, to, options, whenTime, pagingReference)
        }
    }

    private suspend fun postWebFahrplan(bodyText: String): String {
        return try {
            val response = httpClient.post("$baseUrl/angebote/fahrplan") {
                contentType(ContentType.Application.Json)
                setBody(TextContent(bodyText, ContentType.Application.Json))
            }
            val text = readApiText(response, label = "fahrplan")
            OpenBahnDebugLog.d("DbVendo", "postFahrplan response ${FahrplanDiagnostics.summarizeFahrplanBody(text)}")
            text
        } catch (e: ClientRequestException) {
            val errorBody = runCatching { e.response.bodyAsText() }.getOrDefault("")
            OpenBahnDebugLog.w(
                "DbVendo",
                "postFahrplan HTTP ${e.response.status.value} ${FahrplanDiagnostics.summarizeFahrplanBody(errorBody)}",
            )
            rejectBlockedApiBody(errorBody.trim(), "Journey search blocked")
            throw DbApiException("HTTP_${e.response.status.value}")
        } catch (e: ServerResponseException) {
            OpenBahnDebugLog.w("DbVendo", "postFahrplan HTTP ${e.response.status.value}")
            throw DbApiException("HTTP_${e.response.status.value}")
        }
    }

    private suspend fun postMobileFahrplan(
        from: Location,
        to: Location,
        options: JourneySearchOptions,
        whenTime: LocalDateTime,
        pagingReference: String?,
    ): String {
        val body = JourneyRequestBuilder.buildMobile(from, to, options, whenTime, pagingReference)
        val bodyText = json.encodeToString(JsonObject.serializer(), body)
        val contentType = ContentType.parse(MOBILE_JOURNEY_CONTENT_TYPE)
        return try {
            val response = httpClient.post("$MOBILE_BASE_URL/angebote/fahrplan") {
                header("Accept", MOBILE_JOURNEY_CONTENT_TYPE)
                header("Content-Type", MOBILE_JOURNEY_CONTENT_TYPE)
                header("X-Correlation-ID", mobileCorrelationId())
                setBody(TextContent(bodyText, contentType))
            }
            val text = readApiText(response, label = "mobile-fahrplan")
            OpenBahnDebugLog.d(
                "DbVendo",
                "postMobileFahrplan response ${FahrplanDiagnostics.summarizeFahrplanBody(text)}",
            )
            text
        } catch (e: ClientRequestException) {
            val errorBody = runCatching { e.response.bodyAsText() }.getOrDefault("")
            OpenBahnDebugLog.w(
                "DbVendo",
                "postMobileFahrplan HTTP ${e.response.status.value} " +
                    FahrplanDiagnostics.summarizeFahrplanBody(errorBody),
            )
            rejectBlockedApiBody(errorBody.trim(), "Journey search blocked")
            throw DbApiException("HTTP_${e.response.status.value}")
        } catch (e: ServerResponseException) {
            OpenBahnDebugLog.w("DbVendo", "postMobileFahrplan HTTP ${e.response.status.value}")
            throw DbApiException("HTTP_${e.response.status.value}")
        }
    }

    private suspend fun searchLocationsMobile(query: String): List<Location> {
        val body = buildJsonObject {
            putJsonArray("locationTypes") { add("ALL") }
            put("searchTerm", query)
            put("maxResults", 12)
        }
        val bodyText = json.encodeToString(JsonObject.serializer(), body)
        val contentType = ContentType.parse(MOBILE_LOCATION_CONTENT_TYPE)
        return try {
            val response = httpClient.post("$MOBILE_BASE_URL/location/search") {
                header("Accept", MOBILE_LOCATION_CONTENT_TYPE)
                header("Content-Type", MOBILE_LOCATION_CONTENT_TYPE)
                header("X-Correlation-ID", mobileCorrelationId())
                setBody(TextContent(bodyText, contentType))
            }
            val text = readApiText(response, label = "mobile-location")
            parseLocationResponse(text)
        } catch (e: ClientRequestException) {
            val errorBody = runCatching { e.response.bodyAsText() }.getOrDefault("")
            rejectBlockedApiBody(errorBody.trim(), "Location search blocked")
            throw DbApiException("HTTP_${e.response.status.value}")
        } catch (e: ServerResponseException) {
            throw DbApiException("HTTP_${e.response.status.value}")
        }
    }

    private fun parseJourneyResponse(text: String): JourneySearchResult =
        try {
            JourneyResponseParser.parse(text)
        } catch (e: DbApiBlockedException) {
            throw e
        } catch (e: DbApiException) {
            throw e
        } catch (e: DbParseException) {
            throw e
        } catch (e: SerializationException) {
            throw DbParseException(cause = e)
        }

    /**
     * Reads an API response body. Akamai often returns HTTP 403 (or even 200) with an HTML
     * "Access Denied" page — that must become [DbApiBlockedException], never a parse crash.
     */
    private suspend fun readApiText(response: HttpResponse, label: String): String {
        val status = response.status.value
        val text = response.bodyAsText()
        OpenBahnDebugLog.d("DbVendo", "$label HTTP $status bodyBytes=${text.length}")
        val trimmed = text.trim()
        if (status == 403 || status == 452 || status == 429) {
            rejectBlockedApiBody(trimmed, "$label blocked (HTTP $status)")
            throw DbApiException("HTTP_$status")
        }
        rejectBlockedApiBody(trimmed, "$label blocked")
        if (status !in 200..299) {
            throw DbApiException("HTTP_$status")
        }
        return text
    }

    /**
     * Parses `/orte` (and mobile location) payloads. WAF/CDN blocks often return HTML
     * ("Access Denied"); that must become [DbApiBlockedException] — never an uncaught
     * [SerializationException] (which previously crashed the Search screen).
     */
    private fun parseLocationResponse(text: String): List<Location> {
        val trimmed = text.trim()
        rejectBlockedApiBody(trimmed, "Location search blocked")
        return try {
            val asList = json.decodeFromString<List<DbOrt>>(trimmed)
            LocationMapper.mapOrtList(asList)
        } catch (first: Exception) {
            try {
                val wrapped = json.decodeFromString<DbLocationResponse>(trimmed)
                LocationMapper.mapLocations(wrapped)
            } catch (e: SerializationException) {
                throw DbParseException("Invalid location JSON from Deutsche Bahn", e)
            } catch (e: Exception) {
                throw DbParseException("Failed to parse location response", first)
            }
        }
    }

    /** Reject OPS_BLOCKED JSON and HTML denial pages before attempting JSON decode. */
    private fun rejectBlockedApiBody(text: String, blockedMessage: String) {
        if (text.contains("OPS_BLOCKED")) throw DbApiBlockedException(blockedMessage)
        // e.g. "<HTML><HEAD>\n<TITLE>Access Denied..."
        if (text.startsWith("<") || text.contains("<html", ignoreCase = true)) {
            throw DbApiBlockedException(blockedMessage)
        }
    }

    private fun mobileCorrelationId(): String =
        "${UUID.randomUUID()}_${UUID.randomUUID()}"

    /** All stops on the vehicle run (before/after your segment), via bahn.de trip id. */
    suspend fun fetchTripRoute(journeyId: String): List<StopEvent> {
        if (journeyId.isBlank()) return emptyList()
        val text = httpClient.get("$baseUrl/reiseloesung/fahrt") {
            parameter("journeyId", journeyId)
        }.bodyAsText()
        if (text.contains("OPS_BLOCKED")) throw DbApiBlockedException("Trip route blocked")
        val stops = JourneyResponseParser.parseFahrtRoute(text)
        if (stops.isEmpty()) {
            OpenBahnDebugLog.w(
                "DbVendo",
                "fahrt returned 0 stops for journeyId=${journeyId.take(72)} body=${text.take(160)}",
            )
        } else {
            OpenBahnDebugLog.d("DbVendo", "fahrt ${stops.size} stops for journeyId=${journeyId.take(48)}")
        }
        return stops
    }

    /** Full vehicle route: `/fahrt` plus board `mitVias` fallback when needed. */
    suspend fun fetchFullLegRoute(leg: Leg): List<StopEvent> = tripRouteFetcher.fetchFullLegRoute(leg)

    internal suspend fun fetchBoardRaw(
        stationExtId: String,
        whenTime: LocalDateTime,
        departures: Boolean,
        mitVias: Boolean,
        locale: String = "de",
        durationMinutes: Int = BOARD_LOOKUP_MINUTES,
    ): String {
        val path = if (departures) "abfahrten" else "ankuenfte"
        return httpClient.get("$baseUrl/reiseloesung/$path") {
            parameter("ortExtId", stationExtId)
            parameter("datum", whenTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
            parameter("zeit", whenTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
            parameter("dauer", durationMinutes)
            if (mitVias) parameter("mitVias", true)
            parameter("locale", locale)
        }.bodyAsText()
    }

    suspend fun refreshJourney(refreshToken: String): Journey? {
        val body = buildJsonObject { put("ctxRecon", refreshToken); put("poly", true) }
        val text = httpClient.post("$baseUrl/reiseloesung/verbindung") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.bodyAsText()
        if (text.contains("OPS_BLOCKED")) throw DbApiBlockedException("Journey refresh blocked")
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        return JourneyMapper.mapRefresh(root)
    }

    /**
     * Loads realtime delays: refresh via ctxRecon, then station boards (abfahrten/ankünfte with ezZeit).
     * Search/refresh JSON often omits verspaetung; boards are the reliable source for near-term legs.
     */
    suspend fun enrichJourneysWithRealtime(
        journeys: List<Journey>,
        from: Location? = null,
        to: Location? = null,
    ): List<Journey> {
        if (journeys.isEmpty()) return journeys
        return coroutineScope {
            val semaphore = Semaphore(MAX_CONCURRENT_JOURNEY_REFRESH)
            val departureCache = mutableMapOf<String, List<BoardEntry>>()
            val arrivalCache = mutableMapOf<String, List<BoardEntry>>()
            journeys.map { journey ->
                async {
                    val afterRefresh = journey.refreshToken?.takeIf { it.isNotBlank() }?.let { token ->
                        semaphore.withPermit {
                            runCatching { refreshJourney(token) }.getOrNull()
                        }
                    }?.let { refreshed -> journey.withRealtimeFrom(refreshed) } ?: journey

                    enrichDelaysFromStationBoards(afterRefresh, from, to, departureCache, arrivalCache)
                }
            }.map { it.await() }
        }
    }

    private suspend fun enrichDelaysFromStationBoards(
        journey: Journey,
        from: Location?,
        to: Location?,
        departureCache: MutableMap<String, List<BoardEntry>>,
        arrivalCache: MutableMap<String, List<BoardEntry>>,
    ): Journey {
        var result = journey
        val railLegIndices = result.legs.indices.filter { !result.legs[it].isWalking }
        if (railLegIndices.isEmpty()) return result

        for (legIndex in railLegIndices) {
            val leg = result.legs[legIndex]
            val isFirstRail = legIndex == railLegIndices.first()
            val isLastRail = legIndex == railLegIndices.last()

            val depStation = if (isFirstRail && from != null) from else leg.origin.boardLocation()
            depStation?.let { station ->
                parseLocalDateTime(leg.origin.scheduledTime)?.let { whenTime ->
                    result = enrichLegOriginFromDepartures(
                        journey = result,
                        legIndex = legIndex,
                        station = station,
                        whenTime = whenTime,
                        departureCache = departureCache,
                    )
                }
            }

            val currentLeg = result.legs[legIndex]
            val arrStation = if (isLastRail && to != null) to else currentLeg.destination.boardLocation()
            arrStation?.let { station ->
                parseLocalDateTime(currentLeg.destination.scheduledTime)?.let { whenTime ->
                    result = enrichLegDestinationFromArrivals(
                        journey = result,
                        legIndex = legIndex,
                        station = station,
                        whenTime = whenTime,
                        arrivalCache = arrivalCache,
                    )
                }
            }

            result = enrichIntermediateStopsFromBoards(
                journey = result,
                legIndex = legIndex,
                departureCache = departureCache,
                arrivalCache = arrivalCache,
            )
        }
        return result
    }

    private suspend fun enrichLegOriginFromDepartures(
        journey: Journey,
        legIndex: Int,
        station: Location,
        whenTime: LocalDateTime,
        departureCache: MutableMap<String, List<BoardEntry>>,
    ): Journey {
        val leg = journey.legs[legIndex]
        val cacheKey = boardCacheKey(station, whenTime)
        val entries = departureCache.getOrPut(cacheKey) {
            runCatching { departures(station, whenTime, durationMinutes = BOARD_LOOKUP_MINUTES) }
                .getOrDefault(emptyList())
        }
        val match = JourneyBoardMatcher.findDepartureMatch(entries, leg) ?: return journey
        val origin = leg.origin.withBoardRealtime(
            match.scheduledTime,
            match.prognosedTime,
            JourneyBoardMatcher.boardDelayMinutes(match),
            platform = match.platform,
        )
        if (origin == leg.origin) return journey
        return journey.copy(
            legs = journey.legs.mapIndexed { i, l -> if (i == legIndex) l.copy(origin = origin) else l },
            departure = if (legIndex == 0) origin.prognosedTime ?: origin.scheduledTime else journey.departure,
        )
    }

    private suspend fun enrichLegDestinationFromArrivals(
        journey: Journey,
        legIndex: Int,
        station: Location,
        whenTime: LocalDateTime,
        arrivalCache: MutableMap<String, List<BoardEntry>>,
    ): Journey {
        val leg = journey.legs[legIndex]
        val cacheKey = boardCacheKey(station, whenTime)
        val entries = arrivalCache.getOrPut(cacheKey) {
            runCatching { arrivals(station, whenTime, durationMinutes = BOARD_LOOKUP_MINUTES) }
                .getOrDefault(emptyList())
        }
        val match = JourneyBoardMatcher.findArrivalMatch(entries, leg) ?: return journey
        val destination = leg.destination.withBoardRealtime(
            match.scheduledTime,
            match.prognosedTime,
            JourneyBoardMatcher.boardDelayMinutes(match),
            platform = match.platform,
        )
        if (destination == leg.destination) return journey
        return journey.copy(
            legs = journey.legs.mapIndexed { i, l ->
                if (i == legIndex) l.copy(destination = destination) else l
            },
            arrival = if (legIndex == journey.legs.lastIndex) {
                destination.prognosedTime ?: destination.scheduledTime
            } else {
                journey.arrival
            },
        )
    }

    private suspend fun enrichIntermediateStopsFromBoards(
        journey: Journey,
        legIndex: Int,
        departureCache: MutableMap<String, List<BoardEntry>>,
        arrivalCache: MutableMap<String, List<BoardEntry>>,
    ): Journey {
        val leg = journey.legs[legIndex]
        if (leg.intermediateStops.isEmpty()) return journey
        val updatedStops = leg.intermediateStops.map { stop ->
            enrichIntermediateStopFromBoards(stop, leg, departureCache, arrivalCache)
        }
        if (updatedStops == leg.intermediateStops) return journey
        return journey.copy(
            legs = journey.legs.mapIndexed { i, l ->
                if (i == legIndex) l.copy(intermediateStops = updatedStops) else l
            },
        )
    }

    private suspend fun enrichIntermediateStopFromBoards(
        stop: StopEvent,
        leg: Leg,
        departureCache: MutableMap<String, List<BoardEntry>>,
        arrivalCache: MutableMap<String, List<BoardEntry>>,
    ): StopEvent {
        val station = stop.boardLocation() ?: return stop
        val whenTime = parseLocalDateTime(stop.scheduledTime) ?: return stop
        var updated = stop

        val arrivalEntries = arrivalCache.getOrPut(boardCacheKey(station, whenTime)) {
            runCatching { arrivals(station, whenTime, durationMinutes = BOARD_LOOKUP_MINUTES) }
                .getOrDefault(emptyList())
        }
        JourneyBoardMatcher.findArrivalMatch(arrivalEntries, leg.copy(destination = stop))?.let { match ->
            updated = updated.withBoardRealtime(
                match.scheduledTime,
                match.prognosedTime,
                JourneyBoardMatcher.boardDelayMinutes(match),
                platform = match.platform,
            )
        }

        val departureEntries = departureCache.getOrPut(boardCacheKey(station, whenTime)) {
            runCatching { departures(station, whenTime, durationMinutes = BOARD_LOOKUP_MINUTES) }
                .getOrDefault(emptyList())
        }
        JourneyBoardMatcher.findDepartureMatch(departureEntries, leg.copy(origin = stop))?.let { match ->
            updated = updated.withBoardRealtime(
                match.scheduledTime,
                match.prognosedTime,
                JourneyBoardMatcher.boardDelayMinutes(match),
                platform = match.platform,
            )
        }
        return updated
    }

    private fun boardCacheKey(station: Location, whenTime: LocalDateTime): String =
        "${station.evaNumber ?: station.id}|${whenTime.toLocalDate()}|${whenTime.hour}"

    private fun parseLocalDateTime(iso: String): LocalDateTime? = runCatching {
        LocalDateTime.parse(iso.take(19), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
    }.getOrNull()

    suspend fun departures(
        station: Location,
        whenTime: LocalDateTime = LocalDateTime.now(),
        durationMinutes: Int = 60,
        products: Set<TransportProduct> = TransportProduct.ALL,
        locale: String = "de",
    ): List<BoardEntry> {
        val ortExtId = station.evaNumber ?: station.id
        val text = readApiText(
            httpClient.get("$baseUrl/reiseloesung/abfahrten") {
                parameter("ortExtId", ortExtId)
                parameter("datum", whenTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                parameter("zeit", whenTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                parameter("dauer", durationMinutes)
                products.forEach { parameter("verkehrsmittel[]", it.vendoCode) }
                parameter("locale", locale)
            },
            label = "abfahrten",
        )
        val response = try {
            json.decodeFromString<DbStationBoardResponse>(text)
        } catch (e: SerializationException) {
            throw DbParseException("Invalid departures JSON from Deutsche Bahn", e)
        }
        return StationBoardMapper.mapDepartures(response)
    }

    suspend fun arrivals(
        station: Location,
        whenTime: LocalDateTime = LocalDateTime.now(),
        durationMinutes: Int = 60,
        products: Set<TransportProduct> = TransportProduct.ALL,
        locale: String = "de",
    ): List<BoardEntry> {
        val ortExtId = station.evaNumber ?: station.id
        val text = readApiText(
            httpClient.get("$baseUrl/reiseloesung/ankuenfte") {
                parameter("ortExtId", ortExtId)
                parameter("datum", whenTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                parameter("zeit", whenTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                parameter("dauer", durationMinutes)
                products.forEach { parameter("verkehrsmittel[]", it.vendoCode) }
                parameter("locale", locale)
            },
            label = "ankuenfte",
        )
        val response = try {
            json.decodeFromString<DbStationBoardResponse>(text)
        } catch (e: SerializationException) {
            throw DbParseException("Invalid arrivals JSON from Deutsche Bahn", e)
        }
        return StationBoardMapper.mapArrivals(response)
    }

    suspend fun stationBoard(
        station: Location,
        whenTime: LocalDateTime = LocalDateTime.now(),
        products: Set<TransportProduct> = TransportProduct.ALL,
        locale: String = "de",
    ): StationBoard = StationBoard(
        stationName = station.name,
        stationId = station.id,
        departures = departures(station, whenTime, products = products, locale = locale),
        arrivals = arrivals(station, whenTime, products = products, locale = locale),
    )

    fun close() = httpClient.close()

    companion object {
        private const val MAX_CONCURRENT_JOURNEY_REFRESH = 4
        private const val BOARD_LOOKUP_MINUTES = 180

        const val DEFAULT_BASE_URL = "https://int.bahn.de/web/api"
        const val MOBILE_BASE_URL = "https://app.services-bahn.de/mob"
        private const val MOBILE_JOURNEY_CONTENT_TYPE =
            "application/x.db.vendo.mob.verbindungssuche.v9+json"
        private const val MOBILE_LOCATION_CONTENT_TYPE =
            "application/x.db.vendo.mob.location.v3+json"

        fun createDefaultClient(): HttpClient = HttpClient(OkHttp) {
            // expectSuccess=false so we can map Akamai HTML 403 bodies to DbApiBlockedException.
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 20_000
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            defaultRequest {
                headers.append("Accept", "application/json")
                headers.append("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
                headers.append("Origin", "https://www.bahn.de")
                headers.append("Referer", "https://www.bahn.de/")
                // Identify as this FOSS app — do not impersonate DB Navigator or a browser.
                headers.append(
                    "User-Agent",
                    "OpenBahnNavigator/1.0 (FOSS; +https://github.com/T-vK/FossBahn)",
                )
            }
            engine {
                preconfigured = BahnOkHttp.createClient()
            }
        }
    }
}
