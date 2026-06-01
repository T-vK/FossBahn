package de.openbahn.navigator.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
import de.openbahn.model.railTransferCount
import de.openbahn.api.JourneyRatingOptions
import de.openbahn.model.RatedJourney
import de.openbahn.model.StopEvent
import de.openbahn.model.stopProbability
import de.openbahn.model.stopTimelinessIsEstimate
import de.openbahn.model.delayMinutesFromTimes
import de.openbahn.model.tripRouteStops
import de.openbahn.model.withDelaysFrom
import de.openbahn.navigator.R
import de.openbahn.navigator.domain.JourneySearchRepository
import org.koin.compose.koinInject
import de.openbahn.navigator.ui.util.NavigateToStopIconButton
import de.openbahn.navigator.ui.util.ShareJourneyIconButton
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
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JourneyTimeRangeHeader(
                    journey = journey,
                    prediction = prediction,
                    predictionsRequested = predictionsRequested,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShareJourneyIconButton(
                        journey = journey,
                        prediction = prediction,
                        predictionsRequested = predictionsRequested,
                    )
                    Text(
                        formatDurationMinutes(journey.durationMinutes),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Text(
                "${journey.legs.firstOrNull()?.origin?.name} → ${journey.legs.lastOrNull()?.destination?.name}",
                style = MaterialTheme.typography.bodyLarge,
            )
            val walkLegs = journey.legs.count { it.isWalking }
            val transferLine = if (walkLegs > 0) {
                stringResource(
                    R.string.transfers_with_walks,
                    journey.railTransferCount(),
                    walkLegs,
                )
            } else {
                stringResource(R.string.transfers_count, journey.railTransferCount())
            }
            Text(
                transferLine,
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.ConfirmationNumber,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        stringResource(R.string.dticket_valid),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
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
                        if (leg.isWalking) {
                            WalkingTimelineBlock(leg = leg)
                        } else {
                            LegDetailsBlock(
                                leg = leg,
                                legIndex = index,
                                prediction = prediction,
                                predictionsRequested = predictionsRequested,
                            )
                        }
                        LegRemarksBlock(remarks = leg.remarks)
                        val next = journey.legs.getOrNull(index + 1)
                        if (next != null && !leg.isWalking && !next.isWalking) {
                            TransferTimelineBlock(
                                fromLeg = leg,
                                toLeg = next,
                                prediction = prediction?.predictions?.getOrNull(index),
                                predictionsRequested = predictionsRequested,
                                minTransferMinutesUsed = prediction?.minTransferMinutesUsed,
                            )
                        }
                    }
                }
            }

            if (onTrack != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onTrack) {
                        Text(stringResource(R.string.track_journey))
                    }
                }
            }
        }
    }
}

