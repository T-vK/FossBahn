package de.openbahn.navigator

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import de.openbahn.navigator.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class OpenBahnApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startKoin {
            androidContext(this@OpenBahnApplication)
            modules(appModule)
        }
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
            ),
        )
    }

    companion object {
        const val CHANNEL_DELAY_ALERTS = "delay_alerts"
        const val CHANNEL_JOURNEY_TRACKING = "journey_tracking"
    }
}
