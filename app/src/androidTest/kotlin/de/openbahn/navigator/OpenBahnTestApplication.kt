package de.openbahn.navigator

import android.app.Application
import de.openbahn.navigator.data.UserPreferencesRepository
import de.openbahn.navigator.di.appModule
import de.openbahn.navigator.domain.JourneySearchRepository
import de.openbahn.navigator.ui.search.FakeJourneySearchRepository
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.GlobalContext
import org.koin.dsl.module

/** Test application that stubs network journey search for instrumented UI tests. */
class OpenBahnTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@OpenBahnTestApplication)
            allowOverride(true)
            modules(
                appModule,
                module {
                    single<JourneySearchRepository> { FakeJourneySearchRepository() }
                },
            )
        }
        runBlocking {
            GlobalContext.get().get<UserPreferencesRepository>().completeOnboarding(deutschlandTicketOnlyDefault = false)
        }
    }
}
