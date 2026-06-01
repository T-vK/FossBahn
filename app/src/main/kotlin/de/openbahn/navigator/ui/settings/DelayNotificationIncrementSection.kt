package de.openbahn.navigator.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.openbahn.navigator.R
import de.openbahn.navigator.tracking.DelayNotificationPolicy

@Composable
fun DelayNotificationIncrementSection(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val choices = listOf(1, 2, 3, 5, 10)
    SettingsPresetDropdown(
        choices = choices,
        selected = selectedMinutes,
        onSelect = onSelect,
        labelFor = { minutes ->
            if (minutes == DelayNotificationPolicy.DEFAULT_INCREMENT_MINUTES) {
                stringResource(R.string.settings_delay_notification_increment_default, minutes)
            } else {
                stringResource(R.string.settings_delay_notification_increment_minutes, minutes)
            }
        },
        modifier = modifier,
    )
}
