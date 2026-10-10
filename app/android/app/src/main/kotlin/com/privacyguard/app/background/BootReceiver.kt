package com.privacyguard.app.background

import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    private val tag = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(tag, "Device boot completed; rescheduling periodic audit")
            AuditScheduler.schedulePeriodicAudit(context)
        }
    }
}
