package de.openbahn.model

fun Journey.railLegs(): List<Leg> = legs.filter { !it.isWalking }

/** Public transport transfers (walking sections excluded). */
fun Journey.railTransferCount(): Int = (railLegs().size - 1).coerceAtLeast(0)
