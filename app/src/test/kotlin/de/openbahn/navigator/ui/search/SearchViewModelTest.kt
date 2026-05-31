package de.openbahn.navigator.ui.search

import de.openbahn.model.Journey
import de.openbahn.model.JourneySearchResult
import de.openbahn.model.Leg
import de.openbahn.model.Location
import de.openbahn.model.RatedJourney
import de.openbahn.model.StopEvent
import de.openbahn.navigator.data.FavoriteRouteRepository
import de.openbahn.navigator.data.LocationHistoryRepository
import de.openbahn.navigator.data.PendingSearchRepository
import de.openbahn.navigator.data.TrackedJourneyRepository
import de.openbahn.navigator.data.UserPreferencesRepository
import de.openbahn.navigator.domain.JourneySearchRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { userPreferences.onboardingCompleted } returns MutableStateFlow(true)
        every { locationHistory.observeRecent() } returns flowOf(emptyList())
        every { locationHistory.observeFavoriteLocations() } returns flowOf(emptyList())
        coEvery { locationHistory.recentMatching(any()) } returns emptyList()
        coEvery { locationHistory.rankedForAutocomplete(any()) } returns emptyList()
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
        coEvery { searchRepository.rateJourneys(any()) } answers {
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
    )

    @Test
    fun search_showsJourneyResults() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.selectFrom(berlin)
        viewModel.selectTo(munich)
        viewModel.search()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.journeys.isNotEmpty() || state.ratedJourneys.isNotEmpty())
        assertNull(state.error)
        assertEquals("test-journey-1", state.ratedJourneys.first().journey.id)
        assertNotNull(state.pagingEarlier)
        assertNotNull(state.pagingLater)
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
