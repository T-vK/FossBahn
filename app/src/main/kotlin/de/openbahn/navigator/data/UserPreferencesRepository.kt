package de.openbahn.navigator.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

    val deutschlandTicketFilterDefault: Flow<Boolean> = dataStore.data.map {
        it[KEY_DTICKET_FILTER_DEFAULT] ?: false
    }

    val appLanguage: Flow<AppLanguage> = dataStore.data.map { prefs ->
        AppLanguage.fromStorage(prefs[KEY_APP_LANGUAGE])
    }

    suspend fun currentAppLanguage(): AppLanguage = appLanguage.first()

    suspend fun setAppLanguage(language: AppLanguage) {
        dataStore.edit { it[KEY_APP_LANGUAGE] = language.storageValue }
    }

    suspend fun completeOnboarding(deutschlandTicketOnlyDefault: Boolean) {
        dataStore.edit {
            it[KEY_ONBOARDING_DONE] = true
            it[KEY_DTICKET_FILTER_DEFAULT] = deutschlandTicketOnlyDefault
        }
    }

    companion object {
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        private val KEY_DTICKET_FILTER_DEFAULT = booleanPreferencesKey("dticket_filter_default")
        private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
    }
}
