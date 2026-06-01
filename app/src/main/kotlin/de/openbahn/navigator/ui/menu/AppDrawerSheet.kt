package de.openbahn.navigator.ui.menu

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.navigator.R

@Composable
fun AppDrawerSheet(
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onChangelog: () -> Unit,
    onDebugLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(modifier = modifier) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.settings_title)) },
            selected = false,
            onClick = onSettings,
            modifier = Modifier.fillMaxWidth(),
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            label = { Text(stringResource(R.string.menu_about)) },
            selected = false,
            onClick = onAbout,
            modifier = Modifier.fillMaxWidth(),
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
            label = { Text(stringResource(R.string.menu_changelog)) },
            selected = false,
            onClick = onChangelog,
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
            label = { Text(stringResource(R.string.menu_debug_logs)) },
            selected = false,
            onClick = onDebugLogs,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
