package de.openbahn.api.mapper

import de.openbahn.api.dto.DbBoardItem
import de.openbahn.api.dto.DbStationBoardResponse
import de.openbahn.model.BoardEntry
import de.openbahn.model.TransportProduct

internal object StationBoardMapper {
    fun mapDepartures(response: DbStationBoardResponse): List<BoardEntry> =
        response.abfahrten.orEmpty().mapNotNull { mapItem(it, isDeparture = true) }

    fun mapArrivals(response: DbStationBoardResponse): List<BoardEntry> =
        response.ankuenfte.orEmpty().mapNotNull { mapItem(it, isDeparture = false) }

    private fun mapItem(item: DbBoardItem, isDeparture: Boolean): BoardEntry? {
        val vm = item.verkehrsmittel ?: return null
        val time = if (isDeparture) item.abfahrtsZeit else item.ankunftsZeit
        if (time == null) return null
        return BoardEntry(
            line = vm.kurzText ?: vm.name.orEmpty(),
            direction = item.richtung.orEmpty(),
            scheduledTime = time,
            prognosedTime = item.prognoseZeit,
            platform = item.gleis,
            delayMinutes = item.verspaetung,
            cancelled = item.ausfall == true,
            product = vm.produktGattung?.let { code ->
                TransportProduct.entries.find { p -> p.vendoCode.equals(code, true) }
            },
            tripId = item.fahrtId,
            remarks = item.hinweise.orEmpty(),
        )
    }
}
