/**
 * @file AuroraWidgetApp.kt
 * @description Application class — initializes Timber logging and WorkManager scheduling.
 */
package com.franck.aurorawidget

import android.app.Application
import com.franck.aurorawidget.data.preferences.UserPreferences
import com.franck.aurorawidget.notification.AuroraNotifier
import com.franck.aurorawidget.worker.AuroraUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class AuroraWidgetApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.d("AuroraWidgetApp initialized")
        AuroraNotifier.createChannel(this)

        appScope.launch {
            val prefs = UserPreferences(this@AuroraWidgetApp)
            val interval = prefs.getSettings().refreshMinutes
            AuroraUpdateWorker.schedule(this@AuroraWidgetApp, interval.toLong())
        }
    }
}