@Composable
private fun RemarksBlock(remarks: List<String>) {
    var expanded by remember(remarks) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .clickable { expanded = !expanded }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            if (expanded) stringResource(R.string.hide_remarks) else stringResource(R.string.show_remarks, remarks.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            remarks.forEach { remark ->
                Text(remark, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LegRemarksBlock(remarks: List<String>) {
    if (remarks.isEmpty()) return
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        remarks.forEach { remark ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    remark,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("leg_remark"),
                )
            }
        }
    }
}

@Composable
private fun LegDetailsBlock(
    leg: Leg,
    legIndex: Int,
    prediction: RatedJourney?,
    predictionsRequested: Boolean,
) {
    val journeySearch: JourneySearchRepository = koinInject()
    val scope = rememberCoroutineScope()
    val segmentStops = leg.tripRouteStops()
    var showTripDetail by remember(legIndex, leg.tripId, leg.lineDetail) { mutableStateOf(false) }
    var loadedStops by remember(legIndex, leg.tripId) { mutableStateOf<List<StopEvent>?>(null) }
    var routeLoading by remember(legIndex, leg.tripId) { mutableStateOf(false) }
    val canShowTripRoute = leg.lineDetail != null &&
        (segmentStops.size >= 2 || !leg.tripId.isNullOrBlank())

    fun loadFullRouteIfNeeded() {
        if (loadedStops != null || routeLoading) return
        val tripId = leg.tripId
        if (tripId.isNullOrBlank()) {
            loadedStops = segmentStops
            return
        }
        scope.launch {
            routeLoading = true
            val reference = segmentStops.ifEmpty { leg.tripRouteStops() }
            val fetched = runCatching { journeySearch.fetchFullLegRoute(leg) }
                .getOrElse { reference }
            loadedStops = if (fetched.size >= 2) fetched else reference
            routeLoading = false
        }
    }

    LegLineHeader(
        leg = leg,
        canShowTripRoute = canShowTripRoute,
        onTripNumberClick = {
            loadFullRouteIfNeeded()
            showTripDetail = true
        },
    )
    LegTimelineBlock(
        leg = leg,
        legIndex = legIndex,
        prediction = prediction,
        predictionsRequested = predictionsRequested,
    )
    if (showTripDetail && canShowTripRoute) {
        TripDetailDialog(
            leg = leg,
            stops = loadedStops,
            isLoading = routeLoading,
            fallbackStops = segmentStops,
            onDismiss = { showTripDetail = false },
        )
    }
    if (leg.origin.remarks.isNotEmpty() || leg.destination.remarks.isNotEmpty()) {
        LegRemarksBlock(
            remarks = (leg.origin.remarks + leg.destination.remarks).distinct(),
        )
    }
}

@Composable
private fun JourneyTimeRangeHeader(
    journey: Journey,
    prediction: RatedJourney?,
    predictionsRequested: Boolean,
) {
    val firstLegIndex = journey.legs.indexOfFirst { !it.isWalking }.takeIf { it >= 0 } ?: 0
    val lastLegIndex = journey.legs.indexOfLast { !it.isWalking }.takeIf { it >= 0 } ?: journey.legs.lastIndex
    val first = journey.legs.getOrNull(firstLegIndex)?.origin
    val last = journey.legs.getOrNull(lastLegIndex)?.destination
    val tolerance = prediction?.punctualityToleranceMinutes
        ?: JourneyRatingOptions.DEFAULT_PUNCTUALITY_TOLERANCE_MINUTES
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
                timelinessProbability = if (predictionsRequested) {
                    prediction?.stopProbability(firstLegIndex, isArrival = false)
                } else {
                    null
                },
                timelinessIsEstimate = prediction?.stopTimelinessIsEstimate(firstLegIndex, isArrival = false) == true,
                toleranceMinutes = tolerance,
                minTransferMinutesUsed = prediction?.minTransferMinutesUsed,
            )
            Text("–", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            StopTimeText(
                scheduled = arrScheduled,
                prognosed = last?.prognosedTime,
                delayMinutes = arrDelay,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                timelinessProbability = if (predictionsRequested) {
                    prediction?.stopProbability(lastLegIndex, isArrival = true)
                } else {
                    null
                },
                timelinessIsEstimate = prediction?.stopTimelinessIsEstimate(lastLegIndex, isArrival = true) == true,
                toleranceMinutes = tolerance,
                minTransferMinutesUsed = prediction?.minTransferMinutesUsed,
            )
        }
    }
}

