package de.openbahn.navigator.di

import androidx.room.Room
import de.openbahn.api.BahnVorhersageClient
import de.openbahn.api.DbVendoClient
import de.openbahn.navigator.data.OpenBahnDatabase
import de.openbahn.navigator.data.TicketRepository
import de.openbahn.navigator.data.TrackedJourneyRepository
import de.openbahn.navigator.domain.JourneySearchUseCase
import de.openbahn.navigator.domain.PredictionUseCase
import de.openbahn.navigator.ui.board.StationBoardViewModel
import de.openbahn.navigator.ui.search.SearchViewModel
import de.openbahn.navigator.ui.tickets.TicketsViewModel
import de.openbahn.navigator.ui.tracking.TrackingViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { DbVendoClient() }
    single { BahnVorhersageClient() }
    single {
        Room.databaseBuilder(androidContext(), OpenBahnDatabase::class.java, "openbahn.db")
            .fallbackToDestructiveMigration()
            .build()
    }
    single { get<OpenBahnDatabase>().ticketDao() }
    single { get<OpenBahnDatabase>().trackedJourneyDao() }
    single { TicketRepository(get(), androidContext()) }
    single { TrackedJourneyRepository(get()) }
    single { JourneySearchUseCase(get(), get()) }
    single { PredictionUseCase(get()) }
    viewModel { SearchViewModel(get(), get()) }
    viewModel { StationBoardViewModel(get()) }
    viewModel { TicketsViewModel(get()) }
    viewModel { TrackingViewModel(get()) }
}
