package de.openbahn.navigator.data

import de.openbahn.model.Journey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TrackedJourneyRepository(private val dao: TrackedJourneyDao) {
    fun observeActive(): Flow<List<TrackedJourneyEntity>> = dao.observeActive()

    suspend fun track(journey: Journey, fromName: String, toName: String, notifyDelayMinutes: Int = 5) {
        val entity = TrackedJourneyEntity(
            id = journey.id,
            fromName = fromName,
            toName = toName,
            departureIso = journey.departure,
            refreshToken = journey.refreshToken,
            journeyJson = Json.encodeToString(journey),
            notifyOnDelayMinutes = notifyDelayMinutes,
            active = true,
        )
        dao.upsert(entity)
    }

    suspend fun stopTracking(id: String) = dao.deactivate(id)

    suspend fun getActiveForWorker(): List<TrackedJourneyEntity> = dao.getActive()
}
