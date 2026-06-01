package de.openbahn.navigator.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.model.OnTimeToleranceSettings
import de.openbahn.navigator.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinuteTolerancePicker(
    label: String,
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    defaultMinutes: Int = OnTimeToleranceSettings.DEFAULT_MINUTES,
) {
    var expanded by remember { mutableStateOf(false) }
    val choices = OnTimeToleranceSettings.PRESET_MINUTES
    val safeSelection = selectedMinutes.takeIf { it in choices } ?: defaultMinutes

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = toleranceLabel(safeSelection, defaultMinutes),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            choices.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(toleranceLabel(minutes, defaultMinutes)) },
                    onClick = {
                        onSelect(minutes)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
fun TolerancePickerRow(
    label: String,
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    defaultMinutes: Int = OnTimeToleranceSettings.DEFAULT_MINUTES,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        CompactMinuteTolerancePicker(
            selectedMinutes = selectedMinutes,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
            defaultMinutes = defaultMinutes,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactMinuteTolerancePicker(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    defaultMinutes: Int = OnTimeToleranceSettings.DEFAULT_MINUTES,
) {
    var expanded by remember { mutableStateOf(false) }
    val choices = OnTimeToleranceSettings.PRESET_MINUTES
    val safeSelection = selectedMinutes.takeIf { it in choices } ?: defaultMinutes

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = toleranceLabel(safeSelection, defaultMinutes),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            choices.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(toleranceLabel(minutes, defaultMinutes)) },
                    onClick = {
                        onSelect(minutes)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun toleranceLabel(minutes: Int, defaultMinutes: Int): String = when (minutes) {
    0 -> stringResource(R.string.settings_punctuality_exact)
    defaultMinutes -> stringResource(R.string.settings_punctuality_minutes_default, minutes)
    else -> stringResource(R.string.settings_punctuality_minutes, minutes)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPresetDropdown(
    choices: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    labelFor: @Composable (Int) -> String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val safeSelection = selected.takeIf { it in choices } ?: choices.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = labelFor(safeSelection),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            choices.forEach { value ->
                DropdownMenuItem(
                    text = { Text(labelFor(value)) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
