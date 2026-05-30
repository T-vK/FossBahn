package de.openbahn.navigator.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.navigator.R
import de.openbahn.navigator.ui.util.epochMillisToLocalDateTime
import de.openbahn.navigator.ui.util.localDateTimeToEpochMillis
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    visible: Boolean,
    selected: LocalDateTime,
    arrivalSearch: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime, arrivalSearch: Boolean) -> Unit,
) {
    if (!visible) return

    val zone = ZoneId.systemDefault()
    var arrivalMode by remember(selected, arrivalSearch) { mutableStateOf(arrivalSearch) }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = localDateTimeToEpochMillis(selected, zone),
    )
    val timeState = rememberTimePickerState(
        initialHour = selected.hour,
        initialMinute = selected.minute,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_when)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !arrivalMode,
                        onClick = { arrivalMode = false },
                        label = { Text(stringResource(R.string.search_time_departure)) },
                    )
                    FilterChip(
                        selected = arrivalMode,
                        onClick = { arrivalMode = true },
                        label = { Text(stringResource(R.string.search_time_arrival)) },
                    )
                }
                DatePicker(state = dateState)
                TimePicker(state = timeState)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = dateState.selectedDateMillis
                        ?: localDateTimeToEpochMillis(selected, zone)
                    val date = epochMillisToLocalDateTime(millis, zone).toLocalDate()
                    val time = java.time.LocalTime.of(timeState.hour, timeState.minute)
                    onConfirm(LocalDateTime.of(date, time), arrivalMode)
                },
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        onConfirm(LocalDateTime.now(), arrivalMode)
                    },
                ) {
                    Text(stringResource(R.string.search_time_now))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
    )
}
