package de.openbahn.navigator.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.navigator.R
import de.openbahn.navigator.ui.components.ErrorBanner
import de.openbahn.navigator.ui.components.JourneyCard
import de.openbahn.navigator.ui.components.LoadingIndicator
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenFilters: () -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                actions = {
                    IconButton(onClick = onOpenFilters) {
                        Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.filters))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.search() }) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.fromQuery,
                onValueChange = viewModel::setFromQuery,
                label = { Text(stringResource(R.string.from)) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        val from = state.from
                        val to = state.to
                        if (from != null && to != null) {
                            viewModel.selectFrom(to)
                            viewModel.selectTo(from)
                        }
                    }) { Icon(Icons.Default.SwapVert, null) }
                },
            )
            state.fromSuggestions.take(5).forEach { loc ->
                Text(loc.name, Modifier.fillMaxWidth().clickable { viewModel.selectFrom(loc) }.padding(8.dp))
            }
            OutlinedTextField(
                value = state.toQuery,
                onValueChange = viewModel::setToQuery,
                label = { Text(stringResource(R.string.to)) },
                modifier = Modifier.fillMaxWidth(),
            )
            state.toSuggestions.take(5).forEach { loc ->
                Text(loc.name, Modifier.fillMaxWidth().clickable { viewModel.selectTo(loc) }.padding(8.dp))
            }
            state.error?.let { ErrorBanner(it) }
            if (state.isLoading) LoadingIndicator()
            LazyColumn(contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val rated = state.ratedJourneys
                if (rated.isNotEmpty()) {
                    items(rated, key = { it.journey.id }) { ratedJourney ->
                        JourneyCard(
                            journey = ratedJourney.journey,
                            prediction = ratedJourney,
                            onTrack = { viewModel.trackJourney(ratedJourney.journey, context) },
                        )
                    }
                } else {
                    items(state.journeys, key = { it.id }) { journey ->
                        JourneyCard(
                            journey = journey,
                            onTrack = { viewModel.trackJourney(journey, context) },
                        )
                    }
                }
            }
        }
    }
}
