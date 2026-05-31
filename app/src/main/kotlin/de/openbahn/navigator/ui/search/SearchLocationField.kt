package de.openbahn.navigator.ui.search

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.openbahn.navigator.R

@Composable
fun SearchLocationField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onFocusChanged: (Boolean) -> Unit,
    onUseCurrentLocation: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    leadingTrailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .onFocusChanged { onFocusChanged(it.isFocused) },
        singleLine = true,
        trailingIcon = {
            Row {
                leadingTrailing?.invoke()
                IconButton(onClick = onUseCurrentLocation) {
                    Icon(
                        Icons.Outlined.MyLocation,
                        contentDescription = stringResource(R.string.use_current_location),
                    )
                }
            }
        },
    )
}
