package de.openbahn.navigator.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.navigator.R
import de.openbahn.navigator.data.FavoriteRoute
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onSearchRoute: () -> Unit,
    viewModel: FavoritesViewModel = koinViewModel(),
) {
    val routes by viewModel.routes.collectAsState()
    val stations by viewModel.favoriteLocations.collectAsState()
    var editMode by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.favorites_title)) },
                actions = {
                    if (routes.isNotEmpty() || stations.isNotEmpty()) {
                        IconButton(onClick = { editMode = !editMode }) {
                            Icon(
                                imageVector = if (editMode) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = stringResource(
                                    if (editMode) R.string.favorites_done else R.string.favorites_edit,
                                ),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(stringResource(R.string.favorites_routes_section))
            }
            if (routes.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.favorites_routes_empty),
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            } else {
                items(routes, key = { it.id }) { route ->
                    FavoriteRouteRow(
                        route = route,
                        editMode = editMode,
                        onSearch = {
                            viewModel.searchRoute(route)
                            onSearchRoute()
                        },
                        onDelete = { viewModel.deleteRoute(route.id) },
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.favorites_stations_section),
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            if (stations.isEmpty()) {
                item {
                    Text(stringResource(R.string.favorites_stations_empty))
                }
            } else {
                items(stations, key = { it.evaNumber ?: it.id }) { location ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(location.name)
                            if (editMode) {
                                TextButton(onClick = { viewModel.removeFavoriteLocation(location) }) {
                                    Text(stringResource(R.string.delete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteRouteRow(
    route: FavoriteRoute,
    editMode: Boolean,
    onSearch: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSearch, enabled = !editMode),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(route.label ?: "${route.from.name} → ${route.to.name}")
            if (editMode) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }
}
