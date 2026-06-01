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
import de.openbahn.navigator.R
import de.openbahn.navigator.data.UserPreferencesRepository

@Composable
fun NearDepartureCheckIntervalSection(
    selectedSeconds: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NearDepartureCheckChipRow(
        selectedSeconds = selectedSeconds,
        onSelect = onSelect,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NearDepartureCheckChipRow(
    selectedSeconds: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val choices = listOf(5, 10, 15, 30, 60)
    SettingsFilterChipFlow(modifier = modifier.fillMaxWidth()) {
        choices.forEach { seconds ->
            FilterChip(
                selected = selectedSeconds == seconds,
                onClick = { onSelect(seconds) },
                label = {
                    Text(
                        if (seconds == UserPreferencesRepository.DEFAULT_NEAR_DEPARTURE_CHECK_SECONDS) {
                            stringResource(R.string.settings_near_departure_check_default, seconds)
                        } else {
                            stringResource(R.string.settings_near_departure_check_seconds, seconds)
                        },
                    )
                },
            )
        }
    }
}
