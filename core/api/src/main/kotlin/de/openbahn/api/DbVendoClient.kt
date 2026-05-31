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
import de.openbahn.model.Location
import de.openbahn.model.StationBoard
import de.openbahn.model.TransportProduct
import de.openbahn.model.maxDelayMinutes
import de.openbahn.model.withBoardRealtime
import de.openbahn.model.withRealtimeFrom
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Kotlin client for the public bahn.de vendo API (no API key required).
 * Based on https://github.com/public-transport/db-vendo-client dbweb profile.
 */
class DbVendoClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: HttpClient = createDefaultClient(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun searchLocations(query: String, locale: String = "de"): List<Location> {
        OpenBahnDebugLog.d("DbVendo", "searchLocations q=\"$query\" locale=$locale")
        val raw = httpClient.get("$baseUrl/reiseloesung/orte") {
            parameter("suchbegriff", query)
            parameter("typ", "ALL")
            parameter("max", 12)
            parameter("locale", locale)
        }
        val text = raw.body<String>()
        if (text.contains("OPS_BLOCKED")) throw DbApiBlockedException("Location search blocked")
        val locations = try {
            val asList = json.decodeFromString<List<DbOrt>>(text)
            LocationMapper.mapOrtList(asList)
        } catch (_: Exception) {
            val wrapped = json.decodeFromString<DbLocationResponse>(text)
            LocationMapper.mapLocations(wrapped)
        }
        OpenBahnDebugLog.d(
            "DbVendo",
            "searchLocations q=\"$query\" -> ${locations.size} hit(s): " +
                locations.take(4).joinToString { FahrplanDiagnostics.describeLocation(it) },
        )
        return locations
    }

    suspend fun searchJourneys(
        from: Location,
        to: Location,
        options: JourneySearchOptions = JourneySearchOptions(),
        whenTime: LocalDateTime = LocalDateTime.now(),
        pagingReference: String? = null,
    ): JourneySearchResult {
        val apiWhen = JourneySearchTime.forApiRequest(whenTime)
        var raw = postFahrplan(from, to, options, apiWhen, pagingReference)
        var result = parseJourneyResponse(raw)
        if (pagingReference == null && result.journeys.isEmpty() && FahrplanDiagnostics.isEmptySuccessBody(raw)) {
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
            val text = httpClient.post("$baseUrl/angebote/fahrplan") {
                contentType(ContentType.Application.Json)
                setBody(TextContent(bodyText, ContentType.Application.Json))
            }.bodyAsText()
            OpenBahnDebugLog.d("DbVendo", "postFahrplan response ${FahrplanDiagnostics.summarizeFahrplanBody(text)}")
            text
        } catch (e: ClientRequestException) {
            val errorBody = runCatching { e.response.bodyAsText() }.getOrDefault("")
            OpenBahnDebugLog.w(
                "DbVendo",
                "postFahrplan HTTP ${e.response.status.value} ${FahrplanDiagnostics.summarizeFahrplanBody(errorBody)}",
            )
            if (errorBody.contains("OPS_BLOCKED")) throw DbApiBlockedException("Journey search blocked")
            throw DbApiException("HTTP_${e.response.status.value}")
        } catch (e: ServerResponseException) {
            OpenBahnDebugLog.w("DbVendo", "postFahrplan HTTP ${e.response.status.value}")
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

                    if (afterRefresh.maxDelayMinutes() > 0) {
                        afterRefresh
                    } else {
                        enrichDelaysFromStationBoards(afterRefresh, from, to, departureCache, arrivalCache)
                    }
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
        if (from != null && journey.legs.isNotEmpty()) {
            val leg = journey.legs.first()
            val whenTime = parseLocalDateTime(leg.origin.scheduledTime)
            if (whenTime != null) {
            val cacheKey = boardCacheKey(from, whenTime)
            val entries = departureCache.getOrPut(cacheKey) {
                runCatching { departures(from, whenTime, durationMinutes = BOARD_LOOKUP_MINUTES) }
                    .getOrDefault(emptyList())
            }
            JourneyBoardMatcher.findDepartureMatch(entries, leg)?.let { match ->
                val delay = JourneyBoardMatcher.boardDelayMinutes(match) ?: return@let
                val origin = leg.origin.withBoardRealtime(match.scheduledTime, match.prognosedTime, delay)
                result = result.copy(
                    legs = result.legs.mapIndexed { i, l -> if (i == 0) l.copy(origin = origin) else l },
                    departure = origin.prognosedTime ?: origin.scheduledTime,
                )
            }
            }
        }
        if (to != null && result.legs.isNotEmpty()) {
            val legIndex = result.legs.lastIndex
            val leg = result.legs[legIndex]
            val whenTime = parseLocalDateTime(leg.destination.scheduledTime) ?: return result
            val cacheKey = boardCacheKey(to, whenTime)
            val entries = arrivalCache.getOrPut(cacheKey) {
                runCatching { arrivals(to, whenTime, durationMinutes = BOARD_LOOKUP_MINUTES) }
                    .getOrDefault(emptyList())
            }
            JourneyBoardMatcher.findArrivalMatch(entries, leg)?.let { match ->
                val delay = JourneyBoardMatcher.boardDelayMinutes(match) ?: return@let
                val destination = leg.destination.withBoardRealtime(
                    match.scheduledTime,
                    match.prognosedTime,
                    delay,
                )
                result = result.copy(
                    legs = result.legs.mapIndexed { i, l ->
                        if (i == legIndex) l.copy(destination = destination) else l
                    },
                    arrival = destination.prognosedTime ?: destination.scheduledTime,
                )
            }
        }
        return result
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
        val text = httpClient.get("$baseUrl/reiseloesung/abfahrten") {
            parameter("ortExtId", ortExtId)
            parameter("datum", whenTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
            parameter("zeit", whenTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
            parameter("dauer", durationMinutes)
            products.forEach { parameter("verkehrsmittel[]", it.vendoCode) }
            parameter("locale", locale)
        }.body<String>()
        if (text.contains("OPS_BLOCKED")) throw DbApiBlockedException("Departures blocked")
        val response = json.decodeFromString<DbStationBoardResponse>(text)
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
        val text = httpClient.get("$baseUrl/reiseloesung/ankuenfte") {
            parameter("ortExtId", ortExtId)
            parameter("datum", whenTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
            parameter("zeit", whenTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
            parameter("dauer", durationMinutes)
            products.forEach { parameter("verkehrsmittel[]", it.vendoCode) }
            parameter("locale", locale)
        }.body<String>()
        if (text.contains("OPS_BLOCKED")) throw DbApiBlockedException("Arrivals blocked")
        val response = json.decodeFromString<DbStationBoardResponse>(text)
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

        fun createDefaultClient(): HttpClient = HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            defaultRequest {
                headers.append("Accept", "application/json")
                headers.append("User-Agent", "OpenBahnNavigator/1.0 (FOSS; +https://github.com/openbahn-navigator)")
            }
            engine {
                config {
                    followRedirects(true)
                }
            }
        }
    }
}
