package de.openbahn.navigator.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.navigator.R
import de.openbahn.navigator.ui.components.BoardEntryRow
import de.openbahn.navigator.ui.components.ErrorBanner
import de.openbahn.navigator.ui.components.LoadingIndicator
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(viewModel: StationBoardViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.board_title)) }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.station)) },
                modifier = Modifier.fillMaxWidth(),
            )
            state.suggestions.take(5).forEach { loc ->
                Text(loc.name, Modifier.fillMaxWidth().clickable { viewModel.selectStation(loc) }.padding(8.dp))
            }
            SingleChoiceSegmentedButtonRow(Modifier.padding(vertical = 8.dp)) {
                SegmentedButton(
                    selected = !state.showArrivals,
                    onClick = { viewModel.toggleArrivals(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.departures)) }
                SegmentedButton(
                    selected = state.showArrivals,
                    onClick = { viewModel.toggleArrivals(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.arrivals)) }
            }
            state.error?.let { ErrorBanner(it, Modifier.padding(bottom = 8.dp)) }
            if (state.isLoading) LoadingIndicator()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(viewModel.currentEntries(), key = { "${it.line}-${it.scheduledTime}" }) { entry ->
                    BoardEntryRow(entry)
                }
            }
        }
    }
}
