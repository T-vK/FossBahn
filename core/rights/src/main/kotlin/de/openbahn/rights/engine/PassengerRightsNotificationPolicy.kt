package de.openbahn.rights.engine

import de.openbahn.rights.model.PassengerRightsAssessment
import de.openbahn.rights.model.RightsNotificationKind
import de.openbahn.rights.model.RightsNotificationSuggestion

/**
 * Selects at most one notification per evaluation cycle to avoid alert fatigue.
 */
object PassengerRightsNotificationPolicy {
    data class Decision(
        val shouldNotify: Boolean,
        val notification: RightsNotificationSuggestion?,
    )

    fun evaluate(
        assessment: PassengerRightsAssessment,
        lastNotifiedAtEpochMillis: Long?,
        minIntervalMillis: Long = 30 * 60 * 1000L,
    ): Decision {
        if (assessment.notifications.isEmpty()) return Decision(false, null)
        val now = assessment.evaluatedAtEpochMillis
        if (lastNotifiedAtEpochMillis != null && now - lastNotifiedAtEpochMillis < minIntervalMillis) {
            return Decision(false, null)
        }
        val priority = listOf(
            RightsNotificationKind.TAXI_MAY_BE_REQUIRED,
            RightsNotificationKind.NO_PUBLIC_TRANSPORT,
            RightsNotificationKind.FERNVERKEHR_FALLBACK_AVAILABLE,
            RightsNotificationKind.DTICKET_CAP_WARNING,
            RightsNotificationKind.DELAY_60_THRESHOLD,
        )
        val chosen = priority.firstNotNull { kind ->
            assessment.notifications.firstOrNull { it.kind == kind }
        } ?: assessment.notifications.first()
        return Decision(true, chosen)
    }

    private inline fun <T, R : Any> Iterable<T>.firstNotNull(transform: (T) -> R?): R? {
        for (element in this) {
            val result = transform(element)
            if (result != null) return result
        }
        return null
    }
}
