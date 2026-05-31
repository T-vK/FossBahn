package de.openbahn.rights.claims

import de.openbahn.rights.model.ClaimDraft
import de.openbahn.rights.model.ClaimDraftStatus
import de.openbahn.rights.model.LegalDisclaimers
import de.openbahn.rights.model.PassengerRightsAssessment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

object ClaimDraftBuilder {
    private val json = Json { ignoreUnknownKeys = true }

    fun createDraft(
        assessment: PassengerRightsAssessment,
        operatorEmail: String? = null,
    ): ClaimDraft {
        val id = UUID.randomUUID().toString()
        val subject = "Fahrgastrechte — ${assessment.plannedTrip.fromName} → ${assessment.plannedTrip.toName}"
        val body = buildBody(assessment)
        return ClaimDraft(
            id = id,
            journeyId = assessment.plannedTrip.journeyId,
            createdAtEpochMillis = assessment.evaluatedAtEpochMillis,
            status = ClaimDraftStatus.DRAFT,
            assessmentJson = json.encodeToString(assessment),
            subject = subject,
            bodyText = body,
            recipientEmail = operatorEmail,
        )
    }

    private fun buildBody(assessment: PassengerRightsAssessment): String = buildString {
        appendLine(subjectLine(assessment))
        appendLine()
        appendLine("Geplante Reise:")
        appendLine("  Abfahrt: ${assessment.plannedTrip.departureIso}")
        appendLine("  Ankunft: ${assessment.plannedTrip.arrivalIso}")
        appendLine("  Verspätung am Ziel: ${assessment.delayEvent.arrivalDelayMinutes} Min.")
        appendLine()
        if (assessment.entitlements.isNotEmpty()) {
            appendLine("Mögliche Standardentschädigung:")
            assessment.entitlements.forEach { e ->
                val amount = e.amountEuroCents?.let { "€%.2f".format(it / 100.0) } ?: "siehe Fahrpreis"
                appendLine("  • ${e.summary} ($amount) — ${e.legalBasis}")
            }
            appendLine()
        }
        if (assessment.exceptions.isNotEmpty()) {
            appendLine("Ausnahme / Aufwendungsersatz (Prüfung im Einzelfall):")
            assessment.exceptions.forEach { ex ->
                appendLine("  • ${ex.summary}")
                appendLine("    ${ex.disclaimer}")
            }
            appendLine()
        }
        appendLine(LegalDisclaimers.GENERAL)
        appendLine()
        appendLine("—")
        appendLine("Entwurf erstellt mit OpenBahn Navigator. Bitte vor Versand prüfen und Belege anfügen.")
    }

    private fun subjectLine(assessment: PassengerRightsAssessment): String =
        "Antrag Fahrgastrechte — ${assessment.plannedTrip.fromName} → ${assessment.plannedTrip.toName} " +
            "(${assessment.plannedTrip.journeyId.take(32)})"
}
