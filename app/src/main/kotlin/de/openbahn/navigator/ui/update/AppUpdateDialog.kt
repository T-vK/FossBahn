package de.openbahn.navigator.ui.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.openbahn.navigator.R

@Composable
fun AppUpdateDialog(
    versionName: String,
    onInstall: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.app_update_dialog_title)) },
        text = { Text(stringResource(R.string.app_update_dialog_message, versionName)) },
        confirmButton = {
            TextButton(onClick = onInstall) {
                Text(stringResource(R.string.app_update_dialog_install))
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text(stringResource(R.string.app_update_dialog_later))
            }
        },
    )
}
