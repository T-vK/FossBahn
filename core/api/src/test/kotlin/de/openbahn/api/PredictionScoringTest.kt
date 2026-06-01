package de.openbahn.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PredictionScoringTest {
    @Test
    fun probabilityDelayAtMost_sumsBucketsUpToTolerance() {
        val distribution = listOf(0.1, 0.2, 0.4, 0.2, 0.1)
        val offset = 2
        assertEquals(0.7, PredictionScoring.probabilityDelayAtMost(distribution, offset, 0), 0.001)
        assertEquals(1.0, PredictionScoring.probabilityDelayAtMost(distribution, offset, 2), 0.001)
    }

    @Test
    fun probabilityExactlyOnTime_usesSingleBucketAtOffset() {
        val distribution = listOf(0.1, 0.1, 0.8, 0.0)
        assertEquals(0.8, PredictionScoring.probabilityExactlyOnTime(distribution, offset = 2), 0.001)
    }

    @Test
    fun transferSuccessFromDistribution_usesSlackAgainstMinTransfer() {
        val distribution = listOf(0.05, 0.15, 0.5, 0.2, 0.1)
        val score = PredictionScoring.transferSuccessFromDistribution(
            distribution = distribution,
            offset = 2,
            prognosedTransferMinutes = 12,
            minTransferMinutes = 12,
        )
        assertEquals(0.7, score, 0.001)
    }
}
