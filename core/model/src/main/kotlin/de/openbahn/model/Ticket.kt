package de.openbahn.model

import kotlinx.serialization.Serializable

@Serializable
data class StoredTicket(
    val id: String,
    val title: String,
    val type: TicketType,
    val validFrom: String? = null,
    val validUntil: String? = null,
    val holderName: String? = null,
    val qrCodeData: String? = null,
    val photoUri: String? = null,
    val pdfUri: String? = null,
    val notes: String? = null,
    val importedAt: Long = System.currentTimeMillis(),
)

enum class TicketType {
    DEUTSCHLAND_TICKET,
    STANDARD,
    BAHNCARD,
    OTHER,
}
