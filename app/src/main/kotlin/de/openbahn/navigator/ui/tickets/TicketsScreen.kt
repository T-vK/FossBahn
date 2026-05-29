package de.openbahn.navigator.ui.tickets

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.openbahn.model.TicketType
import de.openbahn.navigator.R
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketsScreen(viewModel: TicketsViewModel = koinViewModel()) {
    val tickets by viewModel.tickets.collectAsState()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.importTicket(it, TicketType.OTHER) }
    }
    val dticketPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            viewModel.importDeutschlandTicket(
                holderName = null,
                photoUri = it.toString(),
                validUntil = null,
            )
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tickets_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { importLauncher.launch(arrayOf("application/pdf", "image/*")) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.import_ticket))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(stringResource(R.string.dticket_import_hint), Modifier.padding(bottom = 12.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = { dticketPhotoLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                Text(stringResource(R.string.import_dticket_photo))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tickets, key = { it.id }) { ticket ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(ticket.title)
                            ticket.holderName?.let { Text(it) }
                            ticket.photoUri?.let { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = stringResource(R.string.dticket_photo),
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
