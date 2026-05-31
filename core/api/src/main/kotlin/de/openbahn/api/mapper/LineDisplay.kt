package de.openbahn.api.mapper

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Linienbezeichnung (primary) and Zug-/Fahrtnummer (detail), when both are known. */
data class LineDisplay(
    val primary: String?,
    val detail: String? = null,
)

object LineDisplayMapper {
    fun fromVerkehrsmittel(vm: JsonObject?, journeyId: String? = null): LineDisplay {
        if (vm == null) return LineDisplay(null)
        val name = text(vm, "name")
        val kurz = text(vm, "kurzText")
        val mittel = text(vm, "mittelText")
        val lang = text(vm, "langText")
        val product = text(vm, "produktGattung")
        val lineNumber = text(vm, "linienNummer") ?: text(vm, "nummer")
        val zb = labelFromJourneyId(journeyId)

        val tripNumber = name?.let(::tripNumberLabel)
            ?: name?.takeIf { isBareTripNumber(it) }

        val line = firstUsable(
            mittel?.takeIf { isPublicLineLabel(it) && !isBareTripNumber(it) },
            lang?.takeIf { isPublicLineLabel(it) && !isBareTripNumber(it) },
            composedLineLabel(kurz, product, lineNumber)?.takeIf { lineNumber == null || isLikelyLineNumber(lineNumber) },
            zb?.takeIf { isPublicLineLabel(it) && !labelsEquivalent(it, tripNumber.orEmpty()) },
            kurz?.takeIf { isPublicLineLabel(it) && !isBareProductCode(it) },
            name?.takeIf { isPublicLineLabel(it) && tripNumber == null && !isBareTripNumber(it) },
        )

        val detail = firstUsable(
            tripNumber,
            name?.takeIf { isUsefulTripDetail(it, line) },
            zb?.takeIf { isUsefulTripDetail(it, line) },
        )

        val primary = line ?: detail?.takeIf { tripNumber == null }
        val secondary = detail?.takeIf { primary != null && !labelsEquivalent(primary, it) }
        return LineDisplay(primary, secondary)
    }

    private fun text(obj: JsonObject?, key: String): String? {
        val el = obj?.get(key) ?: return null
        return when (el) {
            is JsonPrimitive -> el.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            else -> null
        }
    }

    private fun isBareTripNumber(value: String): Boolean {
        val t = value.trim()
        if (Regex("""^\d{4,6}$""").matches(t)) return true
        return tripNumberLabel(t) != null
    }

    private fun tripNumberLabel(name: String): String? {
        val t = name.trim()
        if (Regex("""^\d{4,6}$""").matches(t)) return t
        val match = Regex(
            """^(RE|RB|SB|S|ME)\s*(\d{3,})$""",
            RegexOption.IGNORE_CASE,
        ).find(t) ?: return null
        val num = match.groupValues[2].toIntOrNull() ?: return null
        if (num < 100) return null
        return "${match.groupValues[1].uppercase()} $num"
    }

    private fun isLikelyLineNumber(num: String): Boolean {
        val n = num.trim().toIntOrNull() ?: return num.length <= 3
        return n < 100
    }

    private fun isBareProductCode(kurz: String): Boolean =
        kurz.trim().length <= 3 && !kurz.any { it.isDigit() }

    private fun isPublicLineLabel(value: String): Boolean {
        val t = value.trim()
        if (t.isEmpty() || t.length > 48) return false
        if (t.contains('#') || t.contains('|')) return false
        if (isBareTripNumber(t)) return false
        if (t.contains('.') && t.split(' ').size > 4) return false
        return true
    }

    private fun isUsefulTripDetail(candidate: String, line: String?): Boolean {
        if (!isPublicLineLabel(candidate) && !isBareTripNumber(candidate)) return false
        if (line == null) return isBareTripNumber(candidate)
        return !labelsEquivalent(candidate, line)
    }

    private fun labelsEquivalent(a: String, b: String): Boolean =
        normalizeLabel(a) == normalizeLabel(b)

    private fun normalizeLabel(label: String): String =
        label.trim().replace(Regex("""\s+"""), " ").uppercase()

    private fun composedLineLabel(kurz: String?, product: String?, lineNumber: String?): String? {
        val num = lineNumber?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!isLikelyLineNumber(num)) return null
        val prefix = kurz?.trim()?.takeIf { it.isNotEmpty() && !isBareProductCode(it) }
            ?: productCodeLabel(product)
            ?: kurz?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        if (prefix.equals(num, ignoreCase = true)) return prefix
        return "$prefix $num".replace(Regex("""\s+"""), " ").trim()
    }

    private fun productCodeLabel(product: String?): String? {
        if (product.isNullOrBlank()) return null
        return when (product.uppercase()) {
            "SBAHN", "S-BAHN" -> "S"
            "REGIONAL", "REGIONALBAHN" -> "RB"
            "REGIONAL_EXPRESS", "REGIONALEXPRESS" -> "RE"
            "METRONOM" -> "ME"
            "ICE", "IC", "EC", "RJ", "TGV", "THALYS" -> product.uppercase()
            else -> product.take(6).trim()
        }
    }

    private fun labelFromJourneyId(journeyId: String?): String? {
        if (journeyId.isNullOrBlank()) return null
        val match = Regex("""#ZB#([^#]+)#""").find(journeyId) ?: return null
        return match.groupValues[1].trim()
            .replace(Regex("""\s+"""), " ")
            .takeIf { it.isNotEmpty() }
    }

    private fun firstUsable(vararg candidates: String?): String? =
        candidates.firstOrNull { !it.isNullOrBlank() }
}
