package de.openbahn.api

/**
 * Interprets Bahn-Vorhersage delay PMFs (`predictions` + `offset`).
 * Index [i] is the probability that delay equals [i - offset] minutes (relative to schedule).
 */
internal object PredictionScoring {
    fun probabilityDelayAtMost(
        distribution: List<Double>,
        offset: Int,
        maxDelayMinutes: Int,
    ): Double {
        if (distribution.isEmpty()) return 0.0
        var sum = 0.0
        distribution.forEachIndexed { index, probability ->
            val delayMinutes = index - offset
            if (delayMinutes <= maxDelayMinutes) {
                sum += probability
            }
        }
        return sum.coerceIn(0.0, 1.0)
    }

    /**
     * Probability the transfer works when [minTransferMinutes] is required,
     * given [prognosedTransferMinutes] scheduled/prognosed gap and delay PMF at the transfer.
     */
    fun transferSuccessFromDistribution(
        distribution: List<Double>,
        offset: Int,
        prognosedTransferMinutes: Long,
        minTransferMinutes: Int,
    ): Double {
        val slack = prognosedTransferMinutes - minTransferMinutes
        if (slack < 0) return 0.0
        return probabilityDelayAtMost(distribution, offset, slack.toInt())
    }
}
