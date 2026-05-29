package de.openbahn.api

import de.openbahn.api.dto.DbJourneyResponse
import de.openbahn.api.dto.DbLocationResponse
import de.openbahn.api.dto.DbStationBoardResponse
import de.openbahn.api.mapper.JourneyMapper
import de.openbahn.api.mapper.LocationMapper
import de.openbahn.api.mapper.StationBoardMapper
import de.openbahn.model.BoardEntry
import de.openbahn.model.Journey
import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.Location
import de.openbahn.model.StationBoard
import de.openbahn.model.TransportProduct
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
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
        val response: DbLocationResponse = httpClient.get("$baseUrl/reiseloesung/orte") {
            parameter("suchbegriff", query)
            parameter("typ", "ALL")
            parameter("max", 12)
            parameter("locale", locale)
        }.body()
        return LocationMapper.mapLocations(response)
    }

    suspend fun searchJourneys(
        from: Location,
        to: Location,
        options: JourneySearchOptions = JourneySearchOptions(),
        whenTime: LocalDateTime = LocalDateTime.now(),
    ): List<Journey> {
        val body = JourneyRequestBuilder.build(from, to, options, whenTime)
        val response: DbJourneyResponse = httpClient.post("$baseUrl/angebote/fahrplan") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
        return JourneyMapper.mapJourneys(response)
    }

    suspend fun refreshJourney(refreshToken: String): Journey? {
        val body = buildJsonObject { put("ctxRecon", refreshToken); put("poly", true) }
        val response: JsonObject = httpClient.post("$baseUrl/reiseloesung/verbindung") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
        return JourneyMapper.mapRefresh(response)
    }

    suspend fun departures(
        station: Location,
        whenTime: LocalDateTime = LocalDateTime.now(),
        durationMinutes: Int = 60,
        products: Set<TransportProduct> = TransportProduct.ALL,
        locale: String = "de",
    ): List<BoardEntry> {
        val response: DbStationBoardResponse = httpClient.get("$baseUrl/reiseloesung/abfahrten") {
            parameter("ortExtId", station.id)
            parameter("datum", whenTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
            parameter("zeit", whenTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
            parameter("dauer", durationMinutes)
            products.forEach { parameter("verkehrsmittel[]", it.vendoCode) }
            parameter("locale", locale)
        }.body()
        return StationBoardMapper.mapDepartures(response)
    }

    suspend fun arrivals(
        station: Location,
        whenTime: LocalDateTime = LocalDateTime.now(),
        durationMinutes: Int = 60,
        products: Set<TransportProduct> = TransportProduct.ALL,
        locale: String = "de",
    ): List<BoardEntry> {
        val response: DbStationBoardResponse = httpClient.get("$baseUrl/reiseloesung/ankuenfte") {
            parameter("ortExtId", station.id)
            parameter("datum", whenTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
            parameter("zeit", whenTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
            parameter("dauer", durationMinutes)
            products.forEach { parameter("verkehrsmittel[]", it.vendoCode) }
            parameter("locale", locale)
        }.body()
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
