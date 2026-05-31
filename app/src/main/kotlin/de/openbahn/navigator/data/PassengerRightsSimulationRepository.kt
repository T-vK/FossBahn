package de.openbahn.navigator.data

import de.openbahn.model.Journey
import de.openbahn.model.PassengerRightsSimulationConfig
import de.openbahn.model.PassengerRightsSimulationPreset
import de.openbahn.model.applyPassengerRightsSimulation
import de.openbahn.model.toConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PassengerRightsSimulationRepository(
    private val userPreferences: UserPreferencesRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val config: Flow<PassengerRightsSimulationConfig> =
        userPreferences.passengerRightsSimulationJson.map { raw ->
            raw?.let { runCatching { json.decodeFromString<PassengerRightsSimulationConfig>(it) }.getOrNull() }
                ?: PassengerRightsSimulationConfig.Disabled
        }

    suspend fun currentConfig(): PassengerRightsSimulationConfig = config.first()

    suspend fun setConfig(config: PassengerRightsSimulationConfig) {
        val toStore = if (config.enabled) json.encodeToString(config) else null
        userPreferences.savePassengerRightsSimulationJson(toStore)
    }

    suspend fun applyPreset(preset: PassengerRightsSimulationPreset) {
        setConfig(preset.toConfig())
    }

    suspend fun applyToJourney(journey: Journey): Journey {
        val cfg = currentConfig()
        return journey.applyPassengerRightsSimulation(cfg)
    }
}
