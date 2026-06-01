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
import de.openbahn.navigator.tracking.DelayNotificationPolicy

@Composable
fun DelayNotificationIncrementSection(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    DelayNotificationIncrementChipRow(
        selectedMinutes = selectedMinutes,
        onSelect = onSelect,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DelayNotificationIncrementChipRow(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val choices = listOf(1, 2, 3, 5, 10)
    SettingsFilterChipFlow(modifier = modifier.fillMaxWidth()) {
        choices.forEach { minutes ->
            FilterChip(
                selected = selectedMinutes == minutes,
                onClick = { onSelect(minutes) },
                label = {
                    Text(
                        if (minutes == DelayNotificationPolicy.DEFAULT_INCREMENT_MINUTES) {
                            stringResource(R.string.settings_delay_notification_increment_default, minutes)
                        } else {
                            stringResource(R.string.settings_delay_notification_increment_minutes, minutes)
                        },
                    )
                },
            )
        }
    }
}
