package de.openbahn.navigator.ui.settings

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import de.openbahn.navigator.locale.AppLanguage

internal data class SettingsCategory(
    val titleRes: Int,
    val keywordsRes: List<Int> = emptyList(),
    val items: List<SettingsItem>,
)

internal data class SettingsItem(
    val titleRes: Int,
    val descriptionRes: Int? = null,
    val keywordsRes: List<Int> = emptyList(),
    val content: @Composable () -> Unit,
)

@Composable
internal fun buildSettingsCategories(
    appLanguage: AppLanguage,
    onAppLanguageSelect: (AppLanguage) -> Unit,
    onTimeTolerance: de.openbahn.model.OnTimeToleranceSettings,
    onOnTimeToleranceChange: (de.openbahn.model.OnTimeToleranceSettings) -> Unit,
    deutschlandTicketOnly: Boolean,
    onDeutschlandTicketOnlyChange: (Boolean) -> Unit,
    delayNotificationIncrement: Int,
    onDelayNotificationIncrementSelect: (Int) -> Unit,
    nearDepartureCheckSeconds: Int,
    onNearDepartureCheckIntervalSelect: (Int) -> Unit,
    autoUpdateEnabled: Boolean,
    onAutoUpdateEnabledChange: (Boolean) -> Unit,
    onOpenClaims: () -> Unit,
    showDebugSimulation: Boolean,
): List<SettingsCategory> = buildList {
    add(
        SettingsCategory(
            titleRes = de.openbahn.navigator.R.string.settings_category_general,
            items = listOf(
                SettingsItem(
                    titleRes = de.openbahn.navigator.R.string.settings_language,
                    descriptionRes = de.openbahn.navigator.R.string.settings_language_hint,
                    keywordsRes = listOf(
                        de.openbahn.navigator.R.string.settings_language_system,
                        de.openbahn.navigator.R.string.settings_language_german,
                        de.openbahn.navigator.R.string.settings_language_english,
                    ),
                ) {
                    LanguagePreferenceSection(
                        selected = appLanguage,
                        onSelect = onAppLanguageSelect,
                    )
                },
                SettingsItem(
                    titleRes = de.openbahn.navigator.R.string.settings_dticket_only,
                    descriptionRes = de.openbahn.navigator.R.string.settings_dticket_only_hint,
                    keywordsRes = listOf(
                        de.openbahn.navigator.R.string.settings_dticket_only,
                        de.openbahn.navigator.R.string.dticket_valid,
                    ),
                ) {
                    DeutschlandTicketDefaultSection(
                        enabled = deutschlandTicketOnly,
                        onEnabledChange = onDeutschlandTicketOnlyChange,
                    )
                },
            ),
        ),
    )
    add(
        SettingsCategory(
            titleRes = de.openbahn.navigator.R.string.settings_category_predictions,
            keywordsRes = listOf(de.openbahn.navigator.R.string.settings_punctuality),
            items = listOf(
                SettingsItem(
                    titleRes = de.openbahn.navigator.R.string.settings_punctuality,
                    descriptionRes = de.openbahn.navigator.R.string.settings_punctuality_hint,
                    keywordsRes = listOf(
                        de.openbahn.navigator.R.string.settings_on_time_departure,
                        de.openbahn.navigator.R.string.settings_on_time_arrival,
                        de.openbahn.navigator.R.string.settings_on_time_via,
                    ),
                ) {
                    OnTimeDefinitionsSection(
                        settings = onTimeTolerance,
                        onChange = onOnTimeToleranceChange,
                    )
                },
            ),
        ),
    )
    add(
        SettingsCategory(
            titleRes = de.openbahn.navigator.R.string.settings_category_live_updates,
            keywordsRes = listOf(
                de.openbahn.navigator.R.string.settings_delay_notification_increment,
                de.openbahn.navigator.R.string.settings_near_departure_check,
                de.openbahn.navigator.R.string.tracking_title,
            ),
            items = listOf(
                SettingsItem(
                    titleRes = de.openbahn.navigator.R.string.settings_delay_notification_increment,
                    descriptionRes = de.openbahn.navigator.R.string.settings_delay_notification_increment_hint,
                ) {
                    DelayNotificationIncrementSection(
                        selectedMinutes = delayNotificationIncrement,
                        onSelect = onDelayNotificationIncrementSelect,
                    )
                },
                SettingsItem(
                    titleRes = de.openbahn.navigator.R.string.settings_near_departure_check,
                    descriptionRes = de.openbahn.navigator.R.string.settings_near_departure_check_hint,
                ) {
                    NearDepartureCheckIntervalSection(
                        selectedSeconds = nearDepartureCheckSeconds,
                        onSelect = onNearDepartureCheckIntervalSelect,
                    )
                },
            ),
        ),
    )
    add(
        SettingsCategory(
            titleRes = de.openbahn.navigator.R.string.settings_category_development,
            items = listOf(
                SettingsItem(
                    titleRes = de.openbahn.navigator.R.string.settings_auto_update_title,
                    descriptionRes = de.openbahn.navigator.R.string.settings_auto_update_hint,
                    keywordsRes = listOf(
                        de.openbahn.navigator.R.string.settings_auto_update_switch,
                    ),
                ) {
                    AutoUpdateSection(
                        enabled = autoUpdateEnabled,
                        onEnabledChange = onAutoUpdateEnabledChange,
                    )
                },
            ),
        ),
    )
    add(
        SettingsCategory(
            titleRes = de.openbahn.navigator.R.string.passenger_rights_settings_heading,
            keywordsRes = listOf(
                de.openbahn.navigator.R.string.passenger_rights_claims_title,
                de.openbahn.navigator.R.string.passenger_rights_notifications,
            ),
            items = listOf(
                SettingsItem(
                    titleRes = de.openbahn.navigator.R.string.passenger_rights_settings_heading,
                    descriptionRes = de.openbahn.navigator.R.string.passenger_rights_notifications_desc,
                ) {
                    PassengerRightsSettingsSection(onOpenClaims = onOpenClaims)
                },
            ),
        ),
    )
    if (showDebugSimulation) {
        add(
            SettingsCategory(
                titleRes = de.openbahn.navigator.R.string.settings_category_debug,
                items = listOf(
                    SettingsItem(
                        titleRes = de.openbahn.navigator.R.string.passenger_rights_simulation_heading,
                        descriptionRes = de.openbahn.navigator.R.string.passenger_rights_simulation_hint,
                    ) {
                        PassengerRightsSimulationSection()
                    },
                ),
            ),
        )
    }
}

internal fun AppCompatActivity?.recreateForLanguageChange(onApplied: () -> Unit) {
    this?.recreate()
    onApplied()
}
