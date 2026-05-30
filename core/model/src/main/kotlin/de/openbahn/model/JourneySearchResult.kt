package de.openbahn.model

import kotlinx.serialization.Serializable

@Serializable
data class JourneySearchResult(
    val journeys: List<Journey> = emptyList(),
    /** Token for `pagingReference` to load earlier connections (same search). */
    val pagingEarlier: String? = null,
    /** Token for `pagingReference` to load later connections (same search). */
    val pagingLater: String? = null,
)
