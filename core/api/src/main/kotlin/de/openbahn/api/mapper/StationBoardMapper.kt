package de.openbahn.api.mapper

import de.openbahn.api.dto.DbBoardEntry
import de.openbahn.api.dto.DbStationBoardResponse
import de.openbahn.api.dto.DbVerkehrsmittel
import de.openbahn.model.BoardEntry
import de.openbahn.model.TransportProduct

internal object StationBoardMapper {
    fun mapDepartures(response: DbStationBoardResponse): List<BoardEntry> {
        val items = response.entries.orEmpty() + response.abfahrten.orEmpty()
        return items.mapNotNull { mapItem(it, isDeparture = true) }
    }

    fun mapArrivals(response: DbStationBoardResponse): List<BoardEntry> {
        val items = response.entries.orEmpty() + response.ankuenfte.orEmpty()
        return items.mapNotNull { mapItem(it, isDeparture = false) }
    }

    private fun mapItem(item: DbBoardEntry, isDeparture: Boolean): BoardEntry? {
        val vm = item.verkehrsmittel ?: item.verkehrmittel ?: return null
        val time: String = item.zeit
            ?: (if (isDeparture) item.abfahrtsZeit else item.ankunftsZeit)
            ?: return null
        val direction = item.richtung
            ?: item.ueber?.lastOrNull()
            ?: item.ueber?.joinToString(" – ")
            ?: ""
        return BoardEntry(
            line = lineLabel(vm),
            direction = direction,
            scheduledTime = time,
            prognosedTime = item.ezZeit ?: item.prognoseZeit,
            platform = item.gleis,
            delayMinutes = item.verspaetung,
            cancelled = item.ausfall == true,
            product = vm.produktGattung?.let { code ->
                TransportProduct.entries.find { p -> p.vendoCode.equals(code, true) }
            },
            tripId = item.journeyId ?: item.fahrtId,
            remarks = item.hinweise.orEmpty() + item.meldungen.orEmpty(),
        )
    }

    private fun lineLabel(vm: DbVerkehrsmittel): String =
        vm.name ?: vm.kurzText ?: vm.linienNummer ?: "?"
}
