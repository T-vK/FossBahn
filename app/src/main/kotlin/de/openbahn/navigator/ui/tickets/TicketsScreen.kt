package de.openbahn.navigator.ui.tickets

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.model.StoredTicket
import de.openbahn.model.TicketType
import de.openbahn.navigator.R
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketsScreen(
    onOpenDrawer: () -> Unit,
    viewModel: TicketsViewModel = koinViewModel(),
) {
    TicketTabBrightness()

    val tickets by viewModel.tickets.collectAsState()
    val hasDeutschlandTicket = tickets.any { it.type == TicketType.DEUTSCHLAND_TICKET }

    var showInfoDialog by remember { mutableStateOf(false) }
    var ticketToDelete by remember { mutableStateOf<StoredTicket?>(null) }
    var selectedTicket by remember { mutableStateOf<StoredTicket?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.importTicket(it, TicketType.OTHER) }
    }
    val dticketPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            viewModel.importDeutschlandTicket(holderName = null, photoUri = it, validUntil = null)
        }
    }

    ticketToDelete?.let { ticket ->
        AlertDialog(
            onDismissRequest = { ticketToDelete = null },
            title = { Text(stringResource(R.string.delete_ticket_title)) },
            text = { Text(stringResource(R.string.delete_ticket_message, ticket.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTicket(ticket.id)
                        if (selectedTicket?.id == ticket.id) selectedTicket = null
                        ticketToDelete = null
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { ticketToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(stringResource(R.string.tickets_info_title)) },
            text = { Text(stringResource(R.string.dticket_import_hint)) },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }

    selectedTicket?.let { ticket ->
        AlertDialog(
            onDismissRequest = { selectedTicket = null },
            title = { Text(ticket.title) },
            text = {
                Box(Modifier.heightIn(min = 280.dp, max = 520.dp).fillMaxWidth()) {
                    TicketDetailView(ticket = ticket, fillScreen = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTicket = null }) {
                    Text(stringResource(R.string.close))
                }
            },
            dismissButton = {
                TextButton(onClick = { ticketToDelete = ticket }) {
                    Text(stringResource(R.string.delete))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tickets_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu_open))
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.tickets_info_title),
                        )
                    }
                    if (tickets.size == 1) {
                        IconButton(onClick = { ticketToDelete = tickets.first() }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_ticket_title),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { importLauncher.launch(arrayOf("application/pdf", "image/*")) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.import_ticket))
            }
        },
    ) { padding ->
        when {
            tickets.isEmpty() -> {
                EmptyTicketsContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    showDeutschlandImport = !hasDeutschlandTicket,
                    onImportDeutschland = { dticketPhotoLauncher.launch("image/*") },
                )
            }
            tickets.size == 1 -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    TicketDetailView(
                        ticket = tickets.first(),
                        modifier = Modifier.fillMaxSize(),
                        fillScreen = true,
                    )
                }
            }
            else -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                ) {
                    if (!hasDeutschlandTicket) {
                        OutlinedButton(
                            onClick = { dticketPhotoLauncher.launch("image/*") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.ConfirmationNumber,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(stringResource(R.string.import_dticket_photo))
                        }
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(tickets, key = { it.id }) { ticket ->
                            TicketListCard(
                                ticket = ticket,
                                onOpen = { selectedTicket = ticket },
                                onDelete = { ticketToDelete = ticket },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTicketsContent(
    modifier: Modifier,
    showDeutschlandImport: Boolean,
    onImportDeutschland: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.ConfirmationNumber,
            contentDescription = null,
            modifier = Modifier.padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.tickets_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showDeutschlandImport) {
            OutlinedButton(
                onClick = onImportDeutschland,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) {
                Text(stringResource(R.string.import_dticket_photo))
            }
        }
    }
}

@Composable
private fun TicketListCard(
    ticket: StoredTicket,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(ticket.title, style = MaterialTheme.typography.titleMedium)
                ticket.holderName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                val kind = when {
                    ticket.pdfUri != null -> stringResource(R.string.ticket_type_pdf)
                    ticket.photoUri != null -> stringResource(R.string.ticket_type_image)
                    else -> null
                }
                kind?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_ticket_title),
                )
            }
        }
    }
}
