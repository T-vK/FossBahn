package de.openbahn.api.mapper

import de.openbahn.api.dto.DbJourneyResponse
import de.openbahn.api.dto.DbVerbindung
import de.openbahn.api.dto.DbVerbindungsAbschnitt
import de.openbahn.api.dto.DbVerkehrsmittel
import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import de.openbahn.model.TransportProduct
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

internal object JourneyMapper {
    fun mapJourneys(response: DbJourneyResponse): List<Journey> {
        if (response.status == "ERROR") return emptyList()
        val items = response.verbindungen.orEmpty() + response.journeys.orEmpty()
        return items.mapNotNull(::mapVerbindung)
    }

    fun mapRefresh(response: JsonObject): Journey? = JourneyResponseParser.parseRefresh(response)

    private fun mapVerbindung(v: DbVerbindung): Journey? {
        val abschnitte = v.verbindungsAbschnitte.orEmpty().ifEmpty { v.segmente.orEmpty() }
        val legs = abschnitte.mapNotNull(::mapAbschnitt)
        if (legs.isEmpty()) return null
        val duration = v.verbindungsDauerInSeconds?.div(60)
            ?: parseDurationMinutes(v.reiseDauer)
            ?: estimateDuration(legs)
        val firstDep = legs.first().origin.scheduledTime
        val lastArr = legs.last().destination.scheduledTime
        val price = v.preis ?: v.angebotsPreis
        return Journey(
            id = v.verbindungsId ?: v.tripId ?: v.id ?: UUID.randomUUID().toString(),
            legs = legs,
            durationMinutes = duration,
            transfers = v.umstiegsAnzahl ?: v.umstiege ?: (legs.size - 1).coerceAtLeast(0),
            departure = v.abfahrtsZeit ?: firstDep,
            arrival = v.ankunftsZeit ?: lastArr,
            priceHint = price?.text ?: price?.preis?.let { "${it}€" } ?: price?.betrag?.let { "$it ${price.waehrung}" },
            refreshToken = v.ctxRecon ?: v.kontext,
            deutschlandTicketValid = v.dticketGueltig,
            remarks = mapHinweise(v.hinweise),
        )
    }

    private fun mapHinweise(hinweise: List<JsonElement>?): List<String> =
        hinweise.orEmpty().mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNull
                else -> element.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                    ?: element.jsonObject["kurzText"]?.jsonPrimitive?.contentOrNull
            }
        }

    private fun mapAbschnitt(a: DbVerbindungsAbschnitt): Leg? {
        val depName = a.abfahrtsOrt ?: return null
        val arrName = a.ankunftsOrt ?: return null
        val depTime = a.abfahrtsZeitpunkt ?: return null
        val arrTime = a.ankunftsZeitpunkt ?: return null
        val vm = a.verkehrsmittel
        return Leg(
            origin = StopEvent(
                name = depName,
                id = a.abfahrtsOrtExtId,
                platform = a.gleis,
                scheduledTime = depTime,
            ),
            destination = StopEvent(
                name = arrName,
                id = a.ankunftsOrtExtId,
                scheduledTime = arrTime,
            ),
            lineName = lineLabel(vm),
            product = vm?.produktGattung?.let(::mapProduct),
            operator = vm?.betreiber,
            loadFactor = vm?.auslastung,
            bikeAllowed = vm?.fahrradErlaubt,
            tripId = a.journeyId,
        )
    }

    private fun lineLabel(vm: DbVerkehrsmittel?): String? =
        vm?.name ?: vm?.kurzText ?: vm?.linienNummer?.let { "Line $it" }

    private fun mapProduct(code: String): TransportProduct? =
        TransportProduct.entries.find { it.vendoCode.equals(code, ignoreCase = true) }

    private fun parseDurationMinutes(isoDuration: String?): Int? {
        if (isoDuration == null) return null
        val match = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?").find(isoDuration) ?: return null
        val hours = match.groupValues[1].toIntOrNull() ?: 0
        val minutes = match.groupValues[2].toIntOrNull() ?: 0
        return hours * 60 + minutes
    }

    private fun estimateDuration(legs: List<Leg>): Int = 60
}
