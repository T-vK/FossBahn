package de.openbahn.navigator.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.openbahn.navigator.R
import de.openbahn.navigator.data.UserPreferencesRepository

@Composable
fun NearDepartureCheckIntervalSection(
    selectedSeconds: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val choices = listOf(5, 10, 15, 30, 60)
    SettingsPresetDropdown(
        choices = choices,
        selected = selectedSeconds,
        onSelect = onSelect,
        labelFor = { seconds ->
            if (seconds == UserPreferencesRepository.DEFAULT_NEAR_DEPARTURE_CHECK_SECONDS) {
                stringResource(R.string.settings_near_departure_check_default, seconds)
            } else {
                stringResource(R.string.settings_near_departure_check_seconds, seconds)
            }
        },
        modifier = modifier,
    )
}
