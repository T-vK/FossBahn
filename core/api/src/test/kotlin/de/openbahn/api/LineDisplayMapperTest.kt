package de.openbahn.api

import de.openbahn.api.mapper.LineDisplayMapper
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LineDisplayMapperTest {
    @Test
    fun prefersMittelTextOverTrainNumberName() {
        val vm = buildJsonObject {
            put("name", "RE 19073")
            put("mittelText", "RE 1")
            put("kurzText", "RE")
            put("produktGattung", "REGIONAL_EXPRESS")
            put("linienNummer", "1")
        }
        val display = LineDisplayMapper.fromVerkehrsmittel(vm)
        assertEquals("RE 1", display.primary)
        assertEquals("RE 19073", display.detail)
    }

    @Test
    fun singleLabelWhenNameMatchesMittelText() {
        val vm = buildJsonObject {
            put("name", "ICE 523")
            put("mittelText", "ICE 523")
            put("kurzText", "ICE")
        }
        val display = LineDisplayMapper.fromVerkehrsmittel(vm)
        assertEquals("ICE 523", display.primary)
        assertNull(display.detail)
    }

    @Test
    fun composesFromKurzTextAndLineNumber() {
        val vm = buildJsonObject {
            put("kurzText", "S")
            put("linienNummer", "12")
            put("produktGattung", "SBAHN")
        }
        val display = LineDisplayMapper.fromVerkehrsmittel(vm)
        assertEquals("S 12", display.primary)
    }

    @Test
    fun journeyIdZbTokenCanSupplementMissingFields() {
        val vm = buildJsonObject {
            put("kurzText", "ICE")
        }
        val journeyId = "2|#ZB#ICE  523#PC#0#"
        val display = LineDisplayMapper.fromVerkehrsmittel(vm, journeyId)
        assertEquals("ICE 523", display.primary)
    }
}
