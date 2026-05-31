package de.openbahn.navigator.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import de.openbahn.model.StoredTicket
import de.openbahn.model.TicketType
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tickets")
data class TicketEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: TicketType,
    val validFrom: String?,
    val validUntil: String?,
    val holderName: String?,
    val qrCodeData: String?,
    val photoUri: String?,
    val pdfUri: String?,
    val notes: String?,
    val importedAt: Long,
)

fun TicketEntity.toModel() = StoredTicket(
    id = id,
    title = title,
    type = type,
    validFrom = validFrom,
    validUntil = validUntil,
    holderName = holderName,
    qrCodeData = qrCodeData,
    photoUri = photoUri,
    pdfUri = pdfUri,
    notes = notes,
    importedAt = importedAt,
)

fun StoredTicket.toEntity() = TicketEntity(
    id = id,
    title = title,
    type = type,
    validFrom = validFrom,
    validUntil = validUntil,
    holderName = holderName,
    qrCodeData = qrCodeData,
    photoUri = photoUri,
    pdfUri = pdfUri,
    notes = notes,
    importedAt = importedAt,
)

@Dao
interface TicketDao {
    @Query("SELECT * FROM tickets ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<TicketEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ticket: TicketEntity)

    @Query("DELETE FROM tickets WHERE id = :id")
    suspend fun delete(id: String)
}

@Entity(tableName = "tracked_journeys")
data class TrackedJourneyEntity(
    @PrimaryKey val id: String,
    val fromName: String,
    val toName: String,
    val departureIso: String,
    val refreshToken: String?,
    val journeyJson: String,
    /** Last delay (minutes) we alerted for; null until the first notification. */
    val lastNotifiedDelayMinutes: Int? = null,
    val active: Boolean,
)

@Dao
interface TrackedJourneyDao {
    @Query("SELECT * FROM tracked_journeys WHERE active = 1")
    fun observeActive(): Flow<List<TrackedJourneyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(journey: TrackedJourneyEntity)

    @Query("UPDATE tracked_journeys SET active = 0 WHERE id = :id")
    suspend fun deactivate(id: String)

    @Query("SELECT * FROM tracked_journeys WHERE active = 1")
    suspend fun getActive(): List<TrackedJourneyEntity>
}
