package de.openbahn.navigator.ui.search

import de.openbahn.model.Location
import java.util.UUID

data class ViaStopField(
    val id: String = UUID.randomUUID().toString(),
    val query: String = "",
    val location: Location? = null,
)
