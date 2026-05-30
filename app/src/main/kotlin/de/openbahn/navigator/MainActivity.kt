package de.openbahn.navigator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.openbahn.navigator.ui.search.FiltersScreen
import de.openbahn.navigator.ui.search.SearchScreen
import de.openbahn.navigator.ui.theme.OpenBahnTheme
import de.openbahn.navigator.ui.tickets.TicketsScreen
import de.openbahn.navigator.ui.tracking.TrackingScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenBahnTheme {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route

                Scaffold(
                    bottomBar = {
                        if (currentRoute != Routes.FILTERS) {
                            NavigationBar {
                                listOf(
                                    Triple(Routes.SEARCH, Icons.Default.Search, R.string.nav_search),
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
                            SearchScreen(onOpenFilters = { navController.navigate(Routes.FILTERS) })
                        }
                        composable(Routes.FILTERS) {
                            FiltersScreen(onBack = { navController.popBackStack() })
                        }
                        composable(Routes.TICKETS) { TicketsScreen() }
                        composable(Routes.TRACKING) { TrackingScreen() }
                    }
                }
            }
        }
    }
}

object Routes {
    const val SEARCH = "search"
    const val FILTERS = "filters"
    const val TICKETS = "tickets"
    const val TRACKING = "tracking"
}
