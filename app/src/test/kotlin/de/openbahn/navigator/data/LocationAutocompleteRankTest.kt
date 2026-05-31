package de.openbahn.navigator.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocationAutocompleteRankTest {
    @Test
    fun prefixMatch_ranksBeforeContains() {
        assertTrue("Hamburg Hbf".autocompleteMatchRank("Ham") < "Berlin Hbf".autocompleteMatchRank("Ham"))
        assertTrue("Hamburg Hbf".matchesAutocompleteQuery("Ham"))
        assertFalse("Berlin Hbf".matchesAutocompleteQuery("Ham"))
    }

    @Test
    fun wordPrefixMatch_ranksBeforeSubstring() {
        assertTrue(
            "Frankfurt (Main) Hbf".autocompleteMatchRank("Main") <
                "Hamburg Hbf".autocompleteMatchRank("Main"),
        )
    }

    @Test
    fun emptyQuery_matchesAll() {
        assertEquals(0, "Berlin Hbf".autocompleteMatchRank(""))
        assertTrue("Berlin Hbf".matchesAutocompleteQuery(""))
    }
}