@Composable
private fun LegLineHeader(
    leg: Leg,
    canShowTripRoute: Boolean,
    onTripNumberClick: () -> Unit,
) {
    if (leg.lineName == null && leg.lineDetail == null) return
    val tripInteraction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        leg.lineName?.let { line ->
            Text(
                line,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        leg.lineDetail?.let { trip ->
            val prefix = if (leg.lineName != null) " " else ""
            val tripStyle = if (canShowTripRoute) {
                MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "$prefix($trip)",
                style = tripStyle,
                modifier = if (canShowTripRoute) {
                    Modifier
                        .clickable(
                            interactionSource = tripInteraction,
                            indication = ripple(bounded = true),
                            onClick = onTripNumberClick,
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .testTag("leg_trip_number")
                } else {
                    Modifier
                },
            )
        }
    }
}

@Composable
private fun JourneyPredictionSummary(
    journey: Journey,
    prediction: RatedJourney,
) {
    val percent = { value: Double -> (value * 100).toInt().coerceIn(0, 100) }
    if (journey.railTransferCount() == 0) return
    val worst = prediction.predictions.mapNotNull { it.successProbability }.minOrNull() ?: return
    val buffer = prediction.minTransferMinutesUsed
    val text = if (buffer != null) {
        stringResource(R.string.prediction_summary_transfer_buffer, buffer, percent(worst))
    } else {
        stringResource(R.string.prediction_summary_transfer, percent(worst))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("journey_prediction_summary"),
    )
}

@Composable
private fun StopTimeText(
    scheduled: String,
    prognosed: String?,
    delayMinutes: Int,
    style: androidx.compose.ui.text.TextStyle,
    fontWeight: FontWeight? = null,
    timelinessProbability: Double? = null,
    timelinessIsEstimate: Boolean = false,
    toleranceMinutes: Int = JourneyRatingOptions.DEFAULT_PUNCTUALITY_TOLERANCE_MINUTES,
    minTransferMinutesUsed: Int? = null,
) {
    val effectiveDelay = delayMinutes.takeIf { it > 0 }
        ?: delayMinutesFromTimes(scheduled, prognosed)
        ?: 0
    val display = prognosed?.takeIf { it.isNotBlank() } ?: scheduled
    val showScheduledStruck = prognosed != null && prognosed != scheduled
    val clock = formatJourneyClock(display)
    val percent = timelinessProbability?.let { (it * 100).toInt().coerceIn(0, 100) }
    Column(horizontalAlignment = Alignment.End) {
        if (showScheduledStruck) {
            Text(
                stringResource(R.string.scheduled_time_short, formatJourneyClock(scheduled)),
                style = MaterialTheme.typography.labelSmall,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (percent != null) {
            TimelinessTimeWithPercent(
                clock = clock,
                percent = percent,
                probability = timelinessProbability,
                style = style,
                fontWeight = fontWeight,
                delayed = effectiveDelay > 0,
                isEstimate = timelinessIsEstimate,
                toleranceMinutes = toleranceMinutes,
                minTransferMinutesUsed = minTransferMinutesUsed,
            )
        } else {
            Text(
                clock,
                style = style,
                fontWeight = fontWeight,
                color = if (effectiveDelay > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
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
private fun TimelinessTimeWithPercent(
    clock: String,
    percent: Int,
    probability: Double,
    style: androidx.compose.ui.text.TextStyle,
    fontWeight: FontWeight?,
    delayed: Boolean,
    isEstimate: Boolean,
    toleranceMinutes: Int,
    minTransferMinutesUsed: Int?,
) {
    var showTooltip by remember { mutableStateOf(false) }
    val timeColor = if (delayed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val percentColor = when {
        probability >= 0.8 -> MaterialTheme.colorScheme.tertiary
        probability >= 0.5 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            clock,
            style = style,
            fontWeight = fontWeight,
            color = timeColor,
        )
        Text(
            text = "($percent%)",
            style = style.copy(fontSize = style.fontSize * 0.92f),
            fontWeight = FontWeight.Medium,
            color = percentColor,
            modifier = Modifier
                .clickable { showTooltip = true }
                .testTag("timeliness_percent"),
        )
    }
    if (showTooltip) {
        TimelinessProbabilityDialog(
            isEstimate = isEstimate,
            toleranceMinutes = if (isEstimate) toleranceMinutes else 0,
            minTransferMinutesUsed = minTransferMinutesUsed,
            onDismiss = { showTooltip = false },
        )
    }
}

@Composable
internal fun TimelinessProbabilityDialog(
    isEstimate: Boolean,
    toleranceMinutes: Int,
    minTransferMinutesUsed: Int?,
    onDismiss: () -> Unit,
) {
    val body = when {
        isEstimate && toleranceMinutes == 0 ->
            stringResource(R.string.prediction_tooltip_body_estimate_exact)
        isEstimate ->
            stringResource(R.string.prediction_tooltip_body_estimate, toleranceMinutes)
        toleranceMinutes == 0 ->
            stringResource(R.string.prediction_tooltip_body_ml_exact)
        else ->
            stringResource(R.string.prediction_tooltip_body_ml, toleranceMinutes)
    }
    val bufferNote = minTransferMinutesUsed?.let {
        stringResource(R.string.prediction_tooltip_transfer_buffer, it)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prediction_tooltip_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(body)
                if (bufferNote != null) {
                    Text(
                        bufferNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
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
