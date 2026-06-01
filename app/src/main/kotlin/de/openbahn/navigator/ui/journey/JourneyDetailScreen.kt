package de.openbahn.navigator.ui.journey

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import de.openbahn.navigator.ui.util.ShareJourneyIconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.api.BahnVorhersageClient
import de.openbahn.api.JourneyRatingOptions
import de.openbahn.api.loadTripRoutesForJourneys
import de.openbahn.model.Journey
import de.openbahn.navigator.R
import de.openbahn.navigator.data.UserPreferencesRepository
import de.openbahn.navigator.navigation.JourneyDetailPayload
import de.openbahn.navigator.tracking.TrackedJourneyRefreshUseCase
import de.openbahn.model.applyPassengerRightsSimulation
import de.openbahn.navigator.BuildConfig
import de.openbahn.navigator.data.PassengerRightsSimulationRepository
import de.openbahn.navigator.domain.JourneySearchRepository
import de.openbahn.navigator.domain.PassengerRightsRepository
import de.openbahn.navigator.ui.components.JourneyCard
import de.openbahn.navigator.ui.rights.PassengerRightsBanner
import de.openbahn.navigator.ui.util.shareClaimDraftEmail
import de.openbahn.rights.model.PassengerRightsAssessment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyDetailScreen(
    payload: JourneyDetailPayload,
    onBack: () -> Unit,
    onTrack: (() -> Unit)? = null,
    refreshUseCase: TrackedJourneyRefreshUseCase = koinInject(),
    predictionClient: BahnVorhersageClient = koinInject(),
    journeySearch: JourneySearchRepository = koinInject(),
    userPreferences: UserPreferencesRepository = koinInject(),
    passengerRights: PassengerRightsRepository = koinInject(),
    simulationRepository: PassengerRightsSimulationRepository = koinInject(),
) {
    val simulationConfig by simulationRepository.config.collectAsState(
        initial = de.openbahn.model.PassengerRightsSimulationConfig.Disabled,
    )
    var rawJourney by remember(payload.journey.id) { mutableStateOf(payload.journey) }
    var journey by remember(payload.journey.id) { mutableStateOf(payload.journey) }
    var prediction by remember(payload.journey.id) { mutableStateOf(payload.prediction) }
    var rightsAssessment by remember(payload.journey.id) { mutableStateOf<PassengerRightsAssessment?>(null) }
    var showRightsBanner by remember(payload.journey.id) { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    suspend fun refreshConnection() {
        if (isRefreshing) return
        isRefreshing = true
        try {
            val refreshed = refreshUseCase.refreshJourney(rawJourney) ?: return
            rawJourney = refreshed
            journey = refreshed.applyPassengerRightsSimulation(simulationConfig)
            val assessment = passengerRights.evaluate(
                journey = refreshed,
                minTransferMinutes = payload.minTransferMinutes ?: 0,
            )
            rightsAssessment = assessment
            showRightsBanner = passengerRights.shouldSurfaceRightsUi(assessment)
            if (payload.predictionsRequested) {
                val onTimeTolerance = userPreferences.onTimeTolerance.first()
                val options = JourneyRatingOptions(
                    minTransferMinutes = payload.minTransferMinutes,
                    onTimeTolerance = onTimeTolerance,
                )
                val tripRoutes = loadTripRoutesForJourneys(listOf(refreshed)) { leg ->
                    journeySearch.fetchFullLegRoute(leg)
                }
                prediction = predictionClient.rateJourney(refreshed, options, tripRoutes)
            }
        } finally {
            isRefreshing = false
        }
    }

    LaunchedEffect(payload.journey.id, payload.journey.refreshToken) {
        refreshConnection()
    }

    LaunchedEffect(simulationConfig, rawJourney.id) {
        journey = rawJourney.applyPassengerRightsSimulation(simulationConfig)
        if (!simulationConfig.enabled) {
            showRightsBanner = false
            rightsAssessment = null
            return@LaunchedEffect
        }
        val assessment = passengerRights.evaluate(
            journey = rawJourney,
            minTransferMinutes = payload.minTransferMinutes ?: 0,
        )
        rightsAssessment = assessment
        showRightsBanner = passengerRights.shouldSurfaceRightsUi(assessment)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.journey_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    ShareJourneyIconButton(
                        journey = journey,
                        prediction = prediction,
                        predictionsRequested = payload.predictionsRequested,
                    )
                    IconButton(
                        onClick = { scope.launch { refreshConnection() } },
                        enabled = !isRefreshing && journey.refreshToken?.isNotBlank() == true,
                        modifier = Modifier.testTag("journey_detail_refresh"),
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
        ) {
            if (BuildConfig.DEBUG && simulationConfig.enabled) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("passenger_rights_simulation_banner"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ) {
                        Text(
                            stringResource(
                                R.string.passenger_rights_simulation_active,
                                simulationConfig.arrivalDelayMinutes,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
            item {
                JourneyCard(
                    journey = journey,
                    prediction = prediction,
                    predictionsRequested = payload.predictionsRequested,
                    expanded = true,
                    onTrack = onTrack,
                    modifier = Modifier.testTag("journey_detail_card"),
                )
            }
            val assessment = rightsAssessment
            if (assessment != null && showRightsBanner) {
                item {
                    PassengerRightsBanner(
                        assessment = assessment,
                        onCreateClaimDraft = {
                            scope.launch {
                                val draft = passengerRights.createOrUpdateDraft(assessment)
                                context.shareClaimDraftEmail(draft)
                            }
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}
