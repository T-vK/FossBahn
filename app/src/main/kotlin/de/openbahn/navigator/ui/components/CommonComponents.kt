package de.openbahn.navigator.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.openbahn.model.BoardEntry
import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.api.JourneyRatingOptions
import de.openbahn.model.RatedJourney
import de.openbahn.model.StopEvent
import de.openbahn.model.delayMinutesFromTimes
import de.openbahn.navigator.R
import de.openbahn.navigator.ui.util.formatDurationMinutes
import de.openbahn.navigator.ui.util.formatJourneyClock
import de.openbahn.navigator.ui.util.journeyBookingUri

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
    predictionsRequested: Boolean = false,
    expanded: Boolean = false,
    onOpenFullscreen: (() -> Unit)? = null,
    onTrack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var detailsExpanded by remember(journey.id) { mutableStateOf(expanded) }
    val context = LocalContext.current
    val bookingUri = remember(journey.id, journey.refreshToken) { journeyBookingUri(journey) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("journey_card")
            .then(
                if (onOpenFullscreen != null) {
                    Modifier.clickable(onClick = onOpenFullscreen)
                } else {
                    Modifier
                },
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                JourneyTimeRangeHeader(journey = journey)
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
            if (predictionsRequested && prediction != null) {
                JourneyPredictionSummary(
                    journey = journey,
                    prediction = prediction,
                )
            }
            journey.priceHint?.let { price ->
                Text(
                    price,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .then(
                            if (bookingUri != null) {
                                Modifier.clickable {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, bookingUri),
                                    )
                                }
                            } else {
                                Modifier
                            },
                        )
                        .testTag("journey_price"),
                )
            }
            if (journey.deutschlandTicketValid == true) {
                Text(
                    stringResource(R.string.dticket_valid),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            if (journey.remarks.isNotEmpty()) {
                RemarksBlock(remarks = journey.remarks)
            }

            if (!expanded) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { detailsExpanded = !detailsExpanded }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (detailsExpanded) {
                            stringResource(R.string.hide_journey_details)
                        } else {
                            stringResource(R.string.show_journey_details)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded || detailsExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider()
                    journey.legs.forEachIndexed { index, leg ->
                        LegDetailsBlock(leg = leg, legIndex = index)
                        if (index < journey.legs.lastIndex) {
                            TransferBlock(
                                fromLeg = leg,
                                toLeg = journey.legs[index + 1],
                                prediction = prediction?.predictions?.getOrNull(index),
                                predictionsRequested = predictionsRequested,
                                minTransferMinutesUsed = prediction?.minTransferMinutesUsed,
                            )
                        }
                    }
                    if (predictionsRequested) {
                        PunctualityBlock(
                            probability = prediction?.punctualityProbability,
                            isEstimate = prediction?.punctualityIsEstimate == true,
                            toleranceMinutes = prediction?.punctualityToleranceMinutes
                                ?: JourneyRatingOptions.DEFAULT_PUNCTUALITY_TOLERANCE_MINUTES,
                        )
                    }
                }
            }

            onTrack?.let {
                TextButton(
                    onClick = it,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.track_journey))
                }
            }
        }
    }
}

@Composable
private fun RemarksBlock(remarks: List<String>) {
    var expanded by remember(remarks) { mutableStateOf(false) }
    Text(
        if (expanded) stringResource(R.string.hide_remarks) else stringResource(R.string.show_remarks, remarks.size),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { expanded = !expanded },
    )
    AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            remarks.forEach { remark ->
                Text(remark, style = MaterialTheme.typography.bodySmall)
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
        modifier = Modifier.testTag("leg_${legIndex}_departure"),
    )
    if (leg.intermediateStops.isNotEmpty()) {
        IntermediateStopsBlock(stops = leg.intermediateStops, legIndex = legIndex)
    }
    StopRow(
        label = stringResource(R.string.arrival),
        stop = leg.destination,
        modifier = Modifier.testTag("leg_${legIndex}_arrival"),
    )
}

