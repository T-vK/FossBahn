package de.openbahn.navigator.domain

import de.openbahn.api.BahnVorhersageClient
import de.openbahn.api.DbVendoClient
import de.openbahn.api.JourneyRatingOptions
import de.openbahn.model.Journey
import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.JourneySearchResult
import de.openbahn.model.Location
import de.openbahn.model.RatedJourney
import java.time.LocalDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

interface JourneySearchRepository {
    suspend fun searchLocations(query: String, locale: String): List<Location>
    suspend fun searchLocationsNearby(latitude: Double, longitude: Double, locale: String): List<Location>
    suspend fun searchJourneys(
        from: Location,
        to: Location,
        options: JourneySearchOptions,
        whenTime: LocalDateTime = LocalDateTime.now(),
        pagingReference: String? = null,
    ): JourneySearchResult
    suspend fun rateJourneys(
        journeys: List<Journey>,
        ratingOptions: JourneyRatingOptions = JourneyRatingOptions(),
    ): List<RatedJourney>
}

class JourneySearchUseCase(
    private val dbClient: DbVendoClient,
    private val predictionClient: BahnVorhersageClient,
) : JourneySearchRepository {
    override suspend fun searchLocations(query: String, locale: String): List<Location> =
        dbClient.searchLocations(query, locale)

    override suspend fun searchLocationsNearby(
        latitude: Double,
        longitude: Double,
        locale: String,
    ): List<Location> = dbClient.searchLocationsNearby(latitude, longitude, locale)

    override suspend fun searchJourneys(
        from: Location,
        to: Location,
        options: JourneySearchOptions,
        whenTime: LocalDateTime,
        pagingReference: String?,
    ): JourneySearchResult {
        val result = dbClient.searchJourneys(from, to, options, whenTime, pagingReference)
        val withDelays = dbClient.enrichJourneysWithRealtime(result.journeys, from, to)
        return result.copy(journeys = withDelays)
    }

    override suspend fun rateJourneys(
        journeys: List<Journey>,
        ratingOptions: JourneyRatingOptions,
    ): List<RatedJourney> = coroutineScope {
        journeys.map { journey ->
            async { predictionClient.rateJourney(journey, ratingOptions) }
        }.awaitAll()
    }
}

class PredictionUseCase(private val client: BahnVorhersageClient) {
    suspend fun rate(
        journey: Journey,
        ratingOptions: JourneyRatingOptions = JourneyRatingOptions(),
    ): RatedJourney = client.rateJourney(journey, ratingOptions)
}
