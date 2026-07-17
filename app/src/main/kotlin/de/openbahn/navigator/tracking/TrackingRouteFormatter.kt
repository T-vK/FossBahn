package de.openbahn.navigator.tracking

import de.openbahn.model.Journey
import de.openbahn.model.railLegs

private const val ROUTE_SEPARATOR = " -> "
private const val ELLIPSIS = "..."

/**
 * The ordered chain of station names travelled through, built from the journey's rail legs:
 * the first rail leg's origin followed by every rail leg's destination (transfer points and the
 * final arrival). Consecutive duplicates are collapsed so a same-station transfer shows only once.
 *
 * Returns an empty list when the journey has no rail legs, letting callers fall back to the stored
 * from/to names.
 */
fun routeStationNames(journey: Journey): List<String> {
    val rails = journey.railLegs()
    if (rails.isEmpty()) return emptyList()
    val names = mutableListOf(rails.first().origin.name)
    rails.forEach { names += it.destination.name }
    return names
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .dedupeConsecutive()
}

private fun List<String>.dedupeConsecutive(): List<String> {
    val result = mutableListOf<String>()
    for (name in this) if (result.lastOrNull() != name) result += name
    return result
}

/**
 * Joins [names] into a `A -> B -> C` route title that fits within [maxLength] characters.
 *
 * Notification titles cannot wrap on all Android versions, so when the joined title is too long the
 * currently longest station name is progressively shortened by replacing its trailing characters
 * with `...`, one step at a time, until the title fits or no name can shrink any further.
 */
fun fitRouteTitle(names: List<String>, maxLength: Int = 50): String {
    val states = names.map { NameState(it) }
    fun render() = states.joinToString(ROUTE_SEPARATOR) { it.display() }
    while (render().length > maxLength) {
        val target = states
            .filter { it.canShrink() }
            .maxByOrNull { it.displayLength() }
            ?: break
        target.shrink()
    }
    return render()
}

/**
 * A single station name that can be progressively truncated. [kept] is `null` while the full name is
 * shown; once truncated it holds the number of leading characters kept before the [ELLIPSIS].
 */
private class NameState(private val original: String) {
    private var kept: Int? = null

    fun display(): String = kept?.let { original.take(it) + ELLIPSIS } ?: original

    fun displayLength(): Int = kept?.let { it + ELLIPSIS.length } ?: original.length

    /** True while shrinking would still reduce the displayed length. */
    fun canShrink(): Boolean = kept?.let { it > 1 } ?: (original.length > ELLIPSIS.length + 1)

    fun shrink() {
        val current = kept
        kept = if (current == null) original.length - (ELLIPSIS.length + 1) else current - 1
    }
}
