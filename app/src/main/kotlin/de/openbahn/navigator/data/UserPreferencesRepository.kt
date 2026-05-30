package de.openbahn.navigator.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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

    suspend fun completeOnboarding(deutschlandTicketOnlyDefault: Boolean) {
        dataStore.edit {
            it[KEY_ONBOARDING_DONE] = true
            it[KEY_DTICKET_FILTER_DEFAULT] = deutschlandTicketOnlyDefault
        }
    }

    companion object {
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        private val KEY_DTICKET_FILTER_DEFAULT = booleanPreferencesKey("dticket_filter_default")
    }
}
