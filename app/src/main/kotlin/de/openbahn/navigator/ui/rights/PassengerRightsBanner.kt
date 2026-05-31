package de.openbahn.navigator.ui.rights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.navigator.R
import de.openbahn.rights.model.ExceptionEvent
import de.openbahn.rights.model.LegalDisclaimers
import de.openbahn.rights.model.PassengerRightsAssessment
import de.openbahn.rights.model.PassengerRightsDecisionState

@Composable
fun PassengerRightsBanner(
    assessment: PassengerRightsAssessment,
    onCreateClaimDraft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("passenger_rights_banner"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.passenger_rights_banner_title),
                style = MaterialTheme.typography.titleSmall,
            )
            assessment.entitlements.forEach { e ->
                Text(
                    "• ${e.summary}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            assessment.exceptions.forEach { ex ->
                ExceptionLine(ex)
            }
            if (assessment.monthlyLedgerSnapshot != null) {
                val ledger = assessment.monthlyLedgerSnapshot!!
                Text(
                    stringResource(
                        R.string.passenger_rights_dticket_ledger,
                        ledger.totalCompensationEuroCents / 100.0,
                        ledger.capEuroCents / 100.0,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                LegalDisclaimers.GENERAL,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(onClick = onCreateClaimDraft) {
                Text(stringResource(R.string.passenger_rights_create_draft))
            }
        }
    }
}

@Composable
private fun ExceptionLine(ex: ExceptionEvent) {
    val prefix = when (ex.state) {
        PassengerRightsDecisionState.TAXI_REIMBURSEMENT_POSSIBLE ->
            stringResource(R.string.passenger_rights_taxi_hint)
        PassengerRightsDecisionState.FERNVERKEHR_FALLBACK_POSSIBLE ->
            stringResource(R.string.passenger_rights_ice_hint)
        else -> "•"
    }
    Text(
        "$prefix ${ex.summary}",
        style = MaterialTheme.typography.bodySmall,
    )
}
