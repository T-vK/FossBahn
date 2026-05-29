package de.openbahn.navigator.domain

import de.openbahn.api.BahnVorhersageClient
import de.openbahn.api.DbVendoClient
import de.openbahn.model.Journey
import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.Location
import de.openbahn.model.RatedJourney
import java.time.LocalDateTime

class JourneySearchUseCase(
    private val dbClient: DbVendoClient,
    private val predictionClient: BahnVorhersageClient,
) {
    suspend fun searchLocations(query: String, locale: String): List<Location> =
        dbClient.searchLocations(query, locale)

    suspend fun searchJourneys(
        from: Location,
        to: Location,
        options: JourneySearchOptions,
        whenTime: LocalDateTime = LocalDateTime.now(),
        withPredictions: Boolean = false,
    ): List<Journey> = dbClient.searchJourneys(from, to, options, whenTime)

    suspend fun searchWithPredictions(
        from: Location,
        to: Location,
        options: JourneySearchOptions,
        whenTime: LocalDateTime = LocalDateTime.now(),
    ): List<RatedJourney> {
        val journeys = searchJourneys(from, to, options, whenTime)
        return journeys.map { predictionClient.rateJourney(it) }
    }
}

class PredictionUseCase(private val client: BahnVorhersageClient) {
    suspend fun rate(journey: Journey): RatedJourney = client.rateJourney(journey)
}
