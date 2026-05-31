package de.openbahn.navigator.ui.tracking

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.navigator.R
import de.openbahn.navigator.navigation.JourneyNavigation
import de.openbahn.navigator.ui.components.JourneyCard
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    onOpenJourneyDetail: () -> Unit,
    onShowAlternatives: () -> Unit,
    viewModel: TrackingViewModel = koinViewModel(),
) {
    val tracked by viewModel.tracked.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val showBatteryBanner by viewModel.showBatteryOptimizationBanner.collectAsState()
    var showInfoDialog by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    val listState = rememberLazyListState()

    LaunchedEffect(tracked.isNotEmpty()) {
        if (tracked.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(stringResource(R.string.tracking_title)) },
            text = { Text(stringResource(R.string.tracking_description)) },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tracking_title)) },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.tracking_info),
                        )
                    }
                    IconButton(
                        onClick = { viewModel.refreshNow(force = true) },
                        enabled = !isRefreshing,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (showBatteryBanner) {
                BatteryOptimizationBanner(
                    onDismiss = { viewModel.dismissBatteryOptimizationPrompt() },
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            if (tracked.isEmpty()) {
                Text(stringResource(R.string.tracking_empty))
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tracked, key = { it.entity.id }) { item ->
                        Column(Modifier.fillMaxWidth()) {
                            JourneyCard(
                                journey = item.journey,
                                predictionsRequested = true,
                                onOpenFullscreen = {
                                    JourneyNavigation.set(item.journey, predictionsRequested = true)
                                    onOpenJourneyDetail()
                                },
                            )
                            if (item.entity.refreshToken.isNullOrBlank()) {
                                Text(
                                    stringResource(R.string.tracking_no_refresh_token),
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        viewModel.openAlternatives(item.entity.id, onShowAlternatives)
                                    },
                                ) {
                                    Text(stringResource(R.string.tracking_other_connections))
                                }
                                TextButton(onClick = { viewModel.stopTracking(item.entity.id) }) {
                                    Text(stringResource(R.string.stop_tracking))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
