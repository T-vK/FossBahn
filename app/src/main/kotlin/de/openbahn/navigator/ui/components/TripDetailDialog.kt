package de.openbahn.navigator.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import de.openbahn.navigator.R

@Composable
internal fun TripDetailDialog(
    leg: Leg,
    stops: List<StopEvent>?,
    isLoading: Boolean,
    fallbackStops: List<StopEvent>,
    onDismiss: () -> Unit,
) {
    val displayStops = stops?.takeIf { it.size >= 2 } ?: fallbackStops.takeIf { it.size >= 2 }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = tripDetailTitle(leg),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.timeline_direction_to, leg.destination.name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
                HorizontalDivider()
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    when {
                        isLoading -> {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator(Modifier.size(28.dp))
                            }
                        }
                        displayStops != null -> {
                            LegTimelineBlock(
                                leg = leg,
                                legIndex = 0,
                                prediction = null,
                                predictionsRequested = false,
                                routeStops = displayStops,
                                routeBoardAt = leg.origin.name,
                                routeAlightAt = leg.destination.name,
                                routeBoardScheduled = leg.origin.scheduledTime,
                                routeAlightScheduled = leg.destination.scheduledTime,
                            )
                        }
                        else -> {
                            Text(
                                stringResource(R.string.trip_detail_route_unavailable),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun tripDetailTitle(leg: Leg): String {
    val line = leg.lineName?.takeIf { it.isNotBlank() }
    val trip = leg.lineDetail?.takeIf { it.isNotBlank() }
    return when {
        line != null && trip != null -> "$line ($trip)"
        line != null -> line
        trip != null -> trip
        else -> ""
    }
}
