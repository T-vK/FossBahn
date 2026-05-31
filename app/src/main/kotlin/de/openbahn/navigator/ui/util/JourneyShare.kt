package de.openbahn.navigator.ui.util

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.openbahn.api.JourneyRatingOptions
import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.RatedJourney
import de.openbahn.model.StopEvent
import de.openbahn.model.delayMinutesFromTimes
import de.openbahn.navigator.R

fun formatJourneyShareText(
    context: Context,
    journey: Journey,
    prediction: RatedJourney? = null,
    predictionsRequested: Boolean = false,
): String {
    val resources = context.resources
    val from = journey.legs.firstOrNull()?.origin?.name.orEmpty()
    val to = journey.legs.lastOrNull()?.destination?.name.orEmpty()
    return buildString {
        appendLine(resources.getString(R.string.share_route, from, to))
        val first = journey.legs.firstOrNull()?.origin
        val last = journey.legs.lastOrNull()?.destination
        val depClock = formatJourneyClock(first?.prognosedTime ?: first?.scheduledTime ?: journey.departure)
        val arrClock = formatJourneyClock(last?.prognosedTime ?: last?.scheduledTime ?: journey.arrival)
        appendLine(
            resources.getString(
                R.string.share_times,
                depClock,
                arrClock,
                formatDurationMinutes(journey.durationMinutes),
            ),
        )
        if (journey.transfers == 0) {
            appendLine(resources.getString(R.string.share_direct))
        } else {
            appendLine(resources.getString(R.string.transfers_count, journey.transfers))
        }
        appendLine()
        journey.legs.forEachIndexed { index, leg ->
            appendLegSection(resources, leg)
            if (index < journey.legs.lastIndex) {
                appendLine(resources.getString(R.string.share_transfer_separator))
                appendLine()
            }
        }
        if (predictionsRequested && prediction != null) {
            predictionSummaryLine(resources, journey, prediction)?.let { line ->
                appendLine()
                appendLine(line)
            }
        }
        journey.priceHint?.let { hint ->
            appendLine()
            appendLine(hint)
        }
        if (journey.deutschlandTicketValid == true) {
            appendLine(resources.getString(R.string.dticket_valid))
        }
        if (journey.remarks.isNotEmpty()) {
            appendLine()
            journey.remarks.forEach { appendLine(it) }
        }
    }.trim()
}

private fun StringBuilder.appendLegSection(
    resources: android.content.res.Resources,
    leg: Leg,
) {
    leg.lineName?.let { appendLine(it) }
    appendStopLine(resources, R.string.departure, leg.origin)
    appendStopLine(resources, R.string.arrival, leg.destination)
}

private fun StringBuilder.appendStopLine(
    resources: android.content.res.Resources,
    @StringRes labelRes: Int,
    stop: StopEvent,
) {
    val label = resources.getString(labelRes)
    val time = formatJourneyClock(stop.prognosedTime ?: stop.scheduledTime)
    val delay = stop.delayMinutes
        ?: delayMinutesFromTimes(stop.scheduledTime, stop.prognosedTime)
        ?: 0
    val platform = stop.platform?.let { resources.getString(R.string.platform_label, it) }
    val delayPart = if (delay > 0) {
        " (${resources.getString(R.string.delay_minutes, delay)})"
    } else {
        ""
    }
    val platformPart = platform?.let { " · $it" }.orEmpty()
    appendLine("$label: ${stop.name} $time$delayPart$platformPart")
}

private fun predictionSummaryLine(
    resources: android.content.res.Resources,
    journey: Journey,
    prediction: RatedJourney,
): String? {
    val percent = { value: Double -> (value * 100).toInt().coerceIn(0, 100) }
    val tolerance = prediction.punctualityToleranceMinutes
        ?: JourneyRatingOptions.DEFAULT_PUNCTUALITY_TOLERANCE_MINUTES
    return when {
        journey.transfers > 0 -> {
            val worst = prediction.predictions.mapNotNull { it.successProbability }.minOrNull() ?: return null
            val buffer = prediction.minTransferMinutesUsed
            if (buffer != null) {
                resources.getString(R.string.prediction_summary_transfer_buffer, buffer, percent(worst))
            } else {
                resources.getString(R.string.prediction_summary_transfer, percent(worst))
            }
        }
        prediction.punctualityProbability != null -> {
            val p = prediction.punctualityProbability!!
            val estimate = prediction.punctualityIsEstimate
            when {
                tolerance == 0 && estimate ->
                    resources.getString(R.string.prediction_summary_punctuality_estimate_exact, percent(p))
                tolerance == 0 ->
                    resources.getString(R.string.prediction_summary_punctuality_exact, percent(p))
                estimate ->
                    resources.getString(R.string.prediction_summary_punctuality_estimate, tolerance, percent(p))
                else ->
                    resources.getString(R.string.prediction_summary_punctuality, tolerance, percent(p))
            }
        }
        else -> null
    }
}

fun shareJourney(
    context: Context,
    journey: Journey,
    prediction: RatedJourney? = null,
    predictionsRequested: Boolean = false,
) {
    val text = formatJourneyShareText(context, journey, prediction, predictionsRequested)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.share_journey_chooser)),
    )
}

@Composable
fun ShareJourneyIconButton(
    journey: Journey,
    prediction: RatedJourney? = null,
    predictionsRequested: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    IconButton(
        onClick = { shareJourney(context, journey, prediction, predictionsRequested) },
        modifier = modifier.testTag("journey_share"),
    ) {
        Icon(
            Icons.Default.Share,
            contentDescription = stringResource(R.string.share_journey),
        )
    }
}
