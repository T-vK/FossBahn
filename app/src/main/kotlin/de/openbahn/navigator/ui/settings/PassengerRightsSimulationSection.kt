package de.openbahn.navigator.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.model.PassengerRightsSimulationPreset
import de.openbahn.model.toConfig
import de.openbahn.navigator.R
import de.openbahn.navigator.data.PassengerRightsSimulationRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerRightsSimulationSection(
    simulationRepository: PassengerRightsSimulationRepository = koinInject(),
) {
    val config by simulationRepository.config.collectAsState(
        initial = de.openbahn.model.PassengerRightsSimulationConfig.Disabled,
    )
    val scope = rememberCoroutineScope()
    val activePreset = presetForConfig(config)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.passenger_rights_simulation_heading),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.passenger_rights_simulation_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsFilterChipFlow {
            PassengerRightsSimulationPreset.entries.forEach { preset ->
                FilterChip(
                    selected = activePreset == preset,
                    onClick = { scope.launch { simulationRepository.applyPreset(preset) } },
                    label = { Text(presetLabel(preset)) },
                )
            }
        }
        if (config.enabled) {
            Text(
                stringResource(
                    R.string.passenger_rights_simulation_active,
                    config.arrivalDelayMinutes,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun presetLabel(preset: PassengerRightsSimulationPreset): String = when (preset) {
    PassengerRightsSimulationPreset.OFF -> stringResource(R.string.passenger_rights_sim_off)
    PassengerRightsSimulationPreset.DTICKET_DELAY_60 ->
        stringResource(R.string.passenger_rights_sim_dticket_60)
    PassengerRightsSimulationPreset.DTICKET_DELAY_120 ->
        stringResource(R.string.passenger_rights_sim_dticket_120)
    PassengerRightsSimulationPreset.EU_LONG_DISTANCE_60 ->
        stringResource(R.string.passenger_rights_sim_eu_60)
    PassengerRightsSimulationPreset.LAST_CONNECTION_TAXI ->
        stringResource(R.string.passenger_rights_sim_taxi)
}

private fun presetForConfig(
    config: de.openbahn.model.PassengerRightsSimulationConfig,
): PassengerRightsSimulationPreset {
    if (!config.enabled) return PassengerRightsSimulationPreset.OFF
    return PassengerRightsSimulationPreset.entries.firstOrNull { preset ->
        preset != PassengerRightsSimulationPreset.OFF && preset.toConfig() == config
    } ?: PassengerRightsSimulationPreset.OFF
}
