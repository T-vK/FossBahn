package de.openbahn.navigator

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.openbahn.navigator.data.TrackedJourneyRepository
import de.openbahn.navigator.navigation.JourneyNavigation
import de.openbahn.navigator.tracking.TrackingNotificationIntent
import de.openbahn.navigator.ui.favorites.FavoritesScreen
import de.openbahn.navigator.ui.journey.JourneyDetailScreen
import de.openbahn.navigator.ui.search.FiltersScreen
import de.openbahn.navigator.ui.search.SearchScreen
import de.openbahn.navigator.ui.search.SearchViewModel
import de.openbahn.navigator.ui.settings.SettingsScreen
import de.openbahn.navigator.ui.theme.OpenBahnTheme
import de.openbahn.navigator.ui.tickets.TicketsScreen
import de.openbahn.navigator.ui.tracking.TrackingScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

class MainActivity : AppCompatActivity() {
    private val trackedJourneyRepository: TrackedJourneyRepository by inject()
    private val pendingOpenTrackedJourneyId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingOpenTrackedJourneyId.value = consumeTrackedJourneyId(intent)
        setContent {
            OpenBahnTheme {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route
                val searchViewModel: SearchViewModel = koinViewModel()
                val context = LocalContext.current
                val openTrackedJourneyId by pendingOpenTrackedJourneyId

                LaunchedEffect(openTrackedJourneyId) {
                    val journeyId = openTrackedJourneyId ?: return@LaunchedEffect
                    pendingOpenTrackedJourneyId.value = null
                    val tracked = withContext(Dispatchers.IO) {
                        trackedJourneyRepository.findActiveWithJourney(journeyId)
                    } ?: return@LaunchedEffect
                    JourneyNavigation.set(tracked.journey, predictionsRequested = true)
                    navController.navigate(Routes.TRACKING) {
                        popUpTo(Routes.SEARCH) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    navController.navigate(Routes.JOURNEY_DETAIL)
                }

                val hideBottomBar = currentRoute == Routes.FILTERS ||
                    currentRoute == Routes.SETTINGS ||
                    currentRoute == Routes.JOURNEY_DETAIL

                Scaffold(
                    bottomBar = {
                        if (!hideBottomBar) {
                            NavigationBar {
                                listOf(
                                    Triple(Routes.SEARCH, Icons.Default.Route, R.string.nav_connections),
                                    Triple(Routes.FAVORITES, Icons.Default.Star, R.string.nav_favorites),
                                    Triple(Routes.TICKETS, Icons.Default.ConfirmationNumber, R.string.nav_tickets),
                                    Triple(Routes.TRACKING, Icons.Default.Notifications, R.string.nav_tracking),
                                ).forEach { (route, icon, labelRes) ->
                                    NavigationBarItem(
                                        selected = currentRoute == route,
                                        onClick = {
                                            navController.navigate(route) {
                                                popUpTo(Routes.SEARCH) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(icon, null) },
                                        label = { Text(stringResource(labelRes)) },
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = Routes.SEARCH,
                        modifier = Modifier.padding(padding),
                    ) {
                        composable(Routes.SEARCH) {
                            SearchScreen(
                                onOpenFilters = { navController.navigate(Routes.FILTERS) },
                                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                                onOpenJourneyDetail = { navController.navigate(Routes.JOURNEY_DETAIL) },
                                viewModel = searchViewModel,
                            )
                        }
                        composable(Routes.FILTERS) {
                            FiltersScreen(onBack = { navController.popBackStack() })
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(Routes.FAVORITES) {
                            FavoritesScreen(
                                onSearchRoute = {
                                    navController.navigate(Routes.SEARCH) {
                                        popUpTo(Routes.SEARCH) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                        composable(Routes.TICKETS) { TicketsScreen() }
                        composable(Routes.TRACKING) {
                            TrackingScreen(
                                onOpenJourneyDetail = { navController.navigate(Routes.JOURNEY_DETAIL) },
                            )
                        }
                        composable(Routes.JOURNEY_DETAIL) { entry ->
                            val payload = remember(entry.id) { JourneyNavigation.consume() }
                            if (payload != null) {
                                JourneyDetailScreen(
                                    payload = payload,
                                    onBack = { navController.popBackStack() },
                                    onTrack = {
                                        searchViewModel.trackJourney(payload.journey, context)
                                    },
                                )
                            } else {
                                LaunchedEffect(Unit) {
                                    navController.popBackStack()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeTrackedJourneyId(intent)?.let { pendingOpenTrackedJourneyId.value = it }
    }

    private fun consumeTrackedJourneyId(intent: Intent?): String? {
        val id = intent?.getStringExtra(TrackingNotificationIntent.EXTRA_TRACKED_JOURNEY_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        intent.removeExtra(TrackingNotificationIntent.EXTRA_TRACKED_JOURNEY_ID)
        return id
    }
}

object Routes {
    const val SEARCH = "search"
    const val FILTERS = "filters"
    const val SETTINGS = "settings"
    const val FAVORITES = "favorites"
    const val TICKETS = "tickets"
    const val TRACKING = "tracking"
    const val JOURNEY_DETAIL = "journey_detail"
}
