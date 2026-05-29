package de.openbahn.navigator.ui.tickets

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.openbahn.model.StoredTicket
import de.openbahn.model.TicketType
import de.openbahn.navigator.data.TicketRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TicketsViewModel(private val repository: TicketRepository) : ViewModel() {
    val tickets = repository.observeTickets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun importTicket(uri: Uri, type: TicketType = TicketType.OTHER) {
        viewModelScope.launch { repository.importFromUri(uri, type) }
    }

    fun importDeutschlandTicket(holderName: String?, photoUri: String?, validUntil: String?) {
        viewModelScope.launch {
            repository.saveDeutschlandTicket(holderName, photoUri, validUntil)
        }
    }

    fun deleteTicket(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}
