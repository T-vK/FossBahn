package de.openbahn.navigator.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.model.BoardEntry
import de.openbahn.model.Journey
import de.openbahn.model.RatedJourney
import de.openbahn.navigator.R

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
        modifier = modifier.fillMaxWidth().testTag("journey_card_${journey.id}"),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(journey.departure.takeLast(8).ifEmpty { journey.departure }, style = MaterialTheme.typography.titleMedium)
                Text("${journey.durationMinutes} min", style = MaterialTheme.typography.bodyMedium)
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
            prediction?.predictions?.firstOrNull { it.successProbability != null }?.let { p ->
                val pct = ((p.successProbability ?: 0.0) * 100).toInt()
                Text(
                    stringResource(R.string.transfer_probability, pct),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
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
            onTrack?.let {
                androidx.compose.material3.TextButton(onClick = it) {
                    Text(stringResource(R.string.track_journey))
                }
            }
        }
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
                Text(entry.prognosedTime?.takeLast(8) ?: entry.scheduledTime.takeLast(8))
                entry.platform?.let { Text("Pl. $it", style = MaterialTheme.typography.labelSmall) }
                entry.delayMinutes?.takeIf { it > 0 }?.let {
                    Text("+$it", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
