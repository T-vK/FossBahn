package de.openbahn.navigator.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.navigator.R
import de.openbahn.navigator.ui.util.epochMillisToLocalDateTime
import de.openbahn.navigator.ui.util.localDateTimeToEpochMillis
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchWhenSection(
    departureTime: LocalDateTime,
    arrivalSearch: Boolean,
    onDepartureTimeChange: (LocalDateTime) -> Unit,
    onArrivalSearchChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateLabel = remember(departureTime) {
        DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()).format(departureTime)
    }
    val timeLabel = remember(departureTime) {
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(departureTime)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.search_when),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !arrivalSearch,
                onClick = { onArrivalSearchChange(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.testTag("search_time_mode_departure"),
                label = { Text(stringResource(R.string.search_time_departure)) },
            )
            SegmentedButton(
                selected = arrivalSearch,
                onClick = { onArrivalSearchChange(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.testTag("search_time_mode_arrival"),
                label = { Text(stringResource(R.string.search_time_arrival)) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PickerTextField(
                value = dateLabel,
                label = stringResource(R.string.search_date),
                icon = Icons.Outlined.CalendarMonth,
                onClick = { showDatePicker = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_date_field"),
                openTestTag = "search_date_open",
            )
            PickerTextField(
                value = timeLabel,
                label = stringResource(R.string.search_time),
                icon = Icons.Outlined.Schedule,
                onClick = { showTimePicker = true },
                modifier = Modifier
                    .width(128.dp)
                    .testTag("search_time_field"),
                openTestTag = "search_time_open",
            )
        }

        FilledTonalButton(
            onClick = { onDepartureTimeChange(LocalDateTime.now()) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_time_now"),
        ) {
            Text(stringResource(R.string.search_time_now))
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = localDateTimeToEpochMillis(departureTime, zone),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = dateState.selectedDateMillis
                            ?: localDateTimeToEpochMillis(departureTime, zone)
                        val date = epochMillisToLocalDateTime(millis, zone).toLocalDate()
                        onDepartureTimeChange(LocalDateTime.of(date, departureTime.toLocalTime()))
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = departureTime.hour,
            initialMinute = departureTime.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.search_pick_time)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val time = java.time.LocalTime.of(timeState.hour, timeState.minute)
                        onDepartureTimeChange(LocalDateTime.of(departureTime.toLocalDate(), time))
                        showTimePicker = false
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PickerTextField(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    openTestTag: String? = null,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        disabledTextColor = MaterialTheme.colorScheme.onSurface,
        disabledContainerColor = MaterialTheme.colorScheme.surface,
        disabledBorderColor = MaterialTheme.colorScheme.outline,
        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            singleLine = true,
            label = { Text(label) },
            leadingIcon = {
                Icon(icon, contentDescription = label)
            },
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .then(if (openTestTag != null) Modifier.testTag(openTestTag) else Modifier),
        )
    }
}
