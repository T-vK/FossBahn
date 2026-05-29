package de.openbahn.navigator.ui.search

import de.openbahn.model.Journey
import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.Leg
import de.openbahn.model.Location
import de.openbahn.model.RatedJourney
import de.openbahn.model.StopEvent
import de.openbahn.navigator.domain.JourneySearchRepository
import java.time.LocalDateTime

/** Deterministic journey search for instrumented UI tests. */
class FakeJourneySearchRepository : JourneySearchRepository {
    val berlin = Location(id = "8011160", name = "Berlin Hbf", evaNumber = "8011160")
    val munich = Location(id = "8000261", name = "München Hbf", evaNumber = "8000261")

    override suspend fun searchLocations(query: String, locale: String): List<Location> = when {
        query.contains("Berlin", ignoreCase = true) -> listOf(berlin)
        query.contains("München", ignoreCase = true) || query.contains("Munich", ignoreCase = true) ->
            listOf(munich)
        else -> emptyList()
    }

    override suspend fun searchJourneys(
        from: Location,
        to: Location,
        options: JourneySearchOptions,
        whenTime: LocalDateTime,
    ): List<Journey> = listOf(sampleJourney)

    override suspend fun searchWithPredictions(
        from: Location,
        to: Location,
        options: JourneySearchOptions,
        whenTime: LocalDateTime,
    ): List<RatedJourney> = listOf(RatedJourney(journey = sampleJourney))

    val sampleJourney = Journey(
        id = "test-journey-1",
        legs = listOf(
            Leg(
                origin = StopEvent("Berlin Hbf", scheduledTime = "2026-05-30T10:00:00"),
                destination = StopEvent("München Hbf", scheduledTime = "2026-05-30T16:00:00"),
                lineName = "ICE 123",
            ),
        ),
        durationMinutes = 360,
        transfers = 0,
        departure = "2026-05-30T10:00:00",
        arrival = "2026-05-30T16:00:00",
        priceHint = "49,90 €",
    )
}
