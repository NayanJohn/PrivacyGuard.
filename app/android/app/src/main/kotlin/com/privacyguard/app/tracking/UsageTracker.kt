package com.privacyguard.app.tracking

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings

data class ForegroundWindow(
    val startMs: Long,
    val endMs: Long
) {
    fun toMap(): Map<String, Long> = mapOf(
        "start" to startMs,
        "end" to endMs
    )
}

data class RawUsageEvent(
    val eventType: Int,
    val timestamp: Long,
    val packageName: String,
    val className: String? = null
)

class UsageTracker(private val context: Context) {

    companion object {
        const val RESUME_API_29 = 1 // UsageEvents.Event.ACTIVITY_RESUMED
        const val STOP_API_29 = 23   // UsageEvents.Event.ACTIVITY_STOPPED
        const val RESUME_LEGACY = 1  // UsageEvents.Event.MOVE_TO_FOREGROUND
        const val STOP_LEGACY = 2    // UsageEvents.Event.MOVE_TO_BACKGROUND

        /**
         * Pure window reconstruction from raw usage events.
         * Pairs RESUMED and STOPPED events, closes trailing sessions at endMs,
         * accounts for events opened prior to query startMs, and merges overlapping sessions.
         */
        fun buildWindowsFromEvents(
            events: List<RawUsageEvent>,
            startMs: Long,
            endMs: Long,
            apiLevel: Int = Build.VERSION.SDK_INT,
            toleranceMs: Long = 1000L
        ): List<ForegroundWindow> {
            if (endMs <= startMs) return emptyList()

            val isModernApi = apiLevel >= 29
            val resumeType = if (isModernApi) RESUME_API_29 else RESUME_LEGACY
            val stopType = if (isModernApi) STOP_API_29 else STOP_LEGACY

            // Filter to lifecycle events and sort chronologically
            val sortedEvents = events
                .filter { it.eventType == resumeType || it.eventType == stopType }
                .sortedBy { it.timestamp }

            val rawWindows = mutableListOf<ForegroundWindow>()
            var currentStart: Long? = null

            for (event in sortedEvents) {
                if (event.eventType == resumeType) {
                    if (currentStart == null) {
                        currentStart = maxOf(event.timestamp, startMs)
                    }
                } else if (event.eventType == stopType) {
                    val stopTime = minOf(event.timestamp, endMs)
                    val windowStart = currentStart ?: startMs
                    if (stopTime >= windowStart) {
                        rawWindows.add(ForegroundWindow(windowStart, stopTime))
                    }
                    currentStart = null
                }
            }

            // Trailing open session closed at endMs
            if (currentStart != null && currentStart < endMs) {
                rawWindows.add(ForegroundWindow(currentStart, endMs))
            }

            if (rawWindows.isEmpty()) return emptyList()

            // Merge overlapping or adjacent sessions within tolerance
            val merged = mutableListOf<ForegroundWindow>()
            var active = rawWindows[0]

            for (i in 1 until rawWindows.size) {
                val next = rawWindows[i]
                if (next.startMs <= active.endMs + toleranceMs) {
                    active = ForegroundWindow(
                        startMs = active.startMs,
                        endMs = maxOf(active.endMs, next.endMs)
                    )
                } else {
                    merged.add(active)
                    active = next
                }
            }
            merged.add(active)

            return merged
        }
    }

    /**
     * FR-L1: Detect whether PACKAGE_USAGE_STATS is granted.
     */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false

        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * FR-L1: Redirect user to Settings.ACTION_USAGE_ACCESS_SETTINGS.
     */
    fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * FR-L2, FR-L3, FR-L4: Query UsageStatsManager.queryEvents and construct foreground sessions.
     */
    fun getForegroundWindows(packageName: String, startMs: Long, endMs: Long): List<ForegroundWindow> {
        if (!hasUsageAccess() || endMs <= startMs) {
            return emptyList()
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val usageEvents = usageStatsManager.queryEvents(startMs, endMs) ?: return emptyList()
        val rawEvents = mutableListOf<RawUsageEvent>()
        val event = UsageEvents.Event()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.packageName == packageName) {
                rawEvents.add(
                    RawUsageEvent(
                        eventType = event.eventType,
                        timestamp = event.timeStamp,
                        packageName = event.packageName,
                        className = event.className
                    )
                )
            }
        }

        return buildWindowsFromEvents(rawEvents, startMs, endMs, Build.VERSION.SDK_INT)
    }
}
