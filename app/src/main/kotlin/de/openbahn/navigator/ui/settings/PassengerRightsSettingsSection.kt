package de.openbahn.navigator.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.navigator.R
import de.openbahn.navigator.data.UserPreferencesRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun PassengerRightsSettingsSection(
    onOpenClaims: () -> Unit,
    userPreferences: UserPreferencesRepository = koinInject(),
) {
    val enabled by userPreferences.passengerRightsNotificationsEnabled.collectAsState(initial = true)
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.passenger_rights_notifications),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    scope.launch { userPreferences.setPassengerRightsNotificationsEnabled(checked) }
                },
            )
        }
        Text(
            stringResource(R.string.passenger_rights_claims_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onOpenClaims)
                .padding(top = 8.dp),
        )
        Text(
            stringResource(R.string.passenger_rights_claims_settings_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
