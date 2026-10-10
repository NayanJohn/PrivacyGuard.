package com.privacyguard.app.background

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AuditWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val tag = "AuditWorker"

    override suspend fun doWork(): Result {
        Log.i(tag, "AuditWorker starting background execution")
        return try {
            val runner = AuditRunner(applicationContext)
            val result = runner.runAuditCycle()
            Log.i(tag, "AuditWorker finished: ${result.message}")
            Result.success()
        } catch (e: Exception) {
            Log.e(tag, "AuditWorker execution failed", e)
            Result.retry()
        }
    }
}
