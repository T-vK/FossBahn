package de.openbahn.navigator.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import de.openbahn.api.JourneyRatingOptions
import de.openbahn.navigator.tracking.DelayNotificationPolicy
import androidx.datastore.preferences.preferencesDataStore
import de.openbahn.navigator.locale.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore("user_prefs")

class UserPreferencesRepository(private val context: Context) {
    private val dataStore = context.userPrefsDataStore

    val onboardingCompleted: Flow<Boolean> = dataStore.data.map {
        it[KEY_ONBOARDING_DONE] ?: false
    }

    /** Default for “D-Ticket connections only”; updated from Settings, Options, or onboarding. */
    val deutschlandTicketConnectionsOnly: Flow<Boolean> = dataStore.data.map {
        it[KEY_DTICKET_CONNECTIONS_ONLY] ?: false
    }

    val appLanguage: Flow<AppLanguage> = dataStore.data.map { prefs ->
        AppLanguage.fromStorage(prefs[KEY_APP_LANGUAGE])
    }

    val punctualityToleranceMinutes: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_PUNCTUALITY_TOLERANCE]?.coerceIn(0, 30)
            ?: JourneyRatingOptions.DEFAULT_PUNCTUALITY_TOLERANCE_MINUTES
    }

    /** Extra delay (minutes) on a tracked journey before sending another alert. */
    val delayNotificationIncrementMinutes: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_DELAY_NOTIFICATION_INCREMENT]?.coerceIn(1, 60)
            ?: DelayNotificationPolicy.DEFAULT_INCREMENT_MINUTES
    }

    /** How often to refresh tracked journeys when departure is under 20 minutes away. */
    val nearDepartureCheckIntervalSeconds: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_NEAR_DEPARTURE_CHECK_SECONDS]?.coerceIn(5, 120)
            ?: DEFAULT_NEAR_DEPARTURE_CHECK_SECONDS
    }

    val batteryOptimizationPromptDismissed: Flow<Boolean> = dataStore.data.map {
        it[KEY_BATTERY_OPTIMIZATION_DISMISSED] ?: false
    }

    val passengerRightsNotificationsEnabled: Flow<Boolean> = dataStore.data.map {
        it[KEY_PASSENGER_RIGHTS_NOTIFICATIONS] ?: true
    }

    val passengerRightsSimulationJson: Flow<String?> = dataStore.data.map {
        it[KEY_PASSENGER_RIGHTS_SIMULATION]
    }

    suspend fun currentAppLanguage(): AppLanguage = appLanguage.first()

    suspend fun setAppLanguage(language: AppLanguage) {
        dataStore.edit { it[KEY_APP_LANGUAGE] = language.storageValue }
    }

    suspend fun setPunctualityToleranceMinutes(minutes: Int) {
        dataStore.edit {
            it[KEY_PUNCTUALITY_TOLERANCE] = minutes.coerceIn(0, 30)
        }
    }

    suspend fun setDelayNotificationIncrementMinutes(minutes: Int) {
        dataStore.edit {
            it[KEY_DELAY_NOTIFICATION_INCREMENT] = minutes.coerceIn(1, 60)
        }
    }

    suspend fun setNearDepartureCheckIntervalSeconds(seconds: Int) {
        dataStore.edit {
            it[KEY_NEAR_DEPARTURE_CHECK_SECONDS] = seconds.coerceIn(5, 120)
        }
    }

    suspend fun setBatteryOptimizationPromptDismissed(dismissed: Boolean) {
        dataStore.edit { it[KEY_BATTERY_OPTIMIZATION_DISMISSED] = dismissed }
    }

    suspend fun setPassengerRightsNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_PASSENGER_RIGHTS_NOTIFICATIONS] = enabled }
    }

    suspend fun loadDticketLedgerJson(yearMonth: String): String? =
        dataStore.data.first()[dticketLedgerKey(yearMonth)]

    suspend fun saveDticketLedgerJson(yearMonth: String, json: String) {
        dataStore.edit { it[dticketLedgerKey(yearMonth)] = json }
    }

    suspend fun savePassengerRightsSimulationJson(json: String?) {
        dataStore.edit { prefs ->
            if (json == null) {
                prefs.remove(KEY_PASSENGER_RIGHTS_SIMULATION)
            } else {
                prefs[KEY_PASSENGER_RIGHTS_SIMULATION] = json
            }
        }
    }

    suspend fun setDeutschlandTicketConnectionsOnly(enabled: Boolean) {
        dataStore.edit { it[KEY_DTICKET_CONNECTIONS_ONLY] = enabled }
    }

    suspend fun completeOnboarding(deutschlandTicketOnlyDefault: Boolean) {
        dataStore.edit {
            it[KEY_ONBOARDING_DONE] = true
            it[KEY_DTICKET_CONNECTIONS_ONLY] = deutschlandTicketOnlyDefault
        }
    }

    companion object {
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        private val KEY_DTICKET_CONNECTIONS_ONLY = booleanPreferencesKey("dticket_filter_default")
        private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        private val KEY_PUNCTUALITY_TOLERANCE = intPreferencesKey("punctuality_tolerance_minutes")
        private val KEY_DELAY_NOTIFICATION_INCREMENT = intPreferencesKey("delay_notification_increment_minutes")
        private val KEY_NEAR_DEPARTURE_CHECK_SECONDS = intPreferencesKey("near_departure_check_seconds")
        private val KEY_BATTERY_OPTIMIZATION_DISMISSED =
            booleanPreferencesKey("battery_optimization_prompt_dismissed")
        private val KEY_PASSENGER_RIGHTS_NOTIFICATIONS =
            booleanPreferencesKey("passenger_rights_notifications")
        private val KEY_PASSENGER_RIGHTS_SIMULATION =
            stringPreferencesKey("passenger_rights_simulation_json")

        const val DEFAULT_NEAR_DEPARTURE_CHECK_SECONDS = 5

        private fun dticketLedgerKey(yearMonth: String) =
            stringPreferencesKey("dticket_ledger_$yearMonth")
    }
}
