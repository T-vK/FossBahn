package de.openbahn.api

/** Thrown when Deutsche Bahn returns OPS_BLOCKED (common from datacenter IPs). */
class DbApiBlockedException(message: String) : Exception(message)
