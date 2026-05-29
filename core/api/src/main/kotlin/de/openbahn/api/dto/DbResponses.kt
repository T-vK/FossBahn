package de.openbahn.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DbLocationResponse(
    val orte: List<DbOrt>? = null,
    val locations: List<DbOrt>? = null,
)

@Serializable
data class DbOrt(
    val id: String? = null,
    val name: String? = null,
    @SerialName("extId") val extId: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val coordinate: DbCoordinate? = null,
    val type: String? = null,
    val products: List<String>? = null,
)

@Serializable
data class DbCoordinate(
    val x: Double? = null,
    val y: Double? = null,
)

@Serializable
data class DbJourneyResponse(
    val verbindungen: List<DbVerbindung>? = null,
    val journeys: List<DbVerbindung>? = null,
    val pagingReference: String? = null,
    val status: String? = null,
    val code: String? = null,
)

@Serializable
data class DbVerbindung(
    val verbindungsId: String? = null,
    val tripId: String? = null,
    val id: String? = null,
    val reiseDauer: String? = null,
    val umstiege: Int? = null,
    @SerialName("umstiegsAnzahl") val umstiegsAnzahl: Int? = null,
    val abfahrtsZeit: String? = null,
    val ankunftsZeit: String? = null,
    @SerialName("verbindungsDauerInSeconds") val verbindungsDauerInSeconds: Int? = null,
    val ctxRecon: String? = null,
    val kontext: String? = null,
    @SerialName("verbindungsAbschnitte") val verbindungsAbschnitte: List<DbVerbindungsAbschnitt>? = null,
    val segmente: List<DbVerbindungsAbschnitt>? = null,
    val hinweise: List<String>? = null,
    val preis: DbPreis? = null,
    @SerialName("angebotsPreis") val angebotsPreis: DbPreis? = null,
    val dticketGueltig: Boolean? = null,
)

@Serializable
data class DbPreis(
    val betrag: Double? = null,
    val waehrung: String? = null,
    val text: String? = null,
    @SerialName("preis") val preis: Double? = null,
)

/** Leg / segment in a bahn.de journey (verbindungsAbschnitte). */
@Serializable
data class DbVerbindungsAbschnitt(
    val abfahrtsOrt: String? = null,
    @SerialName("abfahrtsOrtExtId") val abfahrtsOrtExtId: String? = null,
    @SerialName("abfahrtsZeitpunkt") val abfahrtsZeitpunkt: String? = null,
    val ankunftsOrt: String? = null,
    @SerialName("ankunftsOrtExtId") val ankunftsOrtExtId: String? = null,
    @SerialName("ankunftsZeitpunkt") val ankunftsZeitpunkt: String? = null,
    val gleis: String? = null,
    @SerialName("abschnittsDauer") val abschnittsDauer: Int? = null,
    val verkehrsmittel: DbVerkehrsmittel? = null,
    @SerialName("journeyId") val journeyId: String? = null,
)

@Serializable
data class DbHalteOrt(
    val name: String? = null,
    val id: String? = null,
    val gleis: String? = null,
    val abfahrtsZeit: String? = null,
    val ankunftsZeit: String? = null,
    val prognoseZeit: String? = null,
    val verspaetung: Int? = null,
    val ausfall: Boolean? = null,
    val hinweise: List<String>? = null,
)

@Serializable
data class DbVerkehrsmittel(
    val name: String? = null,
    val kurzText: String? = null,
    @SerialName("produktGattung") val produktGattung: String? = null,
    val kategorie: String? = null,
    @SerialName("linienNummer") val linienNummer: String? = null,
    val nummer: String? = null,
    val richtung: String? = null,
    val betreiber: String? = null,
    val fahrradErlaubt: Boolean? = null,
    val auslastung: String? = null,
)

@Serializable
data class DbStationBoardResponse(
    val entries: List<DbBoardEntry>? = null,
    val abfahrten: List<DbBoardEntry>? = null,
    val ankuenfte: List<DbBoardEntry>? = null,
)

@Serializable
data class DbBoardEntry(
    val zeit: String? = null,
    @SerialName("ezZeit") val ezZeit: String? = null,
    val gleis: String? = null,
    val ueber: List<String>? = null,
    val richtung: String? = null,
    val verkehrsmittel: DbVerkehrsmittel? = null,
    val verkehrmittel: DbVerkehrsmittel? = null,
    val abfahrtsZeit: String? = null,
    val ankunftsZeit: String? = null,
    val prognoseZeit: String? = null,
    val verspaetung: Int? = null,
    val ausfall: Boolean? = null,
    @SerialName("journeyId") val journeyId: String? = null,
    val fahrtId: String? = null,
    val hinweise: List<String>? = null,
    val meldungen: List<String>? = null,
)
