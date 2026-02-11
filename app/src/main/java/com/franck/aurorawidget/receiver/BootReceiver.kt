/**
 * @file BootReceiver.kt
 * @description Restarts WorkManager periodic aurora updates after device reboot.
 */
package com.franck.aurorawidget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.franck.aurorawidget.data.preferences.UserPreferences
import com.franck.aurorawidget.worker.AuroraUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Timber.d("BootReceiver: device rebooted, rescheduling worker")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = UserPreferences(context)
                    val interval = prefs.getSettings().refreshMinutes
                    AuroraUpdateWorker.schedule(context, interval.toLong())
                    Timber.d("BootReceiver: worker rescheduled (%d min)", interval)
                } catch (e: Exception) {
                    Timber.e(e, "BootReceiver: failed to reschedule worker")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
