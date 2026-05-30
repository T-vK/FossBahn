package de.openbahn.api

import de.openbahn.api.dto.DbBoardEntry
import de.openbahn.api.dto.DbStationBoardResponse
import de.openbahn.api.dto.DbVerkehrsmittel
import de.openbahn.api.mapper.StationBoardMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StationBoardMapperDelayTest {
    @Test
    fun mapDepartures_computesDelayFromEzZeit() {
        val response = DbStationBoardResponse(
            abfahrten = listOf(
                DbBoardEntry(
                    zeit = "2025-02-08T15:31:00",
                    ezZeit = "2025-02-08T16:05:00",
                    verkehrsmittel = DbVerkehrsmittel(name = "RE 19073", produktGattung = "RE"),
                    richtung = "Stuttgart Hbf",
                ),
            ),
        )
        val entry = StationBoardMapper.mapDepartures(response).single()
        assertEquals("2025-02-08T16:05:00", entry.prognosedTime)
        assertEquals(34, entry.delayMinutes)
    }
}
