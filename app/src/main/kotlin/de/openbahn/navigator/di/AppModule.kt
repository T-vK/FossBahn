package de.openbahn.navigator.di

import androidx.room.Room
import de.openbahn.api.BahnVorhersageClient
import de.openbahn.api.DbVendoClient
import de.openbahn.navigator.BuildConfig
import de.openbahn.navigator.data.FavoriteRouteRepository
import de.openbahn.navigator.data.LocationHistoryRepository
import de.openbahn.navigator.data.MIGRATION_3_4
import de.openbahn.navigator.data.MIGRATION_4_5
import de.openbahn.navigator.data.ClaimDraftRepository
import de.openbahn.navigator.data.PassengerRightsSimulationRepository
import de.openbahn.navigator.domain.PassengerRightsRepository
import de.openbahn.navigator.tracking.PassengerRightsNotifier
import de.openbahn.navigator.tracking.TrackedJourneyRightsCheckUseCase
import de.openbahn.navigator.data.OpenBahnDatabase
import de.openbahn.navigator.data.PendingSearchRepository
import de.openbahn.navigator.data.TicketRepository
import de.openbahn.navigator.data.TrackedJourneyRepository
import de.openbahn.navigator.data.UserPreferencesRepository
import de.openbahn.navigator.domain.JourneySearchRepository
import de.openbahn.navigator.domain.JourneySearchUseCase
import de.openbahn.navigator.domain.PredictionUseCase
import de.openbahn.navigator.location.DeviceLocationProvider
import de.openbahn.navigator.tracking.DelayNotificationNotifier
import de.openbahn.navigator.tracking.JourneyTrackingCoordinator
import de.openbahn.navigator.tracking.TrackedJourneyDelayCheckUseCase
import de.openbahn.navigator.tracking.TrackedJourneyRefreshUseCase
import de.openbahn.navigator.ui.board.StationBoardViewModel
import de.openbahn.navigator.ui.favorites.FavoritesViewModel
import de.openbahn.navigator.ui.search.SearchViewModel
import de.openbahn.navigator.ui.settings.SettingsViewModel
import de.openbahn.navigator.ui.tickets.TicketsViewModel
import de.openbahn.navigator.ui.tracking.TrackingViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { DbVendoClient() }
    single { BahnVorhersageClient(baseUrl = BuildConfig.BAHN_VORHERSAGE_API_URL) }
    single {
        Room.databaseBuilder(androidContext(), OpenBahnDatabase::class.java, "openbahn.db")
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }
    single { get<OpenBahnDatabase>().ticketDao() }
    single { get<OpenBahnDatabase>().claimDraftDao() }
    single { get<OpenBahnDatabase>().trackedJourneyDao() }
    single { get<OpenBahnDatabase>().recentLocationDao() }
    single { get<OpenBahnDatabase>().favoriteLocationDao() }
    single { get<OpenBahnDatabase>().favoriteRouteDao() }
    single { UserPreferencesRepository(androidContext()) }
    single { PendingSearchRepository() }
    single { LocationHistoryRepository(get(), get()) }
    single { FavoriteRouteRepository(get()) }
    single { TicketRepository(get(), androidContext()) }
    single { JourneyTrackingCoordinator(androidContext(), lazy { get<TrackedJourneyRepository>() }) }
    single { TrackedJourneyRepository(get(), get(), lazy { get<JourneyTrackingCoordinator>() }) }
    single { DelayNotificationNotifier(androidContext()) }
    single { PassengerRightsNotifier(androidContext()) }
    single { ClaimDraftRepository(get(), get()) }
    single { PassengerRightsSimulationRepository(get()) }
    single { PassengerRightsRepository(get(), get(), get()) }
    single { TrackedJourneyRightsCheckUseCase(get(), get(), get(), get()) }
    single { TrackedJourneyRefreshUseCase(get(), get(), get()) }
    single { TrackedJourneyDelayCheckUseCase(get(), get(), get(), get()) }
    single { DeviceLocationProvider(androidContext(), get<JourneySearchRepository>()) }
    single<JourneySearchRepository> { JourneySearchUseCase(get(), get()) }
    single { PredictionUseCase(get()) }
    viewModel {
        SearchViewModel(get(), get(), get(), get(), get(), get(), get(), androidContext())
    }
    viewModel { FavoritesViewModel(get(), get(), get()) }
    viewModel { StationBoardViewModel(get()) }
    viewModel { TicketsViewModel(get()) }
    viewModel { TrackingViewModel(get(), get(), get(), get(), get(), get(), androidContext()) }
    viewModel { SettingsViewModel(get()) }
}
