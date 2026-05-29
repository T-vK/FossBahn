package de.openbahn.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
    val coordinate: DbCoordinate? = null,
    val type: String? = null,
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
)

@Serializable
data class DbVerbindung(
    val verbindungsId: String? = null,
    val id: String? = null,
    val reiseDauer: String? = null,
    val umstiege: Int? = null,
    val abfahrtsZeit: String? = null,
    val ankunftsZeit: String? = null,
    val ctxRecon: String? = null,
    val segmente: List<DbSegment>? = null,
    val hinweise: List<String>? = null,
    val preis: DbPreis? = null,
    val dticketGueltig: Boolean? = null,
)

@Serializable
data class DbPreis(
    val betrag: Double? = null,
    val waehrung: String? = null,
    val text: String? = null,
)

@Serializable
data class DbSegment(
    val abfahrtsOrt: DbHalteOrt? = null,
    val ankunftsOrt: DbHalteOrt? = null,
    val verkehrsmittel: DbVerkehrsmittel? = null,
    val fahrtId: String? = null,
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
    val produktGattung: String? = null,
    val betreiber: String? = null,
    val fahrradErlaubt: Boolean? = null,
    val auslastung: String? = null,
)

@Serializable
data class DbStationBoardResponse(
    val abfahrten: List<DbBoardItem>? = null,
    val ankuenfte: List<DbBoardItem>? = null,
)

@Serializable
data class DbBoardItem(
    val verkehrsmittel: DbVerkehrsmittel? = null,
    val richtung: String? = null,
    val abfahrtsZeit: String? = null,
    val ankunftsZeit: String? = null,
    val prognoseZeit: String? = null,
    val gleis: String? = null,
    val verspaetung: Int? = null,
    val ausfall: Boolean? = null,
    val fahrtId: String? = null,
    val hinweise: List<String>? = null,
)
