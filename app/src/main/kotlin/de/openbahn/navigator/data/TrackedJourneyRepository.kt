package de.openbahn.navigator.data

import de.openbahn.model.Journey
import de.openbahn.navigator.ui.util.isIsoLongArrived
import de.openbahn.navigator.ui.util.isJourneyLongArrived
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TrackedJourneyRepository(private val dao: TrackedJourneyDao) {
    fun observeActive(): Flow<List<TrackedJourneyWithJourney>> = dao.observeActive().map { list ->
        list.mapNotNull { entity ->
            val journey = runCatching { Json.decodeFromString<Journey>(entity.journeyJson) }.getOrNull()
                ?: return@mapNotNull null
            if (isJourneyLongArrived(journey) || isIsoLongArrived(entity.departureIso)) {
                return@mapNotNull null
            }
            TrackedJourneyWithJourney(entity = entity, journey = journey)
        }
    }

    suspend fun track(journey: Journey, fromName: String, toName: String, notifyDelayMinutes: Int = 5) {
        if (isJourneyLongArrived(journey)) return
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

    suspend fun getActiveForWorker(): List<TrackedJourneyEntity> =
        dao.getActive().filter { entity ->
            val journey = runCatching { Json.decodeFromString<Journey>(entity.journeyJson) }.getOrNull()
            when {
                journey != null -> !isJourneyLongArrived(journey)
                else -> !isIsoLongArrived(entity.departureIso)
            }
        }

    suspend fun pruneArrived() {
        dao.getActive().forEach { entity ->
            val journey = runCatching { Json.decodeFromString<Journey>(entity.journeyJson) }.getOrNull()
            val arrived = when {
                journey != null -> isJourneyLongArrived(journey)
                else -> isIsoLongArrived(entity.departureIso)
            }
            if (arrived) dao.deactivate(entity.id)
        }
    }
}

data class TrackedJourneyWithJourney(
    val entity: TrackedJourneyEntity,
    val journey: Journey,
)
