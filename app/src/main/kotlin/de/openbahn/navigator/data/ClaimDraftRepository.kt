package de.openbahn.navigator.data

import de.openbahn.rights.model.ClaimDraft
import de.openbahn.rights.model.MonthlyLedger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ClaimDraftRepository(
    private val dao: ClaimDraftDao,
    private val userPreferences: UserPreferencesRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeDrafts(): Flow<List<ClaimDraft>> =
        dao.observeAll().map { list -> list.map { it.toClaimDraft() } }

    suspend fun saveDraft(draft: ClaimDraft) {
        dao.upsert(draft.toEntity())
    }

    suspend fun latestForJourney(journeyId: String): ClaimDraft? =
        dao.latestForJourney(journeyId)?.toClaimDraft()

    suspend fun markNotified(draftId: String, atEpochMillis: Long) {
        dao.updateLastNotified(draftId, atEpochMillis)
    }

    suspend fun lastNotifiedAt(draftId: String): Long? = dao.lastNotifiedAt(draftId)

    suspend fun loadMonthlyLedger(yearMonth: String): MonthlyLedger? {
        val raw = userPreferences.loadDticketLedgerJson(yearMonth) ?: return null
        return runCatching { json.decodeFromString<MonthlyLedger>(raw) }.getOrNull()
    }

    suspend fun saveMonthlyLedger(ledger: MonthlyLedger) {
        userPreferences.saveDticketLedgerJson(ledger.yearMonth, json.encodeToString(ledger))
    }
}
