package de.openbahn.navigator.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.navigator.R
import de.openbahn.navigator.locale.AppLanguage

@Composable
fun LanguagePreferenceSection(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    LanguageChipRow(
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageChipRow(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsFilterChipFlow(modifier = modifier.fillMaxWidth()) {
        FilterChip(
            selected = selected == AppLanguage.SYSTEM,
            onClick = { onSelect(AppLanguage.SYSTEM) },
            label = { Text(stringResource(R.string.settings_language_system)) },
        )
        FilterChip(
            selected = selected == AppLanguage.GERMAN,
            onClick = { onSelect(AppLanguage.GERMAN) },
            label = { Text(stringResource(R.string.settings_language_german)) },
        )
        FilterChip(
            selected = selected == AppLanguage.ENGLISH,
            onClick = { onSelect(AppLanguage.ENGLISH) },
            label = { Text(stringResource(R.string.settings_language_english)) },
        )
    }
}

