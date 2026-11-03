package com.nevoit.cresto.feature.settings.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives PackageInstaller results for [AppUpdater]. Not exported: only our PendingIntent reaches it. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_INSTALL_STATUS) {
            AppUpdater.get(context).onInstallStatus(intent)
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.nevoit.cresto.action.UPDATE_INSTALL_STATUS"
    }
}
