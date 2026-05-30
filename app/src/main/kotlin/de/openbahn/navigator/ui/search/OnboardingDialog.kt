package de.openbahn.navigator.ui.search

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.openbahn.navigator.R

@Composable
fun DeutschlandTicketOnboardingDialog(
    onDismissOnly: () -> Unit,
    onEnableFilter: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissOnly,
        title = { Text(stringResource(R.string.onboarding_dticket_title)) },
        text = { Text(stringResource(R.string.onboarding_dticket_message)) },
        confirmButton = {
            TextButton(onClick = onEnableFilter) {
                Text(stringResource(R.string.onboarding_dticket_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissOnly) {
                Text(stringResource(R.string.onboarding_dticket_no))
            }
        },
    )
}
