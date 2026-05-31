package de.openbahn.api.debug

import de.openbahn.api.haltIdForJourney
import de.openbahn.model.Location
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Summaries for bahn.de fahrplan / location responses (log-safe, no full bodies). */
object FahrplanDiagnostics {
    fun describeLocation(location: Location): String =
        buildString {
            append(location.name)
            location.evaNumber?.let { append(" eva=").append(it) }
            append(" haltId=").append(location.haltIdForJourney().ellipsize(72))
        }

    private fun String.ellipsize(max: Int): String =
        if (length <= max) this else take(max - 1) + "…"

    fun summarizeFahrplanBody(raw: String): String {
        if (raw.contains("OPS_BLOCKED")) return "response=OPS_BLOCKED"
        return try {
            val root = Json.parseToJsonElement(raw).jsonObject
            summarizeFahrplanRoot(root) + " bodyBytes=${raw.length}"
        } catch (_: Exception) {
            "response=non-json bodyBytes=${raw.length}"
        }
    }

    /** True when HTTP 200 but body is `{}` or has no connection arrays (common for bad halt ids / past time). */
    fun isEmptySuccessBody(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed == "{}" || trimmed.isEmpty()) return true
        return try {
            val root = Json.parseToJsonElement(trimmed).jsonObject
            val top = root["verbindungen"]?.jsonArray?.size ?: 0
            val inIntervals = root["intervalle"]?.jsonArray.orEmpty().sumOf {
                it.jsonObject["verbindungen"]?.jsonArray?.size ?: 0
            } + root["tagesbestPreisIntervalle"]?.jsonArray.orEmpty().sumOf {
                it.jsonObject["verbindungen"]?.jsonArray?.size ?: 0
            }
            top == 0 && inIntervals == 0 && !trimmed.contains("OPS_BLOCKED")
        } catch (_: Exception) {
            false
        }
    }

    fun summarizeFahrplanRoot(root: JsonObject): String {
        val status = root["status"]?.jsonPrimitive?.content
        val code = root["code"]?.jsonPrimitive?.content
        val top = root["verbindungen"]?.jsonArray?.size ?: 0
        val intervals = root["intervalle"]?.jsonArray?.size ?: 0
        val tages = root["tagesbestPreisIntervalle"]?.jsonArray?.size ?: 0
        val inIntervals = root["intervalle"]?.jsonArray.orEmpty().sumOf {
            it.jsonObject["verbindungen"]?.jsonArray?.size ?: 0
        } + root["tagesbestPreisIntervalle"]?.jsonArray.orEmpty().sumOf {
            it.jsonObject["verbindungen"]?.jsonArray?.size ?: 0
        }
        return buildString {
            append("status=").append(status ?: "ok")
            if (code != null) append(" code=").append(code)
            append(" verbindungen=").append(top)
            append(" intervalle=").append(intervals)
            append(" tagesbestPreisIntervalle=").append(tages)
            append(" connectionsInIntervals=").append(inIntervals)
        }
    }
}