@Composable
private fun JourneyTimeRangeHeader(journey: Journey) {
    val first = journey.legs.firstOrNull()?.origin
    val last = journey.legs.lastOrNull()?.destination
    val depScheduled = first?.scheduledTime ?: journey.departure
    val arrScheduled = last?.scheduledTime ?: journey.arrival
    val depDelay = first?.delayMinutes
        ?: delayMinutesFromTimes(depScheduled, first?.prognosedTime)
        ?: 0
    val arrDelay = last?.delayMinutes
        ?: delayMinutesFromTimes(arrScheduled, last?.prognosedTime)
        ?: 0
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            StopTimeText(
                scheduled = depScheduled,
                prognosed = first?.prognosedTime,
                delayMinutes = depDelay,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text("–", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            StopTimeText(
                scheduled = arrScheduled,
                prognosed = last?.prognosedTime,
                delayMinutes = arrDelay,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun StopRow(
    label: String,
    stop: StopEvent,
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
            StopTimeText(
                scheduled = stop.scheduledTime,
                prognosed = stop.prognosedTime,
                delayMinutes = stop.delayMinutes
                    ?: delayMinutesFromTimes(stop.scheduledTime, stop.prognosedTime)
                    ?: 0,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
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
                            StopTimeText(
                                scheduled = stop.scheduledTime,
                                prognosed = stop.prognosedTime,
                                delayMinutes = stop.delayMinutes ?: 0,
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
private fun JourneyPredictionSummary(
    journey: Journey,
    prediction: RatedJourney,
) {
    val percent = { value: Double -> (value * 100).toInt().coerceIn(0, 100) }
    val tolerance = prediction.punctualityToleranceMinutes
        ?: JourneyRatingOptions.DEFAULT_PUNCTUALITY_TOLERANCE_MINUTES
    val text = when {
        journey.transfers > 0 -> {
            val worst = prediction.predictions.mapNotNull { it.successProbability }.minOrNull()
            if (worst == null) return
            val buffer = prediction.minTransferMinutesUsed
            if (buffer != null) {
                stringResource(R.string.prediction_summary_transfer_buffer, buffer, percent(worst))
            } else {
                stringResource(R.string.prediction_summary_transfer, percent(worst))
            }
        }
        prediction.punctualityProbability != null -> {
            val p = prediction.punctualityProbability!!
            if (tolerance == 0) {
                stringResource(R.string.prediction_summary_punctuality_exact, percent(p))
            } else {
                stringResource(R.string.prediction_summary_punctuality, tolerance, percent(p))
            }
        }
        else -> return
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("journey_prediction_summary"),
    )
}

@Composable
private fun PunctualityBlock(
    probability: Double?,
    isEstimate: Boolean,
    toleranceMinutes: Int,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HorizontalDivider()
        Text(
            stringResource(R.string.direct_connection),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        val probabilityPercent = probability
        when {
            probabilityPercent != null -> {
                val percent = (probabilityPercent * 100).toInt().coerceIn(0, 100)
                val label = when {
                    toleranceMinutes == 0 && isEstimate ->
                        stringResource(R.string.punctuality_probability_estimate_exact, percent)
                    toleranceMinutes == 0 ->
                        stringResource(R.string.punctuality_probability_exact, percent)
                    isEstimate ->
                        stringResource(R.string.punctuality_probability_estimate, toleranceMinutes, percent)
                    else ->
                        stringResource(R.string.punctuality_probability, toleranceMinutes, percent)
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        probabilityPercent >= 0.8 -> MaterialTheme.colorScheme.tertiary
                        probabilityPercent >= 0.5 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.testTag("journey_punctuality"),
                )
            }
            else -> {
                Text(
                    stringResource(R.string.prediction_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("journey_punctuality"),
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun TransferBlock(
    fromLeg: Leg,
    toLeg: Leg,
    prediction: de.openbahn.model.TransferPrediction?,
    predictionsRequested: Boolean,
    minTransferMinutesUsed: Int?,
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
        when {
            probability != null -> {
                val percent = (probability * 100).toInt().coerceIn(0, 100)
                val label = when {
                    minTransferMinutesUsed != null && prediction?.isEstimate == true ->
                        stringResource(
                            R.string.transfer_probability_estimate_with_buffer,
                            minTransferMinutesUsed,
                            percent,
                        )
                    minTransferMinutesUsed != null ->
                        stringResource(
                            R.string.transfer_probability_with_buffer,
                            minTransferMinutesUsed,
                            percent,
                        )
                    prediction?.isEstimate == true ->
                        stringResource(R.string.transfer_probability_estimate, percent)
                    else ->
                        stringResource(R.string.transfer_probability, percent)
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        probability >= 0.8 -> MaterialTheme.colorScheme.tertiary
                        probability >= 0.5 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.testTag("transfer_prediction"),
                )
            }
            predictionsRequested -> {
                Text(
                    stringResource(R.string.prediction_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("transfer_prediction"),
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun StopTimeText(
    scheduled: String,
    prognosed: String?,
    delayMinutes: Int,
    style: androidx.compose.ui.text.TextStyle,
    fontWeight: FontWeight? = null,
) {
    val effectiveDelay = delayMinutes.takeIf { it > 0 }
        ?: delayMinutesFromTimes(scheduled, prognosed)
        ?: 0
    val display = prognosed?.takeIf { it.isNotBlank() } ?: scheduled
    val showScheduledStruck = prognosed != null && prognosed != scheduled
    Column(horizontalAlignment = Alignment.End) {
        if (showScheduledStruck) {
            Text(
                stringResource(R.string.scheduled_time_short, formatJourneyClock(scheduled)),
                style = MaterialTheme.typography.labelSmall,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            formatJourneyClock(display),
            style = style,
            fontWeight = fontWeight,
            color = if (effectiveDelay > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (effectiveDelay > 0) {
            Text(
                stringResource(R.string.delay_minutes, effectiveDelay),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
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
