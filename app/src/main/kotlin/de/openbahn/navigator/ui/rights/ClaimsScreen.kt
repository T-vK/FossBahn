package de.openbahn.navigator.ui.rights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.navigator.R
import de.openbahn.navigator.domain.PassengerRightsRepository
import de.openbahn.navigator.ui.util.shareClaimDraftEmail
import de.openbahn.rights.model.ClaimDraft
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaimsScreen(
    onBack: () -> Unit,
    repository: PassengerRightsRepository = koinInject(),
) {
    val drafts by repository.observeClaimDrafts().collectAsState(initial = emptyList())
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.passenger_rights_claims_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
            )
        },
    ) { padding ->
        if (drafts.isEmpty()) {
            Text(
                stringResource(R.string.passenger_rights_claims_empty),
                modifier = Modifier.padding(padding).padding(16.dp),
            )
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(drafts, key = { it.id }) { draft ->
                    ClaimDraftRow(
                        draft = draft,
                        onSend = { context.shareClaimDraftEmail(draft) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ClaimDraftRow(draft: ClaimDraft, onSend: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSend)
            .padding(12.dp),
    ) {
        Text(draft.subject, style = MaterialTheme.typography.titleSmall)
        Text(
            draft.bodyText.lineSequence().firstOrNull().orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
        )
        Text(
            stringResource(R.string.passenger_rights_tap_to_send),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
