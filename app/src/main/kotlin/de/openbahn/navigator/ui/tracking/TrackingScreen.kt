package de.openbahn.navigator.ui.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.navigator.R
import de.openbahn.navigator.navigation.JourneyNavigation
import de.openbahn.navigator.ui.components.JourneyCard
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    onOpenJourneyDetail: () -> Unit,
    viewModel: TrackingViewModel = koinViewModel(),
) {
    val tracked by viewModel.tracked.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tracking_title)) }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(stringResource(R.string.tracking_description), Modifier.padding(bottom = 16.dp))
            if (tracked.isEmpty()) {
                Text(stringResource(R.string.tracking_empty))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tracked, key = { it.entity.id }) { item ->
                        Column(Modifier.fillMaxWidth()) {
                            JourneyCard(
                                journey = item.journey,
                                predictionsRequested = true,
                                onOpenFullscreen = {
                                    JourneyNavigation.set(item.journey, predictionsRequested = true)
                                    onOpenJourneyDetail()
                                },
                            )
                            TextButton(onClick = { viewModel.stopTracking(item.entity.id) }) {
                                Text(stringResource(R.string.stop_tracking))
                            }
                        }
                    }
                }
            }
        }
    }
}
