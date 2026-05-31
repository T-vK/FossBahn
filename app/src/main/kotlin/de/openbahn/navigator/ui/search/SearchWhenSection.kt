package de.openbahn.navigator.ui.search

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.openbahn.navigator.R
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

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
    val isNow = remember(departureTime) {
        abs(Duration.between(departureTime, LocalDateTime.now()).toMinutes()) <= 1
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

        MultiChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .height(IntrinsicSize.Min)
                .testTag("search_datetime_row"),
        ) {
            SegmentedButton(
                checked = false,
                onCheckedChange = { openDatePicker() },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("search_date_open"),
                icon = {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = stringResource(R.string.search_pick_date),
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                },
                label = {
                    DateTimeSegmentLabel(
                        caption = stringResource(R.string.search_date),
                        value = dateLabel,
                        valueTestTag = "search_date_field",
                    )
                },
            )
            SegmentedButton(
                checked = false,
                onCheckedChange = { openTimePicker() },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("search_time_open"),
                icon = {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = stringResource(R.string.search_pick_time),
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                },
                label = {
                    DateTimeSegmentLabel(
                        caption = stringResource(R.string.search_time),
                        value = timeLabel,
                        valueTestTag = "search_time_field",
                    )
                },
            )
            SegmentedButton(
                checked = isNow,
                onCheckedChange = { checked ->
                    if (checked) onDepartureTimeChange(LocalDateTime.now())
                },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("search_time_now"),
                label = {
                    Text(
                        text = stringResource(R.string.search_time_now),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                    )
                },
            )
        }
    }
}


@Composable
private fun DateTimeSegmentLabel(
    caption: String,
    value: String,
    valueTestTag: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(valueTestTag),
        )
    }
}
