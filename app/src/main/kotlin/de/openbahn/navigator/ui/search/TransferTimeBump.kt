package de.openbahn.navigator.ui.search

/** Preset minimum-transfer options offered in filters and on connection transfer rows. */
val TRANSFER_TIME_PRESETS_MINUTES: List<Int> = listOf(5, 10, 15, 20, 30)

/**
 * Suggests higher minimum-transfer presets than the scheduled gap / current filter,
 * so the user can re-search with more change buffer.
 */
fun transferTimeBumpOptions(
    scheduledTransferMinutes: Int?,
    currentMinTransferMinutes: Int?,
    limit: Int = 3,
): List<Int> {
    val floor = maxOf(scheduledTransferMinutes ?: 0, currentMinTransferMinutes ?: 0)
    return TRANSFER_TIME_PRESETS_MINUTES.filter { it > floor }.take(limit)
}
