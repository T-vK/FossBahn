package de.openbahn.navigator.tracking

/**
 * Whether to post a delay alert and the delay value to store as [lastNotifiedDelayMinutes].
 */
internal data class DelayNotificationDecision(
    val shouldNotify: Boolean,
    val delayMinutes: Int,
)

internal object DelayNotificationPolicy {
    const val DEFAULT_INCREMENT_MINUTES = 1

    fun evaluate(
        currentDelayMinutes: Int,
        lastNotifiedDelayMinutes: Int?,
        incrementMinutes: Int,
    ): DelayNotificationDecision {
        val increment = incrementMinutes.coerceAtLeast(1)
        val delay = currentDelayMinutes.coerceAtLeast(0)
        val shouldNotify = when (val last = lastNotifiedDelayMinutes) {
            null -> delay >= increment
            else -> delay >= last + increment
        }
        return DelayNotificationDecision(shouldNotify = shouldNotify, delayMinutes = delay)
    }
}
