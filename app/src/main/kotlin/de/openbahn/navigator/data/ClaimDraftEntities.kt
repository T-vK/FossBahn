package de.openbahn.navigator.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import de.openbahn.rights.model.ClaimDraft
import de.openbahn.rights.model.ClaimDraftStatus
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "claim_drafts")
data class ClaimDraftEntity(
    @PrimaryKey val id: String,
    val journeyId: String,
    val createdAtEpochMillis: Long,
    val status: String,
    val assessmentJson: String,
    val subject: String,
    val bodyText: String,
    val recipientEmail: String?,
    val lastRightsNotifiedAtEpochMillis: Long? = null,
)

@Dao
interface ClaimDraftDao {
    @Query("SELECT * FROM claim_drafts ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<ClaimDraftEntity>>

    @Query("SELECT * FROM claim_drafts WHERE journeyId = :journeyId ORDER BY createdAtEpochMillis DESC LIMIT 1")
    suspend fun latestForJourney(journeyId: String): ClaimDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ClaimDraftEntity)

    @Query("UPDATE claim_drafts SET lastRightsNotifiedAtEpochMillis = :at WHERE id = :id")
    suspend fun updateLastNotified(id: String, at: Long)

    @Query("UPDATE claim_drafts SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("SELECT lastRightsNotifiedAtEpochMillis FROM claim_drafts WHERE id = :id")
    suspend fun lastNotifiedAt(id: String): Long?
}

fun ClaimDraftEntity.toClaimDraft() = ClaimDraft(
    id = id,
    journeyId = journeyId,
    createdAtEpochMillis = createdAtEpochMillis,
    status = ClaimDraftStatus.valueOf(status),
    assessmentJson = assessmentJson,
    subject = subject,
    bodyText = bodyText,
    recipientEmail = recipientEmail,
)

fun ClaimDraft.toEntity(lastNotified: Long? = null) = ClaimDraftEntity(
    id = id,
    journeyId = journeyId,
    createdAtEpochMillis = createdAtEpochMillis,
    status = status.name,
    assessmentJson = assessmentJson,
    subject = subject,
    bodyText = bodyText,
    recipientEmail = recipientEmail,
    lastRightsNotifiedAtEpochMillis = lastNotified,
)
