package de.openbahn.navigator.ui.search

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/** Clears focused fields and hides the soft keyboard (search screen). */
@Composable
fun rememberDismissSearchKeyboard(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return remember(focusManager, keyboard) {
        {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
}

fun Modifier.dismissKeyboardOnTap(dismiss: () -> Unit): Modifier =
    pointerInput(dismiss) {
        detectTapGestures(onTap = { dismiss() })
    }
