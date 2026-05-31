package de.openbahn.api.mapper

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** User-facing line label (e.g. RE 1, S 12) and optional technical detail (e.g. train number). */
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

        val short = firstUsable(
            mittel?.takeIf { isPublicLineLabel(it) },
            lang?.takeIf { isPublicLineLabel(it) },
            composedLineLabel(kurz, product, lineNumber),
            name?.takeIf { isPublicLineLabel(it) },
            zb?.takeIf { isPublicLineLabel(it) },
            kurz?.takeIf { isPublicLineLabel(it) },
        )

        val detail = firstUsable(
            name?.takeIf { isUsefulDetail(it, short) },
            zb?.takeIf { isUsefulDetail(it, short) },
            lang?.takeIf { isUsefulDetail(it, short) },
            composedLineLabel(kurz, product, lineNumber)?.takeIf { isUsefulDetail(it, short) },
        )

        val primary = short ?: detail
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

    private fun isPublicLineLabel(value: String): Boolean {
        val t = value.trim()
        if (t.isEmpty() || t.length > 48) return false
        if (t.contains('#') || t.contains('|')) return false
        if (t.contains('.') && t.split(' ').size > 4) return false // sentence-like langText
        return true
    }

    private fun isCrypticLabel(value: String): Boolean {
        val t = value.trim()
        if (t.isEmpty()) return true
        if (t.contains('#') || t.contains('|')) return true
        if (Regex("""^\d{5,}$""").matches(t)) return true
        if (Regex("""^Line \d+$""", RegexOption.IGNORE_CASE).matches(t)) return true
        return false
    }

    private fun isUsefulDetail(candidate: String, short: String?): Boolean {
        if (!isPublicLineLabel(candidate) || isCrypticLabel(candidate)) return false
        if (short == null) return true
        if (labelsEquivalent(candidate, short)) return false
        return true
    }

    private fun labelsEquivalent(a: String, b: String): Boolean =
        normalizeLabel(a) == normalizeLabel(b)

    private fun normalizeLabel(label: String): String =
        label.trim()
            .replace(Regex("""\s+"""), " ")
            .uppercase()

    private fun composedLineLabel(kurz: String?, product: String?, lineNumber: String?): String? {
        val num = lineNumber?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val prefix = kurz?.trim()?.takeIf { it.isNotEmpty() }
            ?: productCodeLabel(product)
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
