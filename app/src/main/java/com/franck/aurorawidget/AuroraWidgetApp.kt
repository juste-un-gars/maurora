/**
 * @file AuroraWidgetApp.kt
 * @description Application class — initializes Timber logging and WorkManager scheduling.
 */
package com.franck.aurorawidget

import android.app.Application
import com.franck.aurorawidget.data.preferences.UserPreferences
import com.franck.aurorawidget.worker.AuroraUpdateWorker
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class AuroraWidgetApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.d("AuroraWidgetApp initialized")

        val prefs = UserPreferences(this)
        val interval = runBlocking { prefs.getSettings().refreshMinutes }
        AuroraUpdateWorker.schedule(this, interval.toLong())
    }
}
