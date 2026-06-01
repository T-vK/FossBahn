package de.openbahn.navigator.ui.settings

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.openbahn.navigator.BuildConfig
import de.openbahn.navigator.R
import de.openbahn.navigator.locale.AppLanguage
import de.openbahn.navigator.locale.AppLocaleManager
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenClaims: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val appLanguage by viewModel.appLanguage.collectAsState()
    val onTimeTolerance by viewModel.onTimeTolerance.collectAsState()
    val deutschlandTicketOnly by viewModel.deutschlandTicketConnectionsOnly.collectAsState()
    val delayNotificationIncrement by viewModel.delayNotificationIncrementMinutes.collectAsState()
    val nearDepartureCheckSeconds by viewModel.nearDepartureCheckIntervalSeconds.collectAsState()
    val autoUpdateEnabled by viewModel.autoUpdateEnabled.collectAsState()
    val activity = LocalContext.current as? AppCompatActivity
    var searchQuery by remember { mutableStateOf("") }

    val categories = buildSettingsCategories(
        appLanguage = appLanguage,
        onAppLanguageSelect = { language ->
            if (language == appLanguage) return@buildSettingsCategories
            viewModel.setAppLanguage(language) {
                AppLocaleManager.apply(language)
                activity?.recreate()
            }
        },
        onTimeTolerance = onTimeTolerance,
        onOnTimeToleranceChange = viewModel::setOnTimeTolerance,
        deutschlandTicketOnly = deutschlandTicketOnly,
        onDeutschlandTicketOnlyChange = viewModel::setDeutschlandTicketConnectionsOnly,
        delayNotificationIncrement = delayNotificationIncrement,
        onDelayNotificationIncrementSelect = { minutes ->
            if (minutes != delayNotificationIncrement) {
                viewModel.setDelayNotificationIncrementMinutes(minutes)
            }
        },
        nearDepartureCheckSeconds = nearDepartureCheckSeconds,
        onNearDepartureCheckIntervalSelect = { seconds ->
            if (seconds != nearDepartureCheckSeconds) {
                viewModel.setNearDepartureCheckIntervalSeconds(seconds)
            }
        },
        autoUpdateEnabled = autoUpdateEnabled,
        onAutoUpdateEnabledChange = viewModel::setAutoUpdateEnabled,
        onOpenClaims = onOpenClaims,
        showDebugSimulation = BuildConfig.DEBUG,
    )

    val resources = LocalContext.current.resources
    val filteredCategories = remember(categories, searchQuery, resources) {
        filterSettingsCategories(categories, searchQuery, resources)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.settings_search_hint)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                singleLine = true,
            )
            if (filteredCategories.isEmpty()) {
                Text(
                    stringResource(R.string.settings_search_empty),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    filteredCategories.forEach { category ->
                        item(key = "cat_${category.titleRes}") {
                            SettingsCategoryBlock(category = category)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryBlock(category: SettingsCategory) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(category.titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Card(Modifier.fillMaxWidth()) {
            Column {
                category.items.forEachIndexed { index, item ->
                    SettingsItemBlock(item = item)
                    if (index < category.items.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsItemBlock(item: SettingsItem) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(item.titleRes),
                style = MaterialTheme.typography.titleMedium,
            )
            item.descriptionRes?.let { descRes ->
                Text(
                    text = stringResource(descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(Modifier.fillMaxWidth()) {
            item.content()
        }
    }
}

private fun filterSettingsCategories(
    categories: List<SettingsCategory>,
    query: String,
    resources: android.content.res.Resources,
): List<SettingsCategory> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return categories
    val needle = trimmed.lowercase()
    return categories.mapNotNull { category ->
        val categoryTexts = buildList {
            add(resources.getString(category.titleRes))
            category.keywordsRes.forEach { add(resources.getString(it)) }
        }
        val matchingItems = category.items.filter { item ->
            settingsItemMatches(item, needle, resources)
        }
        when {
            matchingItems.isNotEmpty() -> category.copy(items = matchingItems)
            categoryTexts.any { it.lowercase().contains(needle) } -> category
            else -> null
        }
    }
}

private fun settingsItemMatches(
    item: SettingsItem,
    needle: String,
    resources: android.content.res.Resources,
): Boolean {
    val texts = buildList {
        add(resources.getString(item.titleRes))
        item.descriptionRes?.let { add(resources.getString(it)) }
        item.keywordsRes.forEach { add(resources.getString(it)) }
    }
    return texts.any { it.lowercase().contains(needle) }
}
