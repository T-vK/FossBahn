package de.openbahn.navigator.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.TextButton
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenJourneyDetail: () -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val dismissKeyboard = rememberDismissSearchKeyboard()
    var pendingLocationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants.values.any { it }
        if (granted) {
            pendingLocationAction?.invoke()
        } else {
            viewModel.reportLocationPermissionDenied()
        }
        pendingLocationAction = null
    }

    fun requestLocationThen(action: () -> Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            action()
        } else {
            pendingLocationAction = action
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    LaunchedEffect(state.scrollToResultsToken) {
        if (state.scrollToResultsToken == 0L) return@LaunchedEffect
        val index = firstResultListIndex(state) ?: return@LaunchedEffect
        listState.animateScrollToItem(index)
    }

    LaunchedEffect(state.isLoading) {
        if (state.isLoading) dismissKeyboard()
    }

    if (state.showOnboarding) {
        DeutschlandTicketOnboardingDialog(
            onDismissOnly = viewModel::dismissOnboarding,
            onEnableFilter = { viewModel.completeOnboarding(deutschlandTicketOnly = true) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.testTag("search_screen_title"),
                title = { Text(stringResource(R.string.search_title)) },
                actions = {
                    IconButton(onClick = {
                        dismissKeyboard()
                        onOpenSettings()
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                    IconButton(onClick = {
                        dismissKeyboard()
                        onOpenFilters()
                    }) {
                        Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.filters))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .dismissKeyboardOnTap(dismissKeyboard)
                .testTag("search_results"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "from_field") {
                SearchLocationField(
                    value = state.fromQuery,
                    onValueChange = viewModel::setFromQuery,
                    label = stringResource(R.string.from),
                    onFocusChanged = viewModel::onFromFocusChanged,
                    onUseCurrentLocation = {
                        dismissKeyboard()
                        requestLocationThen { viewModel.useCurrentLocationForFrom() }
                    },
                    onImeDone = dismissKeyboard,
                    testTag = "search_from",
                    leadingTrailing = {
                        IconButton(onClick = {
                            dismissKeyboard()
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
            if (state.activeLocationField == ActiveLocationField.FROM && state.fromSuggestions.isNotEmpty()) {
                item(key = "from_suggestions") {
                    SuggestionList(
                        suggestions = state.fromSuggestions,
                        favoriteKeys = state.favoriteLocationKeys,
                        showSectionHeaders = state.fromQuery.isBlank(),
                        onSelect = { loc ->
                            dismissKeyboard()
                            viewModel.selectFrom(loc)
                        },
                        onToggleFavorite = viewModel::toggleFavoriteLocation,
                    )
                }
                if (state.cachedRecent.isNotEmpty()) {
                    item(key = "clear_recent_from") {
                        TextButton(onClick = {
                            dismissKeyboard()
                            viewModel.clearRecentLocations()
                        }) {
                            Text(stringResource(R.string.clear_recent_locations))
                        }
                    }
                }
            }
            item(key = "to_field") {
                SearchLocationField(
                    value = state.toQuery,
                    onValueChange = viewModel::setToQuery,
                    label = stringResource(R.string.to),
                    onFocusChanged = viewModel::onToFocusChanged,
                    onUseCurrentLocation = {
                        dismissKeyboard()
                        requestLocationThen { viewModel.useCurrentLocationForTo() }
                    },
                    onImeDone = dismissKeyboard,
                    testTag = "search_to",
                )
            }
            item(key = "via_toggle") {
                TextButton(onClick = {
                    dismissKeyboard()
                    viewModel.toggleViaStopsEditor()
                }) {
                    Text(
                        if (state.showViaStopsEditor) {
                            stringResource(R.string.search_hide_via_stops)
                        } else {
                            stringResource(R.string.search_add_via_stop)
                        },
                    )
                }
            }
            if (state.showViaStopsEditor) {
                state.viaStops.forEachIndexed { index, via ->
                    item(key = "via_field_$index") {
                        SearchLocationField(
                            value = via.query,
                            onValueChange = { viewModel.setViaQuery(index, it) },
                            label = stringResource(R.string.search_via_stop, index + 1),
                            onFocusChanged = { focused -> viewModel.onViaFocusChanged(index, focused) },
                            onUseCurrentLocation = {
                                dismissKeyboard()
                                requestLocationThen { viewModel.useCurrentLocationForVia(index) }
                            },
                            onImeDone = dismissKeyboard,
                            testTag = "search_via_$index",
                            leadingTrailing = {
                                IconButton(onClick = {
                                    dismissKeyboard()
                                    viewModel.removeViaStop(index)
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove))
                                }
                            },
                        )
                    }
                }
                if (state.activeViaIndex != null && state.viaSuggestions.isNotEmpty()) {
                    item(key = "via_suggestions") {
                        SuggestionList(
                            suggestions = state.viaSuggestions,
                            favoriteKeys = state.favoriteLocationKeys,
                            showSectionHeaders = false,
                            onSelect = { loc ->
                                dismissKeyboard()
                                viewModel.selectVia(state.activeViaIndex!!, loc)
                            },
                            onToggleFavorite = viewModel::toggleFavoriteLocation,
                        )
                    }
                }
                item(key = "via_add") {
                    TextButton(onClick = {
                        dismissKeyboard()
                        viewModel.addViaStop()
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text(stringResource(R.string.search_add_another_via))
                        }
                    }
                }
            }
            if (state.activeLocationField == ActiveLocationField.TO && state.toSuggestions.isNotEmpty()) {
                item(key = "to_suggestions") {
                    SuggestionList(
                        suggestions = state.toSuggestions,
                        favoriteKeys = state.favoriteLocationKeys,
                        showSectionHeaders = state.toQuery.isBlank(),
                        onSelect = { loc ->
                            dismissKeyboard()
                            viewModel.selectTo(loc)
                        },
                        onToggleFavorite = viewModel::toggleFavoriteLocation,
                    )
                }
                if (state.cachedRecent.isNotEmpty()) {
                    item(key = "clear_recent_to") {
                        TextButton(onClick = {
                            dismissKeyboard()
                            viewModel.clearRecentLocations()
                        }) {
                            Text(stringResource(R.string.clear_recent_locations))
                        }
                    }
                }
            }
            item(key = "when_section") {
                SearchWhenSection(
                    departureTime = state.departureTime,
                    arrivalSearch = state.options.arrivalSearch,
                    onDepartureTimeChange = viewModel::setDepartureTime,
                    onArrivalSearchChange = { arrival ->
                        viewModel.updateOptions(state.options.copy(arrivalSearch = arrival))
                    },
                    onDismissKeyboard = dismissKeyboard,
                    modifier = Modifier.testTag("search_when_section"),
                )
            }
            item(key = "search_button") {
                Button(
                    onClick = {
                        dismissKeyboard()
                        viewModel.search()
                    },
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
                    TextButton(onClick = {
                        dismissKeyboard()
                        viewModel.saveCurrentRouteAsFavorite()
                    }) {
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
                        PagingConnectionsButton(
                            label = stringResource(R.string.load_earlier_connections),
                            onClick = {
                                dismissKeyboard()
                                viewModel.loadEarlierConnections()
                            },
                            enabled = !state.isLoading && !state.isLoadingEarlier,
                            isLoading = state.isLoadingEarlier,
                            testTag = "load_earlier_connections",
                        )
                    }
                }
            }
            val rated = state.ratedJourneys
            val predictionsRequested = state.showPredictions && state.hasSearched
            val minTransferMinutes = state.options.minTransferMinutes
            if (rated.isNotEmpty()) {
                items(rated, key = { it.journey.id }) { ratedJourney ->
                    JourneyResultItem(
                        ratedJourney = ratedJourney,
                        predictionsRequested = predictionsRequested,
                        minTransferMinutes = minTransferMinutes,
                        onOpenDetail = onOpenJourneyDetail,
                        onTrack = { viewModel.trackJourney(ratedJourney.journey) },
                        dismissKeyboard = dismissKeyboard,
                    )
                }
            } else {
                items(state.journeys, key = { it.id }) { journey ->
                    JourneyCard(
                        journey = journey,
                        predictionsRequested = predictionsRequested,
                        onOpenFullscreen = {
                            dismissKeyboard()
                            JourneyNavigation.set(
                                journey,
                                predictionsRequested = predictionsRequested,
                                minTransferMinutes = minTransferMinutes,
                            )
                            onOpenJourneyDetail()
                        },
                        onTrack = {
                            dismissKeyboard()
                            viewModel.trackJourney(journey)
                        },
                    )
                }
            }
            if (state.hasSearched && state.journeys.isNotEmpty() && state.pagingLater != null) {
                item(key = "load_later") {
                    PagingConnectionsButton(
                        label = stringResource(R.string.load_later_connections),
                        onClick = {
                            dismissKeyboard()
                            viewModel.loadLaterConnections()
                        },
                        enabled = !state.isLoading && !state.isLoadingLater,
                        isLoading = state.isLoadingLater,
                        testTag = "load_later_connections",
                    )
                }
            }
            item(key = "list_bottom_spacer") {
                Spacer(Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

@Composable
private fun PagingConnectionsButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    testTag: String,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .testTag(testTag),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) {
        if (isLoading) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(16.dp),
                strokeWidth = 2.dp,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** List index of the first connection card, matching [SearchScreen] item order. */
private fun firstResultListIndex(state: SearchUiState): Int? {
    if (!state.hasSearched || state.isLoading) return null
    if (state.journeys.isEmpty() && state.ratedJourneys.isEmpty()) return null

    var index = 0
    index++ // from_field
    if (state.activeLocationField == ActiveLocationField.FROM && state.fromSuggestions.isNotEmpty()) {
        index++
        if (state.cachedRecent.isNotEmpty()) index++
    }
    index++ // to_field
    if (state.activeLocationField == ActiveLocationField.TO && state.toSuggestions.isNotEmpty()) {
        index++
        if (state.cachedRecent.isNotEmpty()) index++
    }
    index++ // when_section
    index++ // search_button
    if (state.from != null && state.to != null && state.hasSearched) index++
    if (state.error != null) index++
    if (state.info != null) index++
    if (state.isLoading) index++
    if (state.hasSearched && state.journeys.isNotEmpty() && state.pagingEarlier != null) index++
    return index
}

@Composable
private fun JourneyResultItem(
    ratedJourney: RatedJourney,
    predictionsRequested: Boolean,
    minTransferMinutes: Int?,
    onOpenDetail: () -> Unit,
    onTrack: () -> Unit,
    dismissKeyboard: () -> Unit,
) {
    JourneyCard(
        journey = ratedJourney.journey,
        prediction = ratedJourney,
        predictionsRequested = predictionsRequested,
        onOpenFullscreen = {
            dismissKeyboard()
            JourneyNavigation.set(
                ratedJourney.journey,
                prediction = ratedJourney,
                predictionsRequested = predictionsRequested,
                minTransferMinutes = minTransferMinutes,
            )
            onOpenDetail()
        },
        onTrack = {
            dismissKeyboard()
            onTrack()
        },
    )
}

@Composable
private fun SuggestionList(
    suggestions: List<Location>,
    favoriteKeys: Set<String>,
    showSectionHeaders: Boolean,
    onSelect: (Location) -> Unit,
    onToggleFavorite: (Location) -> Unit,
) {
    val limited = suggestions.take(8)
    val favorites = limited.filter { it.stableKey() in favoriteKeys }
    val others = limited.filter { it.stableKey() !in favoriteKeys }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (showSectionHeaders && favorites.isNotEmpty()) {
            Text(
                stringResource(R.string.suggestions_favorites),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            favorites.forEach { loc ->
                SuggestionRow(loc, favoriteKeys, onSelect, onToggleFavorite)
            }
        }
        if (showSectionHeaders && others.isNotEmpty()) {
            Text(
                stringResource(R.string.suggestions_recent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = if (favorites.isNotEmpty()) 8.dp else 4.dp),
            )
            others.forEach { loc ->
                SuggestionRow(loc, favoriteKeys, onSelect, onToggleFavorite)
            }
        }
        if (!showSectionHeaders) {
            limited.forEach { loc ->
                SuggestionRow(loc, favoriteKeys, onSelect, onToggleFavorite)
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    loc: Location,
    favoriteKeys: Set<String>,
    onSelect: (Location) -> Unit,
    onToggleFavorite: (Location) -> Unit,
) {
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

private fun errorStringRes(key: String): Int = when (key) {
    "error_select_stations" -> R.string.error_select_stations
    "error_api_blocked" -> R.string.error_api_blocked
    "error_network" -> R.string.error_network
    "error_parse" -> R.string.error_parse
    "error_location_permission" -> R.string.error_location_permission
    "error_location_unavailable" -> R.string.error_location_unavailable
    else -> R.string.error_search_failed
}

private fun infoStringRes(key: String): Int = when (key) {
    "info_no_connections" -> R.string.info_no_connections
    "info_no_connections_dticket" -> R.string.info_no_connections_dticket
    "info_no_connections_filters" -> R.string.info_no_connections_filters
    else -> R.string.info_no_connections
}
