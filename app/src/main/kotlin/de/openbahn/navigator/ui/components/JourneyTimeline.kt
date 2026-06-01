package de.openbahn.navigator.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.openbahn.api.JourneyRatingOptions
import de.openbahn.model.Leg
import de.openbahn.model.RatedJourney
import de.openbahn.model.StopEvent
import de.openbahn.model.TransferPrediction
import de.openbahn.model.RouteStopSegment
import de.openbahn.model.alightIndexInRoute
import de.openbahn.model.boardIndexInRoute
import de.openbahn.model.delayMinutesFromTimes
import de.openbahn.model.routeStopSegment
import de.openbahn.model.stationNamesMatch
import de.openbahn.model.stopProbability
import de.openbahn.model.stopTimelinessIsEstimate
import de.openbahn.navigator.R
import de.openbahn.navigator.ui.util.NavigateToStopIconButton
import de.openbahn.navigator.ui.util.formatJourneyClock
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Fixed width so clock + percent stack without wrapping and the rail stays aligned. */
private val TimelineTimeWidth = 68.dp
private val TimelineRailWidth = 28.dp
private val TimelineDotLarge = 12.dp
private val TimelineDotSmall = 8.dp

@Composable
internal fun LegTimelineBlock(
    leg: Leg,
    legIndex: Int,
    prediction: RatedJourney?,
    predictionsRequested: Boolean,
    routeStops: List<StopEvent>? = null,
    routeBoardAt: String? = null,
    routeAlightAt: String? = null,
    routeBoardScheduled: String? = null,
    routeAlightScheduled: String? = null,
    modifier: Modifier = Modifier,
) {
    val tolerance = prediction?.punctualityToleranceMinutes
        ?: JourneyRatingOptions.DEFAULT_PUNCTUALITY_TOLERANCE_MINUTES
    val minTransfer = prediction?.minTransferMinutesUsed

    if (routeStops != null && routeStops.size >= 2) {
        val boardAt = routeBoardAt.orEmpty()
        val alightAt = routeAlightAt.orEmpty()
        val boardScheduled = routeBoardScheduled.orEmpty()
        val alightScheduled = routeAlightScheduled.orEmpty()
        val boardIndex = boardIndexInRoute(routeStops, boardAt, boardScheduled)
        val alightIndex = alightIndexInRoute(routeStops, alightAt, alightScheduled, boardIndex)
        TimelineLegSpine(modifier) {
            routeStops.forEachIndexed { stopIndex, stop ->
                val segment = routeStopSegment(stopIndex, boardIndex, alightIndex)
                val onTrip = segment == RouteStopSegment.ON_TRIP
                val isBoard = onTrip && stationNamesMatch(stop.name, boardAt)
                val isAlight = onTrip && stationNamesMatch(stop.name, alightAt)
                TimelineStopRow(
                    stop = stop,
                    stationLabel = stop.name,
                    highlightLabel = when {
                        isBoard && isAlight -> stringResource(R.string.trip_route_your_stop)
                        isBoard -> stringResource(R.string.trip_route_board_here)
                        isAlight -> stringResource(R.string.trip_route_alight_here)
                        else -> null
                    },
                    nodeStyle = when {
                        stopIndex == 0 -> TimelineNodeStyle.Origin
                        stopIndex == routeStops.lastIndex -> TimelineNodeStyle.Destination
                        else -> TimelineNodeStyle.Via
                    },
                    segmentColor = timelineSegmentColor(stop, null),
                    timelinessProbability = null,
                    timelinessIsEstimate = false,
                    predictionsRequested = false,
                    toleranceMinutes = tolerance,
                    minTransferMinutesUsed = minTransfer,
                    navigateTestTag = "leg_${legIndex}_route_stop_$stopIndex",
                    muted = !onTrip,
                )
            }
        }
        return
    }

    var viaExpanded by remember(legIndex, leg.intermediateStops.size) { mutableStateOf(false) }
    val viaCount = leg.intermediateStops.size

    TimelineLegSpine(modifier) {
        TimelineStopRow(
            stop = leg.origin,
            stationLabel = leg.origin.name,
            highlightLabel = stringResource(R.string.departure),
            nodeStyle = TimelineNodeStyle.Origin,
            segmentColor = timelineSegmentColor(
                leg.origin,
                prediction?.stopProbability(legIndex, isArrival = false),
            ),
            timelinessProbability = if (predictionsRequested) {
                prediction?.stopProbability(legIndex, isArrival = false)
            } else {
                null
            },
            timelinessIsEstimate = prediction?.stopTimelinessIsEstimate(legIndex, isArrival = false) == true,
            predictionsRequested = predictionsRequested,
            toleranceMinutes = tolerance,
            minTransferMinutesUsed = minTransfer,
            navigateTestTag = "navigate_leg_${legIndex}_departure",
            belowStation = {
                LegTimelineTrainChip(leg = leg)
            },
            modifier = Modifier.testTag("leg_${legIndex}_departure"),
        )

        if (viaCount > 0) {
            TimelineViaSection(
                expanded = viaExpanded,
                viaCount = viaCount,
                onToggle = { viaExpanded = !viaExpanded },
                legIndex = legIndex,
            )
            AnimatedVisibility(
                visible = viaExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    leg.intermediateStops.forEachIndexed { viaIndex, stop ->
                        TimelineStopRow(
                            stop = stop,
                            stationLabel = stop.name,
                            highlightLabel = null,
                            nodeStyle = TimelineNodeStyle.Via,
                            segmentColor = timelineSegmentColor(
                                stop,
                                prediction?.stopProbability(legIndex, intermediateIndex = viaIndex, isArrival = true),
                            ),
                            timelinessProbability = if (predictionsRequested) {
                                prediction?.stopProbability(legIndex, intermediateIndex = viaIndex, isArrival = true)
                            } else {
                                null
                            },
                            timelinessIsEstimate = prediction?.stopTimelinessIsEstimate(
                                legIndex,
                                intermediateIndex = viaIndex,
                                isArrival = true,
                            ) == true,
                            predictionsRequested = predictionsRequested,
                            toleranceMinutes = tolerance,
                            minTransferMinutesUsed = minTransfer,
                            navigateTestTag = "leg_${legIndex}_via_stop",
                            muted = false,
                        )
                    }
                }
            }
        }

        TimelineStopRow(
            stop = leg.destination,
            stationLabel = leg.destination.name,
            highlightLabel = stringResource(R.string.arrival),
            nodeStyle = TimelineNodeStyle.Destination,
            segmentColor = timelineSegmentColor(
                leg.destination,
                prediction?.stopProbability(legIndex, isArrival = true),
            ),
            timelinessProbability = if (predictionsRequested) {
                prediction?.stopProbability(legIndex, isArrival = true)
            } else {
                null
            },
            timelinessIsEstimate = prediction?.stopTimelinessIsEstimate(legIndex, isArrival = true) == true,
            predictionsRequested = predictionsRequested,
            toleranceMinutes = tolerance,
            minTransferMinutesUsed = minTransfer,
            navigateTestTag = "navigate_leg_${legIndex}_arrival",
            modifier = Modifier.testTag("leg_${legIndex}_arrival"),
        )
    }
}

