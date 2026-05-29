package de.openbahn.api

/** Journey/location response could not be parsed (schema drift from bahn.de API). */
class DbParseException(message: String = "Failed to parse Deutsche Bahn response", cause: Throwable? = null) :
    Exception(message, cause)
