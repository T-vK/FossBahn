package de.openbahn.navigator.ui.search

import de.openbahn.model.Journey
import de.openbahn.model.JourneySearchResult
import de.openbahn.model.Leg
import de.openbahn.model.Location
import de.openbahn.model.RatedJourney
import de.openbahn.model.StopEvent
import de.openbahn.navigator.data.FavoriteRouteRepository
import de.openbahn.navigator.data.stableKey
import de.openbahn.navigator.data.LocationHistoryRepository
import de.openbahn.navigator.data.PendingSearchRepository
import de.openbahn.navigator.data.TrackedJourneyRepository
import de.openbahn.navigator.data.UserPreferencesRepository
import android.app.Application
import de.openbahn.navigator.domain.JourneySearchRepository
import de.openbahn.navigator.location.DeviceLocationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import de.openbahn.navigator.locale.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val berlin = Location(id = "8011160", name = "Berlin Hbf", evaNumber = "8011160")
    private val munich = Location(id = "8000261", name = "München Hbf", evaNumber = "8000261")
    private val sampleJourney = Journey(
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
    )
    private val searchRepository = mockk<JourneySearchRepository>()
    private val trackingRepository = mockk<TrackedJourneyRepository>(relaxed = true)
    private val locationHistory = mockk<LocationHistoryRepository>(relaxed = true)
    private val userPreferences = mockk<UserPreferencesRepository>(relaxed = true)
    private val favoriteRoutes = mockk<FavoriteRouteRepository>(relaxed = true)
    private val pendingSearch = PendingSearchRepository()
    private val deviceLocation = mockk<DeviceLocationProvider>(relaxed = true)
    private val appContext = mockk<Application>(relaxed = true)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { userPreferences.onboardingCompleted } returns MutableStateFlow(true)
        every { locationHistory.observeRecent() } returns flowOf(emptyList())
        every { locationHistory.observeFavoriteLocations() } returns flowOf(emptyList())
        every { userPreferences.appLanguage } returns flowOf(AppLanguage.ENGLISH)
        every { userPreferences.onTimeTolerance } returns flowOf(
            de.openbahn.model.OnTimeToleranceSettings.uniform(10),
        )
        every { userPreferences.deutschlandTicketConnectionsOnly } returns flowOf(false)
        coEvery { locationHistory.recentMatching(any()) } returns emptyList()
        coEvery { locationHistory.rankedForAutocomplete(any()) } returns emptyList()
        coEvery { searchRepository.searchLocationsNearby(any(), any(), any()) } returns emptyList()
        coEvery { searchRepository.searchLocations(any(), any()) } answers {
            val q = firstArg<String>()
            when {
                q.contains("Berlin", ignoreCase = true) -> listOf(berlin)
                q.contains("München", ignoreCase = true) -> listOf(munich)
                else -> emptyList()
            }
        }
        coEvery {
            searchRepository.searchJourneys(any(), any(), any(), any(), any())
        } returns JourneySearchResult(
            journeys = listOf(sampleJourney),
            pagingEarlier = "earlier",
            pagingLater = "later",
        )
        coEvery { searchRepository.rateJourney(any(), any()) } answers {
            RatedJourney(journey = firstArg())
        }
        coEvery { searchRepository.rateJourneys(any(), any()) } answers {
            firstArg<List<Journey>>().map { RatedJourney(journey = it) }
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SearchViewModel(
        searchRepository,
        trackingRepository,
        locationHistory,
        userPreferences,
        favoriteRoutes,
        pendingSearch,
        deviceLocation,
        appContext,
    )

    @Test
    fun search_showsJourneyResults() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.selectFrom(berlin)
        viewModel.selectTo(munich)
        viewModel.search()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.journeys.isNotEmpty())
        assertNull(state.error)
        assertFalse(state.isLoading)
        assertEquals("test-journey-1", state.journeys.first().id)
        assertEquals("test-journey-1", state.ratedJourneys.first().journey.id)
        assertFalse(state.predictionsLoading)
        assertNotNull(state.pagingEarlier)
        assertNotNull(state.pagingLater)
    }

    @Test
    fun search_showsConnectionsBeforePredictionsFinish() = runTest(dispatcher) {
        val ratingGate = CompletableDeferred<Unit>()
        coEvery { searchRepository.rateJourney(any(), any()) } coAnswers {
            ratingGate.await()
            RatedJourney(journey = firstArg())
        }
        val viewModel = createViewModel()
        viewModel.selectFrom(berlin)
        viewModel.selectTo(munich)
        viewModel.search()
        advanceUntilIdle()

        val afterSearch = viewModel.state.value
        assertEquals(listOf("test-journey-1"), afterSearch.journeys.map { it.id })
        assertTrue(afterSearch.ratedJourneys.isEmpty())
        assertFalse(afterSearch.isLoading)
        assertTrue(afterSearch.predictionsLoading)

        ratingGate.complete(Unit)
        advanceUntilIdle()
        assertEquals("test-journey-1", viewModel.state.value.ratedJourneys.first().journey.id)
        assertFalse(viewModel.state.value.predictionsLoading)
    }

    @Test
    fun search_withNoJourneys_showsInfoNotError() = runTest(dispatcher) {
        coEvery {
            searchRepository.searchJourneys(any(), any(), any(), any(), any())
        } returns JourneySearchResult()
        val viewModel = createViewModel()
        viewModel.selectFrom(berlin)
        viewModel.selectTo(munich)
        viewModel.search()
        advanceUntilIdle()
        assertEquals("info_no_connections", viewModel.state.value.info)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun fromSuggestions_excludeSelectedToStation() = runTest(dispatcher) {
        coEvery { locationHistory.rankedForAutocomplete(any()) } returns listOf(berlin, munich)
        val viewModel = createViewModel()
        viewModel.selectTo(munich)
        viewModel.onFromFocusChanged(true)
        advanceUntilIdle()

        val suggestions = viewModel.state.value.fromSuggestions
        assertTrue(suggestions.any { it.stableKey() == berlin.stableKey() })
        assertTrue(suggestions.none { it.stableKey() == munich.stableKey() })
    }

    @Test
    fun toSuggestions_excludeSelectedFromStation() = runTest(dispatcher) {
        coEvery { locationHistory.rankedForAutocomplete(any()) } returns listOf(berlin, munich)
        val viewModel = createViewModel()
        viewModel.selectFrom(berlin)
        viewModel.onToFocusChanged(true)
        advanceUntilIdle()

        val suggestions = viewModel.state.value.toSuggestions
        assertTrue(suggestions.any { it.stableKey() == munich.stableKey() })
        assertTrue(suggestions.none { it.stableKey() == berlin.stableKey() })
    }

    @Test
    fun arrivalSearch_trimsToBestMatchWithQualifyingBuffer() = runTest(dispatcher) {
        fun journey(id: String, arrival: String) = Journey(
            id = id,
            legs = listOf(
                Leg(
                    origin = StopEvent("Berlin Hbf", scheduledTime = "2026-05-30T07:00:00"),
                    destination = StopEvent("München Hbf", scheduledTime = arrival),
                ),
            ),
            durationMinutes = 60,
            transfers = 0,
            departure = "2026-05-30T07:00:00",
            arrival = arrival,
        )
        val best = journey("best", "2026-05-30T09:59:00")
        val later = journey("later", "2026-05-30T10:30:00")
        val tooEarly = journey("tooEarly", "2026-05-30T09:40:00")
        val preceding = journey("preceding", "2026-05-30T09:53:00")

        coEvery {
            searchRepository.searchJourneys(any(), any(), any(), any(), any())
        } returns JourneySearchResult(
            journeys = listOf(best, later),
            pagingEarlier = "earlier-page-token",
            pagingLater = "later-page-token",
        )
        coEvery {
            searchRepository.searchJourneys(any(), any(), any(), any(), eq("earlier-page-token"))
        } returns JourneySearchResult(
            journeys = listOf(tooEarly, preceding),
            pagingEarlier = "even-earlier-token",
        )
        coEvery {
            searchRepository.searchJourneys(any(), any(), any(), any(), eq("later-page-token"))
        } returns JourneySearchResult(
            journeys = emptyList(),
            pagingLater = "even-later-token",
        )

        val viewModel = createViewModel()
        viewModel.selectFrom(berlin)
        viewModel.selectTo(munich)
        viewModel.setWhen(LocalDateTime.of(2026, 5, 30, 10, 0), arrivalSearch = true)
        viewModel.search()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(
            listOf("preceding", "best", "later"),
            state.journeys.map { it.id },
        )
        assertEquals("even-earlier-token", state.pagingEarlier)
        assertEquals("even-later-token", state.pagingLater)
    }

    @Test
    fun arrivalSearch_fetchesLaterPageToSurfaceBestMatch() = runTest(dispatcher) {
        fun journey(id: String, arrival: String) = Journey(
            id = id,
            legs = listOf(
                Leg(
                    origin = StopEvent("Berlin Hbf", scheduledTime = "2026-05-30T07:00:00"),
                    destination = StopEvent("München Hbf", scheduledTime = arrival),
                ),
            ),
            durationMinutes = 60,
            transfers = 0,
            departure = "2026-05-30T07:00:00",
            arrival = arrival,
        )
        val initialEarly = journey("initialEarly", "2026-05-30T17:30:00")
        val laterBest = journey("laterBest", "2026-05-30T18:00:00")
        val laterAfter = journey("laterAfter", "2026-05-30T18:15:00")

        coEvery {
            searchRepository.searchJourneys(any(), any(), any(), any(), any())
        } returns JourneySearchResult(
            journeys = listOf(initialEarly),
            pagingLater = "later-page-token",
        )
        coEvery {
            searchRepository.searchJourneys(any(), any(), any(), any(), eq("later-page-token"))
        } returns JourneySearchResult(
            journeys = listOf(laterBest, laterAfter),
            pagingLater = "even-later-token",
        )

        val viewModel = createViewModel()
        viewModel.selectFrom(berlin)
        viewModel.selectTo(munich)
        viewModel.setWhen(LocalDateTime.of(2026, 5, 30, 18, 0), arrivalSearch = true)
        viewModel.search()
        advanceUntilIdle()

        assertEquals(
            listOf("laterBest", "laterAfter"),
            viewModel.state.value.journeys.map { it.id },
        )
    }

    @Test
    fun arrivalSearch_withoutQualifyingBuffer_startsAtBestMatch() = runTest(dispatcher) {
        fun journey(id: String, arrival: String) = Journey(
            id = id,
            legs = listOf(
                Leg(
                    origin = StopEvent("Berlin Hbf", scheduledTime = "2026-05-30T07:00:00"),
                    destination = StopEvent("München Hbf", scheduledTime = arrival),
                ),
            ),
            durationMinutes = 60,
            transfers = 0,
            departure = "2026-05-30T07:00:00",
            arrival = arrival,
        )
        val first = journey("first", "2026-05-30T09:59:00")
        val later = journey("later", "2026-05-30T10:30:00")
        val tooEarly = journey("tooEarly", "2026-05-30T09:40:00")

        coEvery {
            searchRepository.searchJourneys(any(), any(), any(), any(), any())
        } returns JourneySearchResult(
            journeys = listOf(first, later),
            pagingEarlier = "earlier-page-token",
            pagingLater = "later-page-token",
        )
        coEvery {
            searchRepository.searchJourneys(any(), any(), any(), any(), eq("earlier-page-token"))
        } returns JourneySearchResult(
            journeys = listOf(tooEarly),
            pagingEarlier = "even-earlier-token",
        )
        coEvery {
            searchRepository.searchJourneys(any(), any(), any(), any(), eq("later-page-token"))
        } returns JourneySearchResult(journeys = emptyList())

        val viewModel = createViewModel()
        viewModel.selectFrom(berlin)
        viewModel.selectTo(munich)
        viewModel.setWhen(LocalDateTime.of(2026, 5, 30, 10, 0), arrivalSearch = true)
        viewModel.search()
        advanceUntilIdle()

        assertEquals(
            listOf("first", "later"),
            viewModel.state.value.journeys.map { it.id },
        )
        assertEquals(listOf("tooEarly"), viewModel.state.value.hiddenArrivalJourneys.map { it.id })
    }

    @Test
    fun arrivalSearch_loadEarlier_revealsHiddenBeforeFetchingMore() = runTest(dispatcher) {
        fun journey(id: String, arrival: String) = Journey(
            id = id,
            legs = listOf(
                Leg(
                    origin = StopEvent("Berlin Hbf", scheduledTime = "2026-05-30T07:00:00"),
                    destination = StopEvent("München Hbf", scheduledTime = arrival),
                ),
            ),
            durationMinutes = 60,
            transfers = 0,
            departure = "2026-05-30T07:00:00",
            arrival = arrival,
        )
        val early1 = journey("early1", "2026-05-30T16:00:00")
        val early2 = journey("early2", "2026-05-30T17:00:00")
        val best = journey("best", "2026-05-30T18:00:00")
        val after = journey("after", "2026-05-30T18:15:00")

        coEvery {
            searchRepository.searchJourneys(any(), any(), any(), any(), any())
        } returns JourneySearchResult(
            journeys = listOf(best, after),
            pagingEarlier = "earlier-page-token",
        )
        coEvery {
            searchRepository.searchJourneys(any(), any(), any(), any(), eq("earlier-page-token"))
        } returns JourneySearchResult(
            journeys = listOf(early1, early2),
            pagingEarlier = "even-earlier-token",
        )

        val viewModel = createViewModel()
        viewModel.selectFrom(berlin)
        viewModel.selectTo(munich)
        viewModel.setWhen(LocalDateTime.of(2026, 5, 30, 18, 0), arrivalSearch = true)
        viewModel.search()
        advanceUntilIdle()

        assertEquals(listOf("best", "after"), viewModel.state.value.journeys.map { it.id })
        assertEquals(listOf("early1", "early2"), viewModel.state.value.hiddenArrivalJourneys.map { it.id })

        viewModel.loadEarlierConnections()
        advanceUntilIdle()

        assertEquals(
            listOf("early1", "early2", "best", "after"),
            viewModel.state.value.journeys.map { it.id },
        )
        assertTrue(viewModel.state.value.hiddenArrivalJourneys.isEmpty())
        coVerify(exactly = 2) { searchRepository.searchJourneys(any(), any(), any(), any(), any()) }
    }

    @Test
    fun departureSearch_keepsApiOrder() = runTest(dispatcher) {
        fun journey(id: String, arrival: String) = Journey(
            id = id,
            legs = listOf(
                Leg(
                    origin = StopEvent("Berlin Hbf", scheduledTime = "2026-05-30T07:00:00"),
                    destination = StopEvent("München Hbf", scheduledTime = arrival),
                ),
            ),
            durationMinutes = 60,
            transfers = 0,
            departure = "2026-05-30T07:00:00",
            arrival = arrival,
        )
        val first = journey("first", "2026-05-30T10:30:00")
        val second = journey("second", "2026-05-30T09:59:00")
        coEvery {
            searchRepository.searchJourneys(any(), any(), any(), any(), any())
        } returns JourneySearchResult(journeys = listOf(first, second))

        val viewModel = createViewModel()
        viewModel.selectFrom(berlin)
        viewModel.selectTo(munich)
        viewModel.setWhen(LocalDateTime.of(2026, 5, 30, 7, 0), arrivalSearch = false)
        viewModel.search()
        advanceUntilIdle()

        assertEquals(listOf("first", "second"), viewModel.state.value.journeys.map { it.id })
    }

    @Test
    fun search_withoutStations_showsError() = runTest(dispatcher) {
        coEvery { searchRepository.searchLocations(any(), any()) } returns emptyList()
        val viewModel = createViewModel()
        viewModel.setFromQuery("x")
        viewModel.setToQuery("y")
        viewModel.search()
        advanceUntilIdle()

        assertEquals("error_select_stations", viewModel.state.value.error)
    }
}
