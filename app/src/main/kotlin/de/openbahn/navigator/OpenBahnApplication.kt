package de.openbahn.navigator

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import de.openbahn.api.debug.OpenBahnDebugLog
import de.openbahn.navigator.data.UserPreferencesRepository
import de.openbahn.navigator.di.appModule
import de.openbahn.navigator.locale.AppLocaleManager
import de.openbahn.navigator.tracking.JourneyTrackingCoordinator
import de.openbahn.navigator.update.AppUpdateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

class OpenBahnApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        OpenBahnDebugLog.isEnabled = BuildConfig.DEBUG
        startKoin {
            androidContext(this@OpenBahnApplication)
            modules(appModule)
        }
        runBlocking {
            val language = getKoin().get<UserPreferencesRepository>().currentAppLanguage()
            AppLocaleManager.apply(language)
        }
        createNotificationChannels()
        applicationScope.launch {
            getKoin().get<JourneyTrackingCoordinator>().restoreOnLaunch()
        }
        getKoin().get<AppUpdateMonitor>().start(applicationScope)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DELAY_ALERTS,
                getString(R.string.notification_channel_delays),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = getString(R.string.notification_channel_delays_desc)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_JOURNEY_TRACKING,
                getString(R.string.notification_channel_tracking),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_tracking_desc)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PASSENGER_RIGHTS,
                getString(R.string.notification_channel_passenger_rights),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.notification_channel_passenger_rights_desc)
            },
        )
    }

    companion object {
        const val CHANNEL_DELAY_ALERTS = "delay_alerts"
        const val CHANNEL_JOURNEY_TRACKING = "journey_tracking"
        const val CHANNEL_PASSENGER_RIGHTS = "passenger_rights"
    }
}
