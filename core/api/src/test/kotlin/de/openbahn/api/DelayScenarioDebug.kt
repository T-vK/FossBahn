package de.openbahn.api

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import de.openbahn.model.delayMinutesFromTimes
import de.openbahn.model.maxDelayMinutes

/** Human-readable pipeline trace for delay verification scripts. */
internal object DelayScenarioDebug {
    fun banner(title: String) {
        println()
        println("══════════════════════════════════════════════════════════════")
        println("  $title")
        println("══════════════════════════════════════════════════════════════")
    }

    fun printJourney(stage: String, journey: Journey) {
        println()
        println("── $stage ──")
        println("  id=${journey.id.take(40)} refreshToken=${journey.refreshToken?.take(24)?.plus("…") ?: "—"}")
        println("  maxDelay=${journey.maxDelayMinutes()} min  remarks=${journey.remarks.take(2)}")
        journey.legs.forEachIndexed { i, leg -> printLeg(i, leg) }
    }

    private fun printLeg(index: Int, leg: Leg) {
        val line = leg.lineName ?: "?"
        println("  leg[$index] $line  tripId=${leg.tripId?.take(32) ?: "—"}")
        printStop("    dep", leg.origin)
        printStop("    arr", leg.destination)
    }

    private fun printStop(label: String, stop: StopEvent) {
        val sched = clock(stop.scheduledTime)
        val prog = stop.prognosedTime?.let { clock(it) } ?: "—"
        val delay = stop.delayMinutes
            ?: delayMinutesFromTimes(stop.scheduledTime, stop.prognosedTime)
            ?: 0
        val cancel = if (stop.cancelled) " CANCELLED" else ""
        println("$label ${stop.name}  $sched → $prog  delay=${delay}min$cancel  gl. ${stop.platform ?: "—"}")
    }

    fun clock(iso: String): String {
        val t = iso.indexOf('T')
        return if (t >= 0 && iso.length >= t + 6) iso.substring(t + 1, t + 6) else iso.takeLast(5)
    }

    fun findByLineAndDeparture(
        journeys: List<Journey>,
        linePattern: String,
        departureClock: String,
    ): Journey? = journeys.firstOrNull { journey ->
        journey.legs.any { leg ->
            val line = leg.lineName ?: return@any false
            line.contains(linePattern, ignoreCase = true) &&
                clock(leg.origin.scheduledTime) == departureClock
        }
    }
}
