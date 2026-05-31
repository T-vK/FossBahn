package de.openbahn.navigator.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.model.AccessibilityFilter
import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.RoutingMode
import de.openbahn.model.TransportProduct
import de.openbahn.navigator.R
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersScreen(
    onBack: () -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val options = state.options
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.filters)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.filter_transport), style = MaterialTheme.typography.titleMedium)
            TransportProduct.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { product ->
                        val selected = product in options.products
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val newProducts = if (selected) options.products - product else options.products + product
                                viewModel.updateOptions(options.copy(products = newProducts.ifEmpty { TransportProduct.ALL }))
                            },
                            label = { Text(product.name) },
                        )
                    }
                }
            }

            ToggleRow(stringResource(R.string.filter_bike), options.bikeCarriage) {
                viewModel.updateOptions(options.copy(bikeCarriage = it))
            }
            ToggleRow(stringResource(R.string.filter_direct), options.directOnly) {
                viewModel.updateOptions(options.copy(directOnly = it, maxTransfers = if (it) 0 else options.maxTransfers))
            }
            ToggleRow(stringResource(R.string.filter_dticket_only), options.deutschlandTicketConnectionsOnly) {
                viewModel.updateOptions(options.copy(deutschlandTicketConnectionsOnly = it))
            }
            ToggleRow(stringResource(R.string.filter_dticket_owned), options.deutschlandTicketOwned) {
                viewModel.updateOptions(options.copy(deutschlandTicketOwned = it))
            }
            ToggleRow(stringResource(R.string.filter_fast_routes), options.fastRoutesOnly) {
                viewModel.updateOptions(options.copy(fastRoutesOnly = it))
            }
            Text(stringResource(R.string.filter_transfer_time), style = MaterialTheme.typography.titleMedium)
            listOf(null, 5, 10, 15, 20, 30).forEach { minutes ->
                FilterChip(
                    selected = options.minTransferMinutes == minutes,
                    onClick = { viewModel.updateOptions(options.copy(minTransferMinutes = minutes)) },
                    label = {
                        Text(
                            when (minutes) {
                                null -> stringResource(R.string.filter_transfer_default)
                                else -> stringResource(R.string.filter_transfer_minutes, minutes)
                            },
                        )
                    },
                )
            }

            Text(stringResource(R.string.filter_accessibility), style = MaterialTheme.typography.titleMedium)
            AccessibilityFilter.entries.forEach { filter ->
                FilterChip(
                    selected = options.accessibility == filter,
                    onClick = { viewModel.updateOptions(options.copy(accessibility = filter)) },
                    label = { Text(filter.name) },
                )
            }

            Text(stringResource(R.string.filter_routing), style = MaterialTheme.typography.titleMedium)
            RoutingMode.entries.forEach { mode ->
                FilterChip(
                    selected = options.routingMode == mode,
                    onClick = { viewModel.updateOptions(options.copy(routingMode = mode)) },
                    label = { Text(mode.name) },
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
