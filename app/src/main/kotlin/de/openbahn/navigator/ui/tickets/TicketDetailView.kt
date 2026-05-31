package de.openbahn.navigator.ui.tickets

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ImageBitmap
import coil.compose.AsyncImage
import de.openbahn.model.StoredTicket
import de.openbahn.navigator.R

@Composable
fun TicketDetailView(
    ticket: StoredTicket,
    modifier: Modifier = Modifier,
    fillScreen: Boolean = false,
) {
    if (fillScreen) {
        Column(modifier.fillMaxSize()) {
            TicketPreviewContent(ticket = ticket, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    } else {
        Column(modifier.fillMaxWidth()) {
            Text(
                ticket.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            ticket.holderName?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            TicketPreviewContent(
                ticket = ticket,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
            )
        }
    }
}

@Composable
private fun TicketPreviewContent(ticket: StoredTicket, modifier: Modifier) {
    Box(modifier) {
            when {
                !ticket.pdfUri.isNullOrBlank() -> PdfTicketPages(pdfUri = ticket.pdfUri!!)
                !ticket.photoUri.isNullOrBlank() -> ImageTicketPage(photoUri = ticket.photoUri!!)
                else -> Text(
                    stringResource(R.string.ticket_no_preview),
                    Modifier.align(Alignment.Center),
                )
            }
    }
}

@Composable
private fun ImageTicketPage(photoUri: String) {
    ZoomableTicketContent {
        AsyncImage(
            model = photoUri,
            contentDescription = stringResource(R.string.dticket_photo),
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun PdfTicketPages(pdfUri: String) {
    val context = LocalContext.current
    var pages by remember(pdfUri) { mutableStateOf<List<ImageBitmap>?>(null) }
    var error by remember(pdfUri) { mutableStateOf(false) }

    LaunchedEffect(pdfUri) {
        pages = null
        error = false
        val rendered = renderPdfPages(context, pdfUri)
        if (rendered.isEmpty()) {
            error = true
        } else {
            pages = rendered
        }
    }

    when {
        pages == null && !error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.ticket_pdf_error))
            }
        }
        else -> {
            val renderedPages = pages!!
            if (renderedPages.size == 1) {
                ZoomableTicketContent(Modifier.fillMaxSize()) {
                    Image(
                        bitmap = renderedPages.single(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            } else {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    renderedPages.forEach { page ->
                        ZoomableTicketContent(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 280.dp),
                        ) {
                            Image(
                                bitmap = page,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth,
                            )
                        }
                    }
                }
            }
        }
    }
}
