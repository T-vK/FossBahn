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
    }
}
