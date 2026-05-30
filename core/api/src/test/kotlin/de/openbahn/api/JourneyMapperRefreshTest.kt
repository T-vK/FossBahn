package de.openbahn.api

import de.openbahn.api.mapper.JourneyMapper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class JourneyMapperRefreshTest {
    @Test
    fun mapRefresh_returnsJourneyWithDelays() {
        val text = javaClass.getResource("/dbweb-journey-refresh.json")!!.readText()
        val root = Json.parseToJsonElement(text).jsonObject
        val journey = JourneyMapper.mapRefresh(root)
        assertNotNull(journey)
        assertEquals(12, journey!!.legs.single().origin.delayMinutes)
    }
}
