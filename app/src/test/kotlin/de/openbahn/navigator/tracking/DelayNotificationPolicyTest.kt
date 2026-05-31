package de.openbahn.navigator.tracking

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DelayNotificationPolicyTest {
    @Test
    fun firstNotification_whenDelayReachesIncrement() {
        val decision = DelayNotificationPolicy.evaluate(
            currentDelayMinutes = 1,
            lastNotifiedDelayMinutes = null,
            incrementMinutes = 1,
        )
        assertTrue(decision.shouldNotify)
    }

    @Test
    fun firstNotification_notBeforeIncrement() {
        val decision = DelayNotificationPolicy.evaluate(
            currentDelayMinutes = 0,
            lastNotifiedDelayMinutes = null,
            incrementMinutes = 1,
        )
        assertFalse(decision.shouldNotify)
    }

    @Test
    fun repeatNotification_whenDelayIncreasedByIncrement() {
        val decision = DelayNotificationPolicy.evaluate(
            currentDelayMinutes = 6,
            lastNotifiedDelayMinutes = 5,
            incrementMinutes = 1,
        )
        assertTrue(decision.shouldNotify)
    }

    @Test
    fun repeatNotification_notWhenIncreaseTooSmall() {
        val decision = DelayNotificationPolicy.evaluate(
            currentDelayMinutes = 5,
            lastNotifiedDelayMinutes = 5,
            incrementMinutes = 1,
        )
        assertFalse(decision.shouldNotify)
    }
}
