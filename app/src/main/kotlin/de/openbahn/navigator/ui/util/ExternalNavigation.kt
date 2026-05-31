package de.openbahn.navigator.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.openbahn.api.coordinatesFromHaltId
import de.openbahn.model.StopEvent
import de.openbahn.navigator.R

fun openExternalNavigation(
    context: Context,
    label: String,
    latitude: Double?,
    longitude: Double?,
) {
    val lat = latitude ?: return
    val lon = longitude ?: return
    val encodedLabel = Uri.encode(label.ifBlank { "$lat,$lon" })
    val geoUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($encodedLabel)")
    val intent = Intent(Intent.ACTION_VIEW, geoUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chooser = Intent.createChooser(intent, context.getString(R.string.navigate_chooser))
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }
}

fun StopEvent.navigationCoordinates(): Pair<Double, Double>? =
    coordinatesFromHaltId(id)

@Composable
fun NavigateToStopIconButton(
    stop: StopEvent,
    modifier: Modifier = Modifier,
    testTag: String = "navigate_to_stop",
) {
    val context = LocalContext.current
    val coords = stop.navigationCoordinates()
    if (coords == null) return
    val (lat, lon) = coords
    val label = buildString {
        append(stop.name)
        stop.platform?.let { append(" · ").append(context.getString(R.string.platform_label, it)) }
    }
    IconButton(
        onClick = { openExternalNavigation(context, label, lat, lon) },
        modifier = modifier.testTag(testTag),
    ) {
        Icon(
            Icons.Default.Navigation,
            contentDescription = stringResource(R.string.navigate_to_stop),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
