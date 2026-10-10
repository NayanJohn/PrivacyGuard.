package com.privacyguard.app.appops

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class AppOpsReader(private val context: Context) {

    companion object {
        private const val TAG = "AppOpsReader"
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001

        private val newProcessMethod by lazy {
            try {
                Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).apply { isAccessible = true }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to resolve Shizuku.newProcess method", e)
                null
            }
        }
    }

    init {
        try {
            Shizuku.addBinderReceivedListenerSticky {
                Log.i(TAG, "Shizuku binder received via sticky listener!")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to register Shizuku binder received listener: ${e.message}")
        }
    }

    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        return try {
            if (!isShizukuRunning()) return false
            if (Shizuku.isPreV11()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    fun requestPermission(requestCode: Int = SHIZUKU_PERMISSION_REQUEST_CODE) {
        if (isShizukuRunning()) {
            try {
                Shizuku.requestPermission(requestCode)
            } catch (e: Throwable) {
                Log.e(TAG, "Error requesting Shizuku permission", e)
            }
        }
    }

    fun getAccessEvents(packageName: String, sinceMs: Long? = null): List<AccessEvent> {
        if (!isShizukuRunning() || !hasShizukuPermission()) {
            Log.w(TAG, "Shizuku is not running or permission not granted. Returning empty list.")
            return emptyList()
        }

        val allEvents = mutableListOf<AccessEvent>()
        val nowMs = System.currentTimeMillis()

        // 1. Query AppOps with --history
        try {
            val historyOutput = execElevatedCommand(arrayOf("dumpsys", "appops", "--package", packageName, "--history"))
            if (!historyOutput.isNullOrBlank()) {
                val parsed = AppOpsParser.parseAll(historyOutput, packageName, nowMs)
                allEvents.addAll(parsed)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to query dumpsys appops --history for $packageName", e)
        }

        // 2. Query standard dumpsys appops to catch any current access not yet aggregated into history
        try {
            val standardOutput = execElevatedCommand(arrayOf("dumpsys", "appops", "--package", packageName))
            if (!standardOutput.isNullOrBlank()) {
                val parsed = AppOpsParser.parseAll(standardOutput, packageName, nowMs)
                allEvents.addAll(parsed)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to query dumpsys appops for $packageName", e)
        }

        // 3. Fallback: if no events found yet, try cmd appops get
        if (allEvents.isEmpty()) {
            try {
                val cmdOutput = execElevatedCommand(arrayOf("cmd", "appops", "get", packageName))
                if (!cmdOutput.isNullOrBlank()) {
                    val parsed = AppOpsParser.parseCmdAppopsGet(cmdOutput, packageName, nowMs)
                    allEvents.addAll(parsed)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to query cmd appops get for $packageName", e)
            }
        }

        // Deduplicate on (op, accessTimeMs, rejectTimeMs)
        val deduplicated = mutableMapOf<String, AccessEvent>()
        for (event in allEvents) {
            val key = "${event.op}|${event.accessTimeMs}|${event.rejectTimeMs}"
            val existing = deduplicated[key]
            if (existing == null || event.durationMs > existing.durationMs) {
                deduplicated[key] = event
            }
        }

        var result = deduplicated.values.toList()

        // Filter by sinceMs if specified
        if (sinceMs != null && sinceMs > 0L) {
            result = result.filter { maxOf(it.accessTimeMs, it.rejectTimeMs) >= sinceMs }
        }

        return result.sortedByDescending { maxOf(it.accessTimeMs, it.rejectTimeMs) }
    }

    private fun execElevatedCommand(command: Array<String>): String? {
        val method = newProcessMethod ?: return null
        return try {
            val process = method.invoke(null, command, null, null) as java.lang.Process
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }
            process.waitFor(5, TimeUnit.SECONDS)
            output.toString()
        } catch (e: Throwable) {
            Log.e(TAG, "execElevatedCommand failed for ${command.joinToString(" ")}", e)
            null
        }
    }
}
