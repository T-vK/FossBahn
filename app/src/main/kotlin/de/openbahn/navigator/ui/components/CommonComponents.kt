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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
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
import de.openbahn.model.railTransferCount
import de.openbahn.api.JourneyRatingOptions
import de.openbahn.model.RatedJourney
import de.openbahn.model.StopEvent
import de.openbahn.model.delayMinutesFromTimes
import de.openbahn.model.stationNamesMatch
import de.openbahn.model.tripRouteStops
import de.openbahn.navigator.R
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
                JourneyTimeRangeHeader(journey = journey)
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
                            WalkingLegBlock(leg = leg)
                        } else {
                            LegDetailsBlock(leg = leg, legIndex = index)
                        }
                        LegRemarksBlock(remarks = leg.remarks)
                        val next = journey.legs.getOrNull(index + 1)
                        if (next != null && !leg.isWalking && !next.isWalking) {
                            TransferBlock(
                                fromLeg = leg,
                                toLeg = next,
                                prediction = prediction?.predictions?.getOrNull(index),
                                predictionsRequested = predictionsRequested,
                                minTransferMinutesUsed = prediction?.minTransferMinutesUsed,
                            )
                        }
                    }
                    if (predictionsRequested && journey.railTransferCount() > 0) {
                        PunctualityBlock(
                            probability = prediction?.punctualityProbability,
                            isEstimate = prediction?.punctualityIsEstimate == true,
                            toleranceMinutes = prediction?.punctualityToleranceMinutes
                                ?: JourneyRatingOptions.DEFAULT_PUNCTUALITY_TOLERANCE_MINUTES,
                        )
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
private fun WalkingLegBlock(leg: Leg) {
    val duration = leg.durationMinutes?.let { formatDurationMinutes(it) }
    val distance = leg.distanceMeters?.takeIf { it > 0 }?.let { meters ->
        if (meters >= 1000) {
            "%.1f km".format(meters / 1000.0)
        } else {
            "$meters m"
        }
    }
    val meta = listOfNotNull(duration, distance).joinToString(" · ")
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.DirectionsWalk,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        Text(
            stringResource(R.string.walk_leg_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
    Text(
        "${leg.origin.name} → ${leg.destination.name}",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (meta.isNotBlank()) {
        Text(
            meta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        NavigateToStopIconButton(
            stop = leg.destination,
            testTag = "navigate_walk_destination",
        )
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
private fun LegDetailsBlock(leg: Leg, legIndex: Int) {
    val routeStops = leg.tripRouteStops()
    var showTripRoute by remember(legIndex, routeStops.size, leg.lineDetail) { mutableStateOf(false) }
    val canShowTripRoute = leg.lineDetail != null && routeStops.size >= 2
    LegLineHeader(
        leg = leg,
        canShowTripRoute = canShowTripRoute,
        onTripNumberClick = { showTripRoute = !showTripRoute },
    )
    AnimatedVisibility(
        visible = showTripRoute && canShowTripRoute,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        TripRouteBlock(
            stops = routeStops,
            boardAt = leg.origin.name,
            alightAt = leg.destination.name,
            legIndex = legIndex,
        )
    }
    StopRow(
        label = stringResource(R.string.departure),
        stop = leg.origin,
        modifier = Modifier.testTag("leg_${legIndex}_departure"),
        navigateTestTag = "navigate_leg_${legIndex}_departure",
    )
    if (!showTripRoute && leg.intermediateStops.isNotEmpty()) {
        IntermediateStopsBlock(stops = leg.intermediateStops, legIndex = legIndex)
    }
    StopRow(
        label = stringResource(R.string.arrival),
        stop = leg.destination,
        modifier = Modifier.testTag("leg_${legIndex}_arrival"),
        navigateTestTag = "navigate_leg_${legIndex}_arrival",
    )
    if (leg.origin.remarks.isNotEmpty() || leg.destination.remarks.isNotEmpty()) {
        LegRemarksBlock(
            remarks = (leg.origin.remarks + leg.destination.remarks).distinct(),
        )
    }
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
    navigateTestTag: String = "navigate_to_stop",
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stop.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (stop.cancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (stop.cancelled) TextDecoration.LineThrough else null,
            )
            if (stop.remarks.isNotEmpty()) {
                stop.remarks.forEach { remark ->
                    Text(
                        remark,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
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
            NavigateToStopIconButton(stop = stop, testTag = navigateTestTag)
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
                    textDecoration = TextDecoration.Underline,
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
private fun TripRouteBlock(
    stops: List<StopEvent>,
    boardAt: String,
    alightAt: String,
    legIndex: Int,
) {
    Text(
        stringResource(R.string.trip_route_heading),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
    Column(
        Modifier.padding(start = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        stops.forEachIndexed { index, stop ->
            val isBoard = stationNamesMatch(stop.name, boardAt)
            val isAlight = stationNamesMatch(stop.name, alightAt)
            Column(Modifier.testTag("leg_${legIndex}_route_stop_$index")) {
                if (isBoard || isAlight) {
                    Text(
                        text = when {
                            isBoard && isAlight -> stringResource(R.string.trip_route_your_stop)
                            isBoard -> stringResource(R.string.trip_route_board_here)
                            else -> stringResource(R.string.trip_route_alight_here)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                ViaStopRow(stop = stop)
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
                ViaStopRow(
                    stop = stop,
                    modifier = Modifier.testTag("leg_${legIndex}_via_stop"),
                )
            }
        }
    }
}

@Composable
private fun ViaStopRow(
    stop: StopEvent,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            stop.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (stop.cancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (stop.cancelled) TextDecoration.LineThrough else null,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (stop.scheduledTime.isNotBlank()) {
                StopTimeText(
                    scheduled = stop.scheduledTime,
                    prognosed = stop.prognosedTime,
                    delayMinutes = stop.delayMinutes
                        ?: delayMinutesFromTimes(stop.scheduledTime, stop.prognosedTime)
                        ?: 0,
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

@Composable
private fun JourneyPredictionSummary(
    journey: Journey,
    prediction: RatedJourney,
) {
    val percent = { value: Double -> (value * 100).toInt().coerceIn(0, 100) }
    val tolerance = prediction.punctualityToleranceMinutes
        ?: JourneyRatingOptions.DEFAULT_PUNCTUALITY_TOLERANCE_MINUTES
    val text = when {
        journey.railTransferCount() > 0 -> {
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
            val estimate = prediction.punctualityIsEstimate
            when {
                tolerance == 0 && estimate ->
                    stringResource(R.string.prediction_summary_punctuality_estimate_exact, percent(p))
                tolerance == 0 ->
                    stringResource(R.string.prediction_summary_punctuality_exact, percent(p))
                estimate ->
                    stringResource(R.string.prediction_summary_punctuality_estimate, tolerance, percent(p))
                else ->
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
            stringResource(R.string.prediction_arrival_forecast),
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.SwapHoriz,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.transfer_at, station),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
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
