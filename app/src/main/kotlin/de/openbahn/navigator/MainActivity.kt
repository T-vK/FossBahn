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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.openbahn.navigator.tracking.TrackingNotificationIntent
import de.openbahn.navigator.ui.about.AboutScreen
import de.openbahn.navigator.ui.about.ChangelogScreen
import de.openbahn.navigator.ui.debug.DebugLogScreen
import de.openbahn.navigator.ui.favorites.FavoritesScreen
import de.openbahn.navigator.ui.journey.JourneyDetailScreen
import de.openbahn.navigator.ui.menu.AppDrawerSheet
import de.openbahn.navigator.ui.search.FiltersScreen
import de.openbahn.navigator.ui.search.SearchScreen
import de.openbahn.navigator.ui.search.SearchViewModel
import de.openbahn.navigator.ui.rights.ClaimsScreen
import de.openbahn.navigator.ui.settings.SettingsScreen
import de.openbahn.navigator.ui.theme.OpenBahnTheme
import de.openbahn.navigator.ui.tickets.TicketsScreen
import de.openbahn.navigator.ui.tracking.TrackingScreen
import de.openbahn.navigator.ui.tracking.TrackingViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class MainActivity : AppCompatActivity() {
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
                val trackingViewModel: TrackingViewModel = koinViewModel()
                val openTrackedJourneyId by pendingOpenTrackedJourneyId
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                fun openDrawer() {
                    scope.launch { drawerState.open() }
                }

                fun closeDrawerAndNavigate(route: String) {
                    scope.launch {
                        drawerState.close()
                        navController.navigate(route)
                    }
                }

                LaunchedEffect(openTrackedJourneyId) {
                    val journeyId = openTrackedJourneyId ?: return@LaunchedEffect
                    pendingOpenTrackedJourneyId.value = null
                    trackingViewModel.applyNotificationHighlight(journeyId)
                    trackingViewModel.refreshNow(force = true)
                    navController.navigate(Routes.TRACKING) {
                        popUpTo(Routes.SEARCH) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

                val hideBottomBar = currentRoute == Routes.FILTERS ||
                    currentRoute == Routes.FAVORITES ||
                    currentRoute == Routes.SETTINGS ||
                    currentRoute == Routes.CLAIMS ||
                    currentRoute == Routes.JOURNEY_DETAIL ||
                    currentRoute == Routes.ABOUT ||
                    currentRoute == Routes.CHANGELOG ||
                    currentRoute == Routes.DEBUG_LOGS

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        AppDrawerSheet(
                            onSettings = { closeDrawerAndNavigate(Routes.SETTINGS) },
                            onAbout = { closeDrawerAndNavigate(Routes.ABOUT) },
                            onChangelog = { closeDrawerAndNavigate(Routes.CHANGELOG) },
                            onDebugLogs = { closeDrawerAndNavigate(Routes.DEBUG_LOGS) },
                        )
                    },
                ) {
                    Scaffold(
                        bottomBar = {
                            if (!hideBottomBar) {
                                NavigationBar {
                                    listOf(
                                        Triple(Routes.SEARCH, Icons.Default.Route, R.string.nav_connections),
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
                                    onOpenDrawer = ::openDrawer,
                                    onOpenFilters = { navController.navigate(Routes.FILTERS) },
                                    onOpenFavorites = { navController.navigate(Routes.FAVORITES) },
                                    onOpenJourneyDetail = { navController.navigate(Routes.JOURNEY_DETAIL) },
                                    viewModel = searchViewModel,
                                )
                            }
                            composable(Routes.FILTERS) {
                                FiltersScreen(onBack = { navController.popBackStack() })
                            }
                            composable(Routes.SETTINGS) {
                                SettingsScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenClaims = { navController.navigate(Routes.CLAIMS) },
                                )
                            }
                            composable(Routes.CLAIMS) {
                                ClaimsScreen(onBack = { navController.popBackStack() })
                            }
                            composable(Routes.ABOUT) {
                                AboutScreen(onBack = { navController.popBackStack() })
                            }
                            composable(Routes.CHANGELOG) {
                                ChangelogScreen(onBack = { navController.popBackStack() })
                            }
                            composable(Routes.DEBUG_LOGS) {
                                DebugLogScreen(onBack = { navController.popBackStack() })
                            }
                            composable(Routes.FAVORITES) {
                                FavoritesScreen(
                                    onBack = { navController.popBackStack() },
                                    onSearchRoute = { navController.popBackStack() },
                                )
                            }
                            composable(Routes.TICKETS) {
                                TicketsScreen(onOpenDrawer = ::openDrawer)
                            }
                            composable(Routes.TRACKING) {
                                TrackingScreen(
                                    onOpenDrawer = ::openDrawer,
                                    viewModel = trackingViewModel,
                                    onOpenJourneyDetail = { navController.navigate(Routes.JOURNEY_DETAIL) },
                                    onShowAlternatives = {
                                        navController.navigate(Routes.SEARCH) {
                                            popUpTo(Routes.SEARCH) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                )
                            }
                            composable(Routes.JOURNEY_DETAIL) { entry ->
                                val payload = remember(entry.id) {
                                    de.openbahn.navigator.navigation.JourneyNavigation.consume()
                                }
                                if (payload != null) {
                                    JourneyDetailScreen(
                                        payload = payload,
                                        onBack = { navController.popBackStack() },
                                        onTrack = { searchViewModel.trackJourney(payload.journey) },
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeTrackedJourneyId(intent)?.let { pendingOpenTrackedJourneyId.value = it }
    }

    private fun consumeTrackedJourneyId(intent: Intent?): String? {
        if (intent == null) return null
        val fromNotification = intent.action == TrackingNotificationIntent.ACTION_OPEN_TRACKED_JOURNEY ||
            intent.hasExtra(TrackingNotificationIntent.EXTRA_TRACKED_JOURNEY_ID)
        if (!fromNotification) return null
        val id = intent.getStringExtra(TrackingNotificationIntent.EXTRA_TRACKED_JOURNEY_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        intent.removeExtra(TrackingNotificationIntent.EXTRA_TRACKED_JOURNEY_ID)
        intent.action = null
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
    const val CLAIMS = "claims"
    const val ABOUT = "about"
    const val CHANGELOG = "changelog"
    const val DEBUG_LOGS = "debug_logs"
}
