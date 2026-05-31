package de.openbahn.navigator.data

import android.content.Context
import android.net.Uri
import de.openbahn.model.StoredTicket
import de.openbahn.model.TicketType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class TicketRepository(
    private val dao: TicketDao,
    private val context: Context,
) {
    fun observeTickets(): Flow<List<StoredTicket>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun importFromUri(uri: Uri, type: TicketType = TicketType.OTHER): StoredTicket =
        withContext(Dispatchers.IO) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            val id = UUID.randomUUID().toString()
            val persisted = persistUri(uri, id, mime)
            val ticket = StoredTicket(
                id = id,
                title = when (type) {
                    TicketType.DEUTSCHLAND_TICKET -> "Deutschland-Ticket"
                    else -> persisted.fileName ?: "Ticket"
                },
                type = type,
                photoUri = if (mime.startsWith("image/")) persisted.uri else null,
                pdfUri = if (mime == "application/pdf") persisted.uri else null,
            )
            dao.upsert(ticket.toEntity())
            ticket
        }

    suspend fun saveDeutschlandTicket(
        holderName: String?,
        photoUri: Uri?,
        validUntil: String?,
    ): StoredTicket = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val persistedPhoto = photoUri?.let { uri ->
            persistUri(uri, id, context.contentResolver.getType(uri).orEmpty().ifBlank { "image/*" })
        }
        val ticket = StoredTicket(
            id = id,
            title = "Deutschland-Ticket",
            type = TicketType.DEUTSCHLAND_TICKET,
            holderName = holderName,
            photoUri = persistedPhoto?.uri,
            validUntil = validUntil,
        )
        dao.upsert(ticket.toEntity())
        ticket
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.getById(id)?.let { entity ->
            deleteStoredFile(entity.pdfUri)
            deleteStoredFile(entity.photoUri)
        }
        dao.delete(id)
    }

    private fun deleteStoredFile(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        runCatching {
            val file = when {
                uriString.startsWith("file:") -> java.io.File(Uri.parse(uriString).path ?: return)
                else -> null
            }
            file?.delete()
        }
    }

    private data class PersistedFile(val uri: String, val fileName: String?)

    private fun persistUri(source: Uri, id: String, mime: String): PersistedFile {
        val ext = when {
            mime.contains("pdf") -> "pdf"
            mime.contains("png") -> "png"
            else -> "jpg"
        }
        val dir = context.getDir("tickets", Context.MODE_PRIVATE)
        val file = java.io.File(dir, "$id.$ext")
        context.contentResolver.openInputStream(source)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return PersistedFile(file.toURI().toString(), file.name)
    }
}
