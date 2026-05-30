package de.openbahn.navigator.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.openbahn.model.BoardEntry
import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.RatedJourney
import de.openbahn.model.StopEvent
import de.openbahn.navigator.R
import de.openbahn.navigator.ui.util.formatDurationMinutes
import de.openbahn.navigator.ui.util.formatJourneyClock

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
fun JourneyCard(
    journey: Journey,
    prediction: RatedJourney? = null,
    onTrack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("journey_card"),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${formatJourneyClock(journey.departure)} – ${formatJourneyClock(journey.arrival)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    formatDurationMinutes(journey.durationMinutes),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                "${journey.legs.firstOrNull()?.origin?.name} → ${journey.legs.lastOrNull()?.destination?.name}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.transfers_count, journey.transfers),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            journey.priceHint?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (journey.deutschlandTicketValid == true) {
                Text(
                    stringResource(R.string.dticket_valid),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            HorizontalDivider()

            journey.legs.forEachIndexed { index, leg ->
                LegDetailsBlock(leg = leg, legIndex = index)
                if (index < journey.legs.lastIndex) {
                    TransferBlock(
                        fromLeg = leg,
                        toLeg = journey.legs[index + 1],
                        prediction = prediction?.predictions?.getOrNull(index),
                    )
                }
            }

            val anyDelay = journey.legs.any { leg ->
                (leg.origin.delayMinutes ?: 0) > 0 || (leg.destination.delayMinutes ?: 0) > 0
            }
            if (anyDelay) {
                journey.legs.forEach { leg ->
                    val delay = leg.destination.delayMinutes ?: leg.origin.delayMinutes
                    if ((delay ?: 0) > 0) {
                        Text(
                            stringResource(R.string.delay_minutes, delay!!),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            onTrack?.let {
                TextButton(onClick = it, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.track_journey))
                }
            }
        }
    }
}

@Composable
private fun LegDetailsBlock(leg: Leg, legIndex: Int) {
    leg.lineName?.let { line ->
        Text(
            line,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    StopRow(
        label = stringResource(R.string.departure),
        stop = leg.origin,
        time = formatJourneyClock(leg.origin.scheduledTime),
        modifier = Modifier.testTag("leg_${legIndex}_departure"),
    )
    if (leg.intermediateStops.isNotEmpty()) {
        IntermediateStopsBlock(stops = leg.intermediateStops, legIndex = legIndex)
    }
    StopRow(
        label = stringResource(R.string.arrival),
        stop = leg.destination,
        time = formatJourneyClock(leg.destination.scheduledTime),
        modifier = Modifier.testTag("leg_${legIndex}_arrival"),
    )
}

@Composable
private fun StopRow(
    label: String,
    stop: StopEvent,
    time: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stop.name, style = MaterialTheme.typography.bodyMedium)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(time, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            stop.platform?.let { platform ->
                Text(
                    stringResource(R.string.platform_label, platform),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun IntermediateStopsBlock(stops: List<StopEvent>, legIndex: Int) {
    var expanded by remember(legIndex, stops.size) { mutableStateOf(false) }
    Text(
        text = if (expanded) {
            stringResource(R.string.hide_intermediate_stations)
        } else {
            stringResource(R.string.show_intermediate_stations, stops.size)
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable { expanded = !expanded }
            .padding(vertical = 2.dp)
            .testTag("leg_${legIndex}_intermediate_toggle"),
    )
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        Column(
            Modifier.padding(start = 8.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            stops.forEach { stop ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stop.name, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (stop.scheduledTime.isNotBlank()) {
                            Text(
                                formatJourneyClock(stop.scheduledTime),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        stop.platform?.let {
                            Text(
                                stringResource(R.string.platform_label, it),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferBlock(
    fromLeg: Leg,
    toLeg: Leg,
    prediction: de.openbahn.model.TransferPrediction?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HorizontalDivider()
        val station = fromLeg.destination.name
        Text(
            stringResource(R.string.transfer_at, station),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            fromLeg.destination.platform?.let { platform ->
                Text(
                    stringResource(R.string.platform_arrival_short, platform),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            toLeg.origin.platform?.let { platform ->
                Text(
                    stringResource(R.string.platform_departure_short, platform),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        val probability = prediction?.successProbability
        if (probability != null) {
            Text(
                stringResource(R.string.transfer_probability, (probability * 100).toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.testTag("transfer_prediction"),
            )
        } else {
            Text(
                stringResource(R.string.prediction_unavailable),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
    }
}

@Composable
fun BoardEntryRow(entry: BoardEntry, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(entry.line, style = MaterialTheme.typography.titleMedium)
                Text(entry.direction, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(entry.prognosedTime?.let { formatJourneyClock(it) } ?: formatJourneyClock(entry.scheduledTime))
                entry.platform?.let {
                    Text(
                        stringResource(R.string.platform_label, it),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                entry.delayMinutes?.takeIf { it > 0 }?.let {
                    Text("+$it", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
