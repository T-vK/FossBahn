package de.openbahn.navigator.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.api.JourneyRatingOptions
import de.openbahn.navigator.R

@Composable
fun PunctualityToleranceSection(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    PunctualityToleranceChipRow(
        selectedMinutes = selectedMinutes,
        onSelect = onSelect,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PunctualityToleranceChipRow(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val choices = listOf(0, 5, 10, 15)
    SettingsFilterChipFlow(modifier = modifier.fillMaxWidth()) {
        choices.forEach { minutes ->
            FilterChip(
                selected = selectedMinutes == minutes,
                onClick = { onSelect(minutes) },
                label = {
                    Text(
                        when (minutes) {
                            0 -> stringResource(R.string.settings_punctuality_exact)
                            JourneyRatingOptions.DEFAULT_PUNCTUALITY_TOLERANCE_MINUTES ->
                                stringResource(R.string.settings_punctuality_minutes_default, minutes)
                            else -> stringResource(R.string.settings_punctuality_minutes, minutes)
                        },
                    )
                },
            )
        }
    }
}