@Composable
internal fun TransferTimelineBlock(
    fromLeg: Leg,
    toLeg: Leg,
    prediction: TransferPrediction?,
    predictionsRequested: Boolean,
    minTransferMinutesUsed: Int?,
    modifier: Modifier = Modifier,
) {
    val transferMins = transferMinutesBetween(fromLeg.destination, toLeg.origin)
    val probability = prediction?.successProbability
    val percent = probability?.let { (it * 100).toInt().coerceIn(0, 100) }

    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(TimelineTimeWidth)
                .padding(end = 4.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            transferMins?.let { mins ->
                Text(
                    "${mins} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            Modifier
                .width(TimelineRailWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            DashedVerticalLine(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Icon(
                Icons.Default.SwapHoriz,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(2.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.transfer_at, fromLeg.destination.name),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                fromLeg.destination.platform?.let {
                    Text(
                        stringResource(R.string.platform_arrival_short, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                toLeg.origin.platform?.let {
                    Text(
                        stringResource(R.string.platform_departure_short, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (percent != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.testTag("transfer_prediction"),
                ) {
                    Text(
                        text = "$percent%",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = transferProbabilityColor(probability),
                    )
                }
            } else if (predictionsRequested) {
                Text(
                    stringResource(R.string.prediction_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun WalkingTimelineBlock(
    leg: Leg,
    modifier: Modifier = Modifier,
) {
    val duration = leg.durationMinutes?.let { formatDurationMinutesShort(it) }
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(TimelineTimeWidth),
            contentAlignment = Alignment.CenterEnd,
        ) {
            duration?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            Modifier
                .width(TimelineRailWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            DashedVerticalLine(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Icon(
                Icons.AutoMirrored.Filled.DirectionsWalk,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(R.string.walk_leg_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                "${leg.origin.name} → ${leg.destination.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NavigateToStopIconButton(stop = leg.destination, testTag = "navigate_walk_destination")
    }
}

@Composable
private fun TimelineStopRow(
    stop: StopEvent,
    stationLabel: String,
    highlightLabel: String?,
    nodeStyle: TimelineNodeStyle,
    segmentColor: Color,
    timelinessProbability: Double?,
    timelinessIsEstimate: Boolean,
    predictionsRequested: Boolean,
    toleranceMinutes: Int,
    minTransferMinutesUsed: Int?,
    navigateTestTag: String,
    muted: Boolean = false,
    belowStation: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val delay = stop.delayMinutes
        ?: delayMinutesFromTimes(stop.scheduledTime, stop.prognosedTime)
        ?: 0
    val nameColor = when {
        stop.cancelled -> MaterialTheme.colorScheme.error
        muted -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimelineTimeColumn(
            scheduled = stop.scheduledTime,
            prognosed = stop.prognosedTime,
            delayMinutes = delay,
            timelinessProbability = if (predictionsRequested) timelinessProbability else null,
            timelinessIsEstimate = timelinessIsEstimate,
            toleranceMinutes = toleranceMinutes,
            minTransferMinutesUsed = minTransferMinutesUsed,
        )
        Box(
            Modifier.width(TimelineRailWidth),
            contentAlignment = Alignment.Center,
        ) {
            TimelineDot(nodeStyle = nodeStyle, color = segmentColor)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    highlightLabel?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        stationLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (nodeStyle != TimelineNodeStyle.Via) FontWeight.SemiBold else FontWeight.Normal,
                        color = nameColor,
                        textDecoration = if (stop.cancelled) TextDecoration.LineThrough else null,
                    )
                    belowStation?.invoke()
                }
                stop.platform?.let { platform ->
                    Text(
                        stringResource(R.string.platform_label, platform),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            stop.remarks.forEach { remark ->
                Text(
                    remark,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        NavigateToStopIconButton(stop = stop, testTag = navigateTestTag)
    }
}

@Composable
private fun TimelineViaSection(
    expanded: Boolean,
    viaCount: Int,
    onToggle: () -> Unit,
    legIndex: Int,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp)
            .testTag("leg_${legIndex}_intermediate_toggle"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(TimelineTimeWidth))
        Box(
            Modifier.width(TimelineRailWidth),
            contentAlignment = Alignment.Center,
        ) {
            TimelineDot(
                nodeStyle = TimelineNodeStyle.ViaCollapsed,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Row(
            Modifier
                .weight(1f)
                .padding(start = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expanded) {
                    stringResource(R.string.hide_intermediate_stations)
                } else {
                    stringResource(R.string.show_intermediate_stations, viaCount)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun TimelineLegSpine(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outline
    Box(modifier.fillMaxWidth()) {
        Column { content() }
        Canvas(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = TimelineTimeWidth)
                .width(TimelineRailWidth)
                .matchParentSize(),
        ) {
            val stroke = 2.dp.toPx()
            val x = size.width / 2f
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun TimelineDot(
    nodeStyle: TimelineNodeStyle,
    color: Color,
) {
    val surface = MaterialTheme.colorScheme.surface
    when (nodeStyle) {
        TimelineNodeStyle.Origin -> {
            Box(
                Modifier
                    .size(TimelineDotLarge)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        TimelineNodeStyle.Destination -> {
            Box(
                Modifier
                    .size(TimelineDotLarge)
                    .clip(CircleShape)
                    .background(color)
                    .border(2.dp, surface, CircleShape),
            )
        }
        TimelineNodeStyle.Via, TimelineNodeStyle.ViaCollapsed -> {
            Box(
                Modifier
                    .size(TimelineDotSmall)
                    .clip(CircleShape)
                    .background(surface)
                    .border(2.dp, color, CircleShape),
            )
        }
    }
}

@Composable
private fun DashedVerticalLine(
    modifier: Modifier,
    color: Color,
) {
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        val dash = floatArrayOf(8f, 8f)
        drawLine(
            color = color,
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(dash),
        )
    }
}

@Composable
private fun TimelineTimeColumn(
    scheduled: String,
    prognosed: String?,
    delayMinutes: Int,
    timelinessProbability: Double?,
    timelinessIsEstimate: Boolean,
    toleranceMinutes: Int,
    minTransferMinutesUsed: Int?,
) {
    val effectiveDelay = delayMinutes.takeIf { it > 0 }
        ?: delayMinutesFromTimes(scheduled, prognosed)
        ?: 0
    val display = prognosed?.takeIf { it.isNotBlank() } ?: scheduled
    val showScheduledStruck = prognosed != null && prognosed != scheduled
    val clock = formatJourneyClock(display)
    val percent = timelinessProbability?.let { (it * 100).toInt().coerceIn(0, 100) }

    Column(
        Modifier
            .width(TimelineTimeWidth)
            .padding(end = 4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        if (showScheduledStruck) {
            Text(
                stringResource(R.string.scheduled_time_short, formatJourneyClock(scheduled)),
                style = MaterialTheme.typography.labelSmall,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (percent != null && timelinessProbability != null) {
            TimelineTimeWithPercent(
                clock = clock,
                percent = percent,
                probability = timelinessProbability,
                delayed = effectiveDelay > 0,
                isEstimate = timelinessIsEstimate,
                toleranceMinutes = toleranceMinutes,
                minTransferMinutesUsed = minTransferMinutesUsed,
            )
        } else {
            Text(
                clock,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
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
private fun TimelineTimeWithPercent(
    clock: String,
    percent: Int,
    probability: Double,
    delayed: Boolean,
    isEstimate: Boolean,
    toleranceMinutes: Int,
    minTransferMinutesUsed: Int?,
) {
    var showTooltip by remember { mutableStateOf(false) }
    val timeColor = if (delayed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val percentColor = transferProbabilityColor(probability)
    Column(horizontalAlignment = Alignment.End) {
        Text(
            clock,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = timeColor,
            maxLines = 1,
        )
        Text(
            text = "($percent%)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = percentColor,
            maxLines = 1,
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
private fun LegTimelineTrainChip(leg: Leg) {
    val line = leg.lineName?.takeIf { it.isNotBlank() } ?: return
    val destination = leg.destination.name
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.padding(top = 6.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Train,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
                Text(
                    line,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.timeline_direction_to, destination),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private enum class TimelineNodeStyle {
    Origin,
    Via,
    ViaCollapsed,
    Destination,
}

@Composable
private fun timelineSegmentColor(stop: StopEvent, probability: Double?): Color {
  val delay = stop.delayMinutes
        ?: delayMinutesFromTimes(stop.scheduledTime, stop.prognosedTime)
        ?: 0
    if (delay > 0) return MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
    probability?.let { return transferProbabilityColor(it) }
    return MaterialTheme.colorScheme.tertiary
}

@Composable
private fun transferProbabilityColor(probability: Double): Color = when {
    probability >= 0.8 -> MaterialTheme.colorScheme.tertiary
    probability >= 0.5 -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.error
}

private fun transferMinutesBetween(arrival: StopEvent, departure: StopEvent): Long? {
    val arr = parseIsoLocal(arrival.prognosedTime ?: arrival.scheduledTime) ?: return null
    val dep = parseIsoLocal(departure.prognosedTime ?: departure.scheduledTime) ?: return null
    return Duration.between(arr, dep).toMinutes()
}

private fun parseIsoLocal(raw: String): LocalDateTime? = try {
    LocalDateTime.parse(raw.take(19), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
} catch (_: Exception) {
    null
}

private fun formatDurationMinutesShort(minutes: Int): String {
    if (minutes < 60) return "${minutes} min"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "${h}h" else "${h}h ${m}min"
}
