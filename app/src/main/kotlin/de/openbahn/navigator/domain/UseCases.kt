package de.openbahn.navigator.domain

import de.openbahn.api.BahnVorhersageClient
import de.openbahn.api.DbVendoClient
import de.openbahn.api.JourneyRatingOptions
import de.openbahn.api.loadTripRoutesForJourneys
import de.openbahn.model.Journey
import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.JourneySearchResult
import de.openbahn.model.Leg
import de.openbahn.model.Location
import de.openbahn.model.RatedJourney
import de.openbahn.model.StopEvent
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

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
    suspend fun fetchTripRoute(journeyId: String): List<StopEvent>
    suspend fun fetchFullLegRoute(leg: Leg): List<StopEvent>
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
    ): List<RatedJourney> {
        val tripRoutes = loadTripRoutesForJourneys(journeys) { leg -> fetchFullLegRoute(leg) }
        return predictionClient.rateJourneys(journeys, ratingOptions, tripRoutes)
    }

    override suspend fun fetchTripRoute(journeyId: String): List<StopEvent> =
        dbClient.fetchTripRoute(journeyId)

    override suspend fun fetchFullLegRoute(leg: Leg): List<StopEvent> =
        withContext(Dispatchers.IO) { dbClient.fetchFullLegRoute(leg) }
}

class PredictionUseCase(
    private val client: BahnVorhersageClient,
    private val searchRepository: JourneySearchRepository,
) {
    suspend fun rate(
        journey: Journey,
        ratingOptions: JourneyRatingOptions = JourneyRatingOptions(),
    ): RatedJourney {
        val tripRoutes = loadTripRoutesForJourneys(listOf(journey)) { leg ->
            searchRepository.fetchFullLegRoute(leg)
        }
        return client.rateJourney(journey, ratingOptions, tripRoutes)
    }
}
