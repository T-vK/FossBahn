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
import de.openbahn.model.BoardEntry
import de.openbahn.model.Journey
import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.JourneySearchResult
import de.openbahn.model.Location
import de.openbahn.model.StationBoard
import de.openbahn.model.TransportProduct
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
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerializationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
        val raw = postFahrplan(from, to, options, whenTime, pagingReference)
        val result = parseJourneyResponse(raw)
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
        OpenBahnDebugLog.d(
            "DbVendo",
            "postFahrplan ${FahrplanDiagnostics.describeLocation(from)} -> " +
                "${FahrplanDiagnostics.describeLocation(to)} anfrageZeitpunkt=$whenTime",
        )
        return try {
            val text = httpClient.post("$baseUrl/angebote/fahrplan") {
                contentType(ContentType.Application.Json)
                setBody(body)
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

    /** Fetches up-to-date delays via `/reiseloesung/verbindung` (search often omits verspaetung). */
    suspend fun enrichJourneysWithRealtime(journeys: List<Journey>): List<Journey> {
        if (journeys.isEmpty()) return journeys
        return coroutineScope {
            val semaphore = Semaphore(MAX_CONCURRENT_JOURNEY_REFRESH)
            journeys.map { journey ->
                async {
                    val token = journey.refreshToken?.takeIf { it.isNotBlank() } ?: return@async journey
                    semaphore.withPermit {
                        runCatching { refreshJourney(token) }.getOrNull() ?: journey
                    }
                }
            }.map { it.await() }
        }
    }

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
