package de.openbahn.navigator.ui.search

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
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
    onImeDone: (() -> Unit)? = null,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }

    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = fieldValue.copy(text = value, selection = TextRange(value.length))
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { updated ->
            fieldValue = updated
            onValueChange(updated.text)
        },
        label = { Text(label) },
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .onFocusChanged { focusState ->
                if (focusState.isFocused && fieldValue.text.isNotEmpty()) {
                    fieldValue = fieldValue.copy(
                        selection = TextRange(0, fieldValue.text.length),
                    )
                }
                onFocusChanged(focusState.isFocused)
            },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = { onImeDone?.invoke() },
        ),
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
