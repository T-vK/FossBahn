package de.openbahn.rights.model

/** Central legal wording — UI must surface these for exception paths. */
object LegalDisclaimers {
    const val GENERAL =
        "Hinweise ersetzen keine Rechtsberatung. Ansprüche hängen vom Einzelfall ab " +
            "(EU-VO 2021/782, EVO, BGB)."

    const val TAXI =
        "Taxikosten können als Aufwendungsersatz erstattungsfähig sein, wenn sie zur " +
            "Schadensminderung erforderlich waren (u. a. letzte Verbindung, kein zumutbarer " +
            "ÖPNV, Ziel sonst nicht erreichbar). Kein automatischer Anspruch."

    const val FERNVERKEHR =
        "Fernverkehr (ICE/IC) ist keine Deutschlandticket-Leistung. Eine Erstattung kann " +
            "nur in Ausnahmefällen über allgemeine Schadensminderung prüfbar sein — " +
            "typischerweise nicht ohne Zusatzticket."

    const val DEUTSCHLAND_TICKET =
        "Deutschlandticket: Pauschalentschädigung nach Nahverkehrsregeln (60/120 Min.), " +
            "monatlich gedeckelt. Kein Fernverkehrs-Upgrade."
}
