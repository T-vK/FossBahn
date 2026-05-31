package de.openbahn.navigator.ui.search

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.navigator.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
    val context = LocalContext.current
    val dateLabel = remember(departureTime) {
        DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()).format(departureTime)
    }
    val timeLabel = remember(departureTime) {
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(departureTime)
    }

    val openDatePicker = remember(context, departureTime) {
        {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val date = LocalDate.of(year, month + 1, dayOfMonth)
                    onDepartureTimeChange(LocalDateTime.of(date, departureTime.toLocalTime()))
                },
                departureTime.year,
                departureTime.monthValue - 1,
                departureTime.dayOfMonth,
            ).show()
        }
    }

    val openTimePicker = remember(context, departureTime) {
        {
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val time = LocalTime.of(hour, minute)
                    onDepartureTimeChange(LocalDateTime.of(departureTime.toLocalDate(), time))
                },
                departureTime.hour,
                departureTime.minute,
                true,
            ).show()
        }
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = openDatePicker,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .testTag("search_date_open"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = stringResource(R.string.search_pick_date),
                    modifier = Modifier.padding(end = 6.dp),
                )
                Column {
                    Text(
                        text = stringResource(R.string.search_date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("search_date_field"),
                    )
                }
            }
            OutlinedButton(
                onClick = openTimePicker,
                modifier = Modifier
                    .weight(0.85f)
                    .heightIn(min = 40.dp)
                    .testTag("search_time_open"),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = stringResource(R.string.search_pick_time),
                    modifier = Modifier.padding(end = 4.dp),
                )
                Column {
                    Text(
                        text = stringResource(R.string.search_time),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("search_time_field"),
                    )
                }
            }
            FilledTonalButton(
                onClick = { onDepartureTimeChange(LocalDateTime.now()) },
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .testTag("search_time_now"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    stringResource(R.string.search_time_now),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
