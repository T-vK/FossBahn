package de.openbahn.api

import de.openbahn.model.StopEvent
import de.openbahn.model.TransportProduct

/** Shared station coordinates / line metadata for Bahn-Vorhersage requests. */
internal object BahnVorhersageStationData {
    fun coordsForStop(stop: StopEvent): Pair<Double, Double>? {
        coordsFromHaltId(stop.id)?.let { return it }
        return defaultCoordsForName(stop.name)
    }

    fun coordsFromHaltId(id: String?): Pair<Double, Double>? {
        if (id.isNullOrBlank()) return null
        val m = Regex("""@X=(\d+)@Y=(\d+)@""").find(id) ?: return null
        val lon = m.groupValues[1].toDoubleOrNull()?.div(1_000_000.0) ?: return null
        val lat = m.groupValues[2].toDoubleOrNull()?.div(1_000_000.0) ?: return null
        return lat to lon
    }

    fun defaultCoordsForName(name: String): Pair<Double, Double>? = when {
        name.contains("Berlin Hbf", ignoreCase = true) -> 52.525 to 13.369
        name.contains("Hamburg Hbf", ignoreCase = true) -> 53.553 to 10.006
        name.contains("München Hbf", ignoreCase = true) || name.contains("Munich", ignoreCase = true) ->
            48.140 to 11.558
        name.contains("Köln Hbf", ignoreCase = true) || name.contains("Cologne", ignoreCase = true) ->
            50.943 to 6.958
        name.contains("Frankfurt", ignoreCase = true) -> 50.107 to 8.662
        else -> null
    }

    fun lineNumber(lineName: String?, lineDetail: String?): Int {
        val sources = listOfNotNull(lineDetail, lineName)
        for (source in sources) {
            Regex("""(\d{1,5})""").findAll(source).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        }
        return 0
    }

    fun categoryFromLeg(product: TransportProduct?, lineName: String?): String {
        product?.let {
            return when (it) {
                TransportProduct.ICE -> "ICE"
                TransportProduct.IC_EC -> "IC"
                TransportProduct.IR -> "IR"
                TransportProduct.REGIONAL -> "RE"
                TransportProduct.SBAHN -> "S"
                TransportProduct.UBAHN -> "U"
                TransportProduct.BUS -> "BUS"
                TransportProduct.TRAM -> "TRAM"
                TransportProduct.FERRY -> "SCHIFF"
                TransportProduct.ON_DEMAND -> "BUS"
            }
        }
        return categoryFromLine(lineName)
    }

    fun categoryFromLine(lineName: String?): String {
        val u = lineName?.uppercase().orEmpty()
        return when {
            u.startsWith("ICE") -> "ICE"
            u.startsWith("IC") || u.startsWith("EC") -> "IC"
            u.startsWith("RE") -> "RE"
            u.startsWith("RB") -> "RB"
            u.startsWith("S") -> "S"
            u.startsWith("U") -> "U"
            u.startsWith("BUS") -> "BUS"
            else -> "UNKNOWN"
        }
    }

    fun isRegional(product: TransportProduct?, lineName: String?): Boolean {
        if (product != null) {
            return product !in setOf(TransportProduct.ICE, TransportProduct.IC_EC, TransportProduct.IR)
        }
        val cat = categoryFromLine(lineName)
        return cat !in setOf("ICE", "IC", "EC")
    }
}
