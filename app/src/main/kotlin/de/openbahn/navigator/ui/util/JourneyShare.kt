package de.openbahn.navigator.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
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
import de.openbahn.model.railTransferCount
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
        val walkLegs = journey.legs.count { it.isWalking }
        appendLine(
            if (walkLegs > 0) {
                resources.getString(R.string.transfers_with_walks, journey.railTransferCount(), walkLegs)
            } else if (journey.railTransferCount() == 0) {
                resources.getString(R.string.share_direct)
            } else {
                resources.getString(R.string.transfers_count, journey.railTransferCount())
            },
        )
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
    when {
        leg.lineName != null && leg.lineDetail != null -> appendLine("${leg.lineName} (${leg.lineDetail})")
        leg.lineName != null -> appendLine(leg.lineName!!)
        leg.lineDetail != null -> appendLine(leg.lineDetail!!)
    }
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
    val tolerance = prediction.effectiveOnTimeTolerance().arrivalMinutes
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

/**
 * Shares a link to the bahn.de page for the connection, reconstructed from the API ctxRecon token.
 */
fun shareJourney(context: Context, journey: Journey) {
    val uri = journeyBookingUri(journey) ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, uri.toString())
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.share_journey_chooser)),
    )
}

/**
 * Copies the formatted, human-readable journey summary to the clipboard.
 */
fun copyJourneyDetails(
    context: Context,
    journey: Journey,
    prediction: RatedJourney? = null,
    predictionsRequested: Boolean = false,
) {
    val text = formatJourneyShareText(context, journey, prediction, predictionsRequested)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val label = context.getString(R.string.copy_journey)
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    // Android 13+ shows its own copy confirmation UI; avoid a duplicate toast there.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.journey_copied, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun ShareJourneyIconButton(
    journey: Journey,
    prediction: RatedJourney? = null,
    predictionsRequested: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hasLink = journey.refreshToken?.isNotBlank() == true
    IconButton(
        onClick = { shareJourney(context, journey) },
        enabled = hasLink,
        modifier = modifier.testTag("journey_share"),
    ) {
        Icon(
            Icons.Default.Share,
            contentDescription = stringResource(R.string.share_journey),
        )
    }
}

@Composable
fun CopyJourneyIconButton(
    journey: Journey,
    prediction: RatedJourney? = null,
    predictionsRequested: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    IconButton(
        onClick = { copyJourneyDetails(context, journey, prediction, predictionsRequested) },
        modifier = modifier.testTag("journey_copy"),
    ) {
        Icon(
            Icons.Default.ContentCopy,
            contentDescription = stringResource(R.string.copy_journey),
        )
    }
}
