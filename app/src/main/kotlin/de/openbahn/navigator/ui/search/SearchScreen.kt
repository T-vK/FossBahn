package de.openbahn.navigator.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.model.Location
import de.openbahn.model.RatedJourney
import de.openbahn.navigator.R
import de.openbahn.navigator.navigation.JourneyNavigation
import de.openbahn.navigator.ui.components.ErrorBanner
import de.openbahn.navigator.ui.components.JourneyCard
import de.openbahn.navigator.ui.components.LoadingIndicator
import de.openbahn.navigator.data.stableKey
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenFilters: () -> Unit,
    onOpenJourneyDetail: () -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showDateTimePicker by remember { mutableStateOf(false) }
    val whenLabel = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault())
        .format(state.departureTime)
    val whenCaption = if (state.options.arrivalSearch) {
        stringResource(R.string.search_arrival_at, whenLabel)
    } else {
        stringResource(R.string.search_departure_at, whenLabel)
    }

    if (state.showOnboarding) {
        DeutschlandTicketOnboardingDialog(
            onDismissOnly = viewModel::dismissOnboarding,
            onEnableFilter = { viewModel.completeOnboarding(deutschlandTicketOnly = true) },
        )
    }

    DateTimePickerDialog(
        visible = showDateTimePicker,
        selected = state.departureTime,
        arrivalSearch = state.options.arrivalSearch,
        onDismiss = { showDateTimePicker = false },
        onConfirm = { time, arrival ->
            viewModel.setWhen(time, arrival)
            showDateTimePicker = false
        },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.testTag("search_screen_title"),
                title = { Text(stringResource(R.string.search_title)) },
                actions = {
                    IconButton(onClick = onOpenFilters) {
                        Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.filters))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("search_results"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "from_field") {
                OutlinedTextField(
                    value = state.fromQuery,
                    onValueChange = viewModel::setFromQuery,
                    label = { Text(stringResource(R.string.from)) },
                    modifier = Modifier.fillMaxWidth().testTag("search_from"),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            val from = state.from
                            val to = state.to
                            if (from != null && to != null) {
                                viewModel.selectFrom(to)
                                viewModel.selectTo(from)
                            }
                        }) {
                            Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.swap_stations))
                        }
                    },
                )
            }
            item(key = "from_suggestions") {
                SuggestionList(
                    suggestions = state.fromSuggestions,
                    favoriteKeys = state.favoriteLocationKeys,
                    onSelect = viewModel::selectFrom,
                    onToggleFavorite = viewModel::toggleFavoriteLocation,
                )
            }
            item(key = "to_field") {
                OutlinedTextField(
                    value = state.toQuery,
                    onValueChange = viewModel::setToQuery,
                    label = { Text(stringResource(R.string.to)) },
                    modifier = Modifier.fillMaxWidth().testTag("search_to"),
                    singleLine = true,
                )
            }
            item(key = "to_suggestions") {
                SuggestionList(
                    suggestions = state.toSuggestions,
                    favoriteKeys = state.favoriteLocationKeys,
                    onSelect = viewModel::selectTo,
                    onToggleFavorite = viewModel::toggleFavoriteLocation,
                )
            }
            if (state.cachedRecent.isNotEmpty()) {
                item(key = "clear_recent") {
                    TextButton(onClick = viewModel::clearRecentLocations) {
                        Text(stringResource(R.string.clear_recent_locations))
                    }
                }
            }
            item(key = "when_field") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDateTimePicker = true },
                ) {
                    OutlinedTextField(
                        value = whenCaption,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.search_when)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_when_field"),
                    )
                }
            }
            item(key = "search_button") {
                Button(
                    onClick = { viewModel.search() },
                    modifier = Modifier.fillMaxWidth().testTag("search_button"),
                    enabled = !state.isLoading,
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Text(
                        stringResource(R.string.search_connections),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (state.from != null && state.to != null && state.hasSearched) {
                item(key = "save_favorite_route") {
                    TextButton(onClick = { viewModel.saveCurrentRouteAsFavorite() }) {
                        Text(stringResource(R.string.save_favorite_route))
                    }
                }
            }
            state.error?.let { key ->
                item(key = "error") {
                    ErrorBanner(stringResource(errorStringRes(key)))
                }
            }
            state.info?.let { key ->
                item(key = "info") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Text(
                            stringResource(infoStringRes(key)),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (state.isLoading) {
                item(key = "loading") {
                    LoadingIndicator()
                }
            }
            if (state.hasSearched && state.journeys.isNotEmpty()) {
                if (state.pagingEarlier != null) {
                    item(key = "load_earlier") {
                        OutlinedButton(
                            onClick = { viewModel.loadEarlierConnections() },
                            modifier = Modifier.fillMaxWidth().testTag("load_earlier_connections"),
                            enabled = !state.isLoading && !state.isLoadingEarlier,
                        ) {
                            if (state.isLoadingEarlier) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 8.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            Text(stringResource(R.string.load_earlier_connections))
                        }
                    }
                }
            }
            val rated = state.ratedJourneys
            val predictionsRequested = state.showPredictions && state.hasSearched
            if (rated.isNotEmpty()) {
                items(rated, key = { it.journey.id }) { ratedJourney ->
                    JourneyResultItem(
                        ratedJourney = ratedJourney,
                        predictionsRequested = predictionsRequested,
                        onOpenDetail = onOpenJourneyDetail,
                        onTrack = { viewModel.trackJourney(ratedJourney.journey, context) },
                    )
                }
            } else {
                items(state.journeys, key = { it.id }) { journey ->
                    JourneyCard(
                        journey = journey,
                        predictionsRequested = predictionsRequested,
                        onOpenFullscreen = {
                            JourneyNavigation.set(journey, predictionsRequested = predictionsRequested)
                            onOpenJourneyDetail()
                        },
                        onTrack = { viewModel.trackJourney(journey, context) },
                    )
                }
            }
            if (state.hasSearched && state.journeys.isNotEmpty() && state.pagingLater != null) {
                item(key = "load_later") {
                    OutlinedButton(
                        onClick = { viewModel.loadLaterConnections() },
                        modifier = Modifier.fillMaxWidth().testTag("load_later_connections"),
                        enabled = !state.isLoading && !state.isLoadingLater,
                    ) {
                        if (state.isLoadingLater) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(stringResource(R.string.load_later_connections))
                    }
                }
            }
            item(key = "list_bottom_spacer") {
                Spacer(Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

@Composable
private fun JourneyResultItem(
    ratedJourney: RatedJourney,
    predictionsRequested: Boolean,
    onOpenDetail: () -> Unit,
    onTrack: () -> Unit,
) {
    JourneyCard(
        journey = ratedJourney.journey,
        prediction = ratedJourney,
        predictionsRequested = predictionsRequested,
        onOpenFullscreen = {
            JourneyNavigation.set(
                ratedJourney.journey,
                prediction = ratedJourney,
                predictionsRequested = predictionsRequested,
            )
            onOpenDetail()
        },
        onTrack = onTrack,
    )
}

@Composable
private fun SuggestionList(
    suggestions: List<Location>,
    favoriteKeys: Set<String>,
    onSelect: (Location) -> Unit,
    onToggleFavorite: (Location) -> Unit,
) {
    Column {
        suggestions.take(8).forEach { loc ->
            val key = loc.stableKey()
            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag("location_suggestion_${loc.evaNumber ?: loc.id}")
                    .clickable { onSelect(loc) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    loc.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = { onToggleFavorite(loc) }) {
                    val isFav = key in favoriteKeys
                    Icon(
                        if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = stringResource(
                            if (isFav) R.string.remove_favorite_station else R.string.add_favorite_station,
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun errorStringRes(key: String): Int = when (key) {
    "error_select_stations" -> R.string.error_select_stations
    "error_api_blocked" -> R.string.error_api_blocked
    "error_network" -> R.string.error_network
    "error_parse" -> R.string.error_parse
    else -> R.string.error_search_failed
}

private fun infoStringRes(key: String): Int = when (key) {
    "info_no_connections" -> R.string.info_no_connections
    else -> R.string.info_no_connections
}
