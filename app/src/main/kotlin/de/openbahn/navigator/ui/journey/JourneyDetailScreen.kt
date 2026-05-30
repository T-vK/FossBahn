package de.openbahn.navigator.ui.journey

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.navigator.R
import de.openbahn.navigator.navigation.JourneyDetailPayload
import de.openbahn.navigator.ui.components.JourneyCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyDetailScreen(
    payload: JourneyDetailPayload,
    onBack: () -> Unit,
    onTrack: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.journey_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                JourneyCard(
                    journey = payload.journey,
                    prediction = payload.prediction,
                    predictionsRequested = payload.predictionsRequested,
                    expanded = true,
                    onTrack = onTrack,
                    modifier = Modifier.testTag("journey_detail_card"),
                )
            }
        }
    }
}
