package de.openbahn.navigator.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.model.OnTimeToleranceSettings
import de.openbahn.navigator.R

@Composable
fun OnTimeDefinitionsSection(
    settings: OnTimeToleranceSettings,
    onChange: (OnTimeToleranceSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_on_time_transfer_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TolerancePickerRow(
            label = stringResource(R.string.settings_on_time_departure),
            selectedMinutes = settings.departureMinutes,
            onSelect = { onChange(settings.copy(departureMinutes = it)) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TolerancePickerRow(
            label = stringResource(R.string.settings_on_time_via),
            selectedMinutes = settings.viaStopMinutes,
            onSelect = { onChange(settings.copy(viaStopMinutes = it)) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TolerancePickerRow(
            label = stringResource(R.string.settings_on_time_arrival),
            selectedMinutes = settings.arrivalMinutes,
            defaultMinutes = OnTimeToleranceSettings.DEFAULT_MINUTES,
            onSelect = { onChange(settings.copy(arrivalMinutes = it)) },
        )
    }
}
