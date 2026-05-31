package de.openbahn.api

/** Parses bahn.de halt coordinates from `@X` (longitude) and `@Y` (latitude) micro-degrees. */
fun coordinatesFromHaltId(haltId: String?): Pair<Double, Double>? {
    if (haltId.isNullOrBlank()) return null
    val lonMicro = HALT_X.find(haltId)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: return null
    val latMicro = HALT_Y.find(haltId)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: return null
    return latMicro / COORD_MICRO_DEGREES to lonMicro / COORD_MICRO_DEGREES
}

private const val COORD_MICRO_DEGREES = 1_000_000.0
private val HALT_X = Regex("""@X=(\d+)@""")
private val HALT_Y = Regex("""@Y=(\d+)@""")
