package de.openbahn.api

class DbApiException(val errorCode: String) : Exception("Deutsche Bahn API error: $errorCode")
