package com.privacyguard.app.background

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AuditScheduler {
    private const val TAG = "AuditScheduler"
    const val PERIODIC_WORK_NAME = "privacyguard_periodic_audit"

    fun schedulePeriodicAudit(context: Context, intervalMinutes: Long = 15) {
        val workRequest = PeriodicWorkRequestBuilder<AuditWorker>(
            intervalMinutes.coerceAtLeast(15), TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        Log.i(TAG, "Periodic audit scheduled with interval of $intervalMinutes minutes")
    }

    fun cancelPeriodicAudit(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_WORK_NAME)
        Log.i(TAG, "Periodic audit cancelled")
    }

    fun isPeriodicAuditScheduled(context: Context): Boolean {
        return try {
            val infos = WorkManager.getInstance(context.applicationContext)
                .getWorkInfosForUniqueWork(PERIODIC_WORK_NAME)
                .get()
            infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check work status", e)
            false
        }
    }

    fun enqueueImmediateAudit(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<AuditWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueue(workRequest)
        Log.i(TAG, "Immediate one-time audit enqueued")
    }
}
