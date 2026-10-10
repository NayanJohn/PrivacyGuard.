package com.privacyguard.app.scoring

import com.privacyguard.app.appops.AccessEvent
import com.privacyguard.app.baseline.PermissionBaselineResult
import com.privacyguard.app.tracking.ForegroundWindow
import kotlin.math.roundToInt

object ScoringEngine {

    fun classifyEvent(
        event: AccessEvent,
        windows: List<ForegroundWindow>,
        toleranceMs: Long = 5000L
    ): ObservedState {
        val uidStateLower = event.uidState.lowercase()
        val isExplicitForeground = uidStateLower == "top" ||
                uidStateLower.startsWith("top-") ||
                uidStateLower == "fg" ||
                uidStateLower.startsWith("fg-") ||
                uidStateLower.startsWith("pers")

        val eventTime = if (event.accessTimeMs > 0L) event.accessTimeMs else event.rejectTimeMs
        if (eventTime > 0L && windows.isNotEmpty()) {
            val inWindow = windows.any { w ->
                val wStart = w.startMs - toleranceMs
                val wEnd = w.endMs + toleranceMs
                eventTime in wStart..wEnd
            }
            if (inWindow) {
                return ObservedState.FOREGROUND
            }
        }

        return if (isExplicitForeground) {
            ObservedState.FOREGROUND
        } else {
            ObservedState.BACKGROUND
        }
    }

    fun isOpGranted(op: String, baseline: PermissionBaselineResult?): Boolean {
        if (baseline == null) return false
        val granted = baseline.granted
        return when (op) {
            "OP_CAMERA" -> granted.contains("android.permission.CAMERA")
            "OP_RECORD_AUDIO" -> granted.contains("android.permission.RECORD_AUDIO")
            "OP_FINE_LOCATION" -> granted.contains("android.permission.ACCESS_FINE_LOCATION")
            "OP_COARSE_LOCATION" -> granted.contains("android.permission.ACCESS_COARSE_LOCATION") || granted.contains("android.permission.ACCESS_FINE_LOCATION")
            "OP_MONITOR_LOCATION", "OP_MONITOR_HIGH_POWER_LOCATION" -> granted.contains("android.permission.ACCESS_FINE_LOCATION") || granted.contains("android.permission.ACCESS_COARSE_LOCATION")
            "OP_READ_SMS" -> granted.contains("android.permission.READ_SMS")
            "OP_SEND_SMS" -> granted.contains("android.permission.SEND_SMS")
            "OP_RECEIVE_SMS" -> granted.contains("android.permission.RECEIVE_SMS")
            "OP_READ_CONTACTS" -> granted.contains("android.permission.READ_CONTACTS")
            "OP_WRITE_CONTACTS" -> granted.contains("android.permission.WRITE_CONTACTS")
            "OP_READ_EXTERNAL_STORAGE" -> granted.contains("android.permission.READ_EXTERNAL_STORAGE")
            "OP_WRITE_EXTERNAL_STORAGE" -> granted.contains("android.permission.WRITE_EXTERNAL_STORAGE")
            "OP_MANAGE_EXTERNAL_STORAGE" -> granted.contains("android.permission.MANAGE_EXTERNAL_STORAGE")
            "OP_READ_MEDIA_AUDIO" -> granted.contains("android.permission.READ_MEDIA_AUDIO")
            "OP_READ_MEDIA_VIDEO" -> granted.contains("android.permission.READ_MEDIA_VIDEO")
            "OP_READ_MEDIA_IMAGES" -> granted.contains("android.permission.READ_MEDIA_IMAGES")
            "OP_SYSTEM_ALERT_WINDOW" -> granted.contains("android.permission.SYSTEM_ALERT_WINDOW")
            "OP_POST_NOTIFICATION", "OP_POST_NOTIFICATIONS" -> granted.contains("android.permission.POST_NOTIFICATIONS")
            else -> false
        }
    }

    fun calculateConfidence(
        dataSources: DataSourceStatus,
        weights: ConfidenceWeights = ConfidenceWeights()
    ): ConfidenceReport {
        // 1. Shizuku Status (35%)
        val shizukuQuality = if (dataSources.shizukuConnected) 1.0 else 0.0
        val shizukuContribution = weights.shizukuWeight * shizukuQuality * 100.0
        val shizukuComp = ConfidenceComponent(
            name = "Shizuku elevated binder status",
            weight = weights.shizukuWeight,
            quality = shizukuQuality,
            scoreContribution = shizukuContribution,
            detail = if (dataSources.shizukuConnected) "Active & authorized (elevated UID 2000)" else "Stopped or unauthorized (AppOps access unavailable)"
        )

        // 2. UsageStats Permission (25%)
        val usageQuality = if (dataSources.usageAccessGranted) 1.0 else 0.0
        val usageContribution = weights.usageStatsWeight * usageQuality * 100.0
        val usageComp = ConfidenceComponent(
            name = "UsageStats access permission",
            weight = weights.usageStatsWeight,
            quality = usageQuality,
            scoreContribution = usageContribution,
            detail = if (dataSources.usageAccessGranted) "Granted (real lifecycle events observed)" else "Missing (app lifecycle cannot be tracked)"
        )

        // 3. Polling Coverage (20%)
        val unmonitoredRatio = if (dataSources.totalWindowMs > 0L) {
            dataSources.unmonitoredGapMs.toDouble() / dataSources.totalWindowMs.toDouble()
        } else 0.0
        val coverageQuality = (1.0 - unmonitoredRatio).coerceIn(0.0, 1.0)
        val coverageContribution = weights.pollingCoverageWeight * coverageQuality * 100.0
        val coverageComp = ConfidenceComponent(
            name = "Audit window coverage",
            weight = weights.pollingCoverageWeight,
            quality = coverageQuality,
            scoreContribution = coverageContribution,
            detail = "${(coverageQuality * 100).roundToInt()}% window coverage without unmonitored gaps"
        )

        // 4. Sample Event Density (10%)
        val densityQuality = when {
            dataSources.totalEventsObserved >= 5 -> 1.0
            dataSources.totalEventsObserved in 1..4 -> 0.5
            else -> 0.2
        }
        val densityContribution = weights.sampleDensityWeight * densityQuality * 100.0
        val densityComp = ConfidenceComponent(
            name = "Event sample density",
            weight = weights.sampleDensityWeight,
            quality = densityQuality,
            scoreContribution = densityContribution,
            detail = "${dataSources.totalEventsObserved} events observed during audit window"
        )

        // 5. API Completeness (10%)
        val apiQuality = if (dataSources.apiLevel >= 29) 1.0 else 0.7
        val apiContribution = weights.apiCompletenessWeight * apiQuality * 100.0
        val apiComp = ConfidenceComponent(
            name = "Android API source level",
            weight = weights.apiCompletenessWeight,
            quality = apiQuality,
            scoreContribution = apiContribution,
            detail = "API ${dataSources.apiLevel} (${if (dataSources.apiLevel >= 29) "Full UsageEvents & AppOps table" else "Legacy events"})"
        )

        val components = listOf(shizukuComp, usageComp, coverageComp, densityComp, apiComp)
        val total = components.sumOf { it.scoreContribution }.roundToInt().coerceIn(0, 100)

        return ConfidenceReport(
            totalConfidence = total,
            components = components
        )
    }

    fun evaluate(
        packageName: String,
        foregroundWindows: List<ForegroundWindow>,
        accessEvents: List<AccessEvent>,
        baseline: PermissionBaselineResult?,
        dataSources: DataSourceStatus,
        config: ScoringConfig = ScoringConfig()
    ): ScanResult {
        val confidenceReport = calculateConfidence(dataSources, config.confidenceWeights)

        val status = if (
            !dataSources.shizukuConnected ||
            !dataSources.usageAccessGranted ||
            (dataSources.totalWindowMs > 0L && (dataSources.unmonitoredGapMs.toDouble() / dataSources.totalWindowMs.toDouble()) > 0.2)
        ) {
            ScanStatus.PRELIMINARY
        } else {
            ScanStatus.COMPLETE
        }

        // 1. NeverBlock packages (Settings, SystemUI, PrivacyGuard, dialer, emergency, launcher)
        // are system components and never carry risk penalties.
        if (NeverBlockList.isNeverBlock(packageName)) {
            return ScanResult(
                packageName = packageName,
                riskScore = 0,
                privacyHealth = 100,
                confidence = confidenceReport.totalConfidence,
                confidenceReport = confidenceReport,
                status = status,
                mismatches = emptyList(),
                totalMismatchesCount = 0
            )
        }

        data class ClassifiedEvent(val event: AccessEvent, val state: ObservedState)
        val classified = accessEvents.map { ev ->
            ClassifiedEvent(ev, classifyEvent(ev, foregroundWindows, config.foregroundWindowToleranceMs))
        }

        val grouped = classified.groupBy { Pair(it.event.op, it.state) }

        var totalPenalty = 0
        val mismatchesList = mutableListOf<Mismatch>()

        val hasBackgroundLocation = baseline?.granted?.contains("android.permission.ACCESS_BACKGROUND_LOCATION") == true

        for ((key, eventsInBucket) in grouped) {
            val (op, state) = key
            val weight = config.weights[op] ?: config.defaultWeight

            val hasActualAccess = eventsInBucket.any { it.event.accessCount > 0 || it.event.accessTimeMs > 0L }
            val isGrantedInBaseline = isOpGranted(op, baseline)

            val multiplier = when {
                // If only rejections occurred (system blocked access), no data breach occurred -> 0 penalty
                !hasActualAccess -> 0.0

                // Legitimate foreground access with granted permission -> 0 penalty
                state == ObservedState.FOREGROUND && isGrantedInBaseline -> config.grantedForegroundMultiplier

                // Foreground access with ungranted or missing baseline -> standard foreground multiplier
                state == ObservedState.FOREGROUND -> config.foregroundMultiplier

                // Legitimate background location grant -> 0 penalty
                state == ObservedState.BACKGROUND && op.contains("LOCATION") && hasBackgroundLocation -> 0.0

                // Unauthorized background access -> background multiplier
                state == ObservedState.BACKGROUND -> config.backgroundMultiplier

                else -> 0.0
            }

            val bucketPenalty = (weight * multiplier).toInt()
            totalPenalty += bucketPenalty

            val isLocation = op.contains("LOCATION")
            val isLegitimateBg = isLocation && hasBackgroundLocation

            // Only flag as mismatch if there was ACTUAL access in the background and not legitimately granted
            if (state == ObservedState.BACKGROUND && hasActualAccess && !isLegitimateBg) {
                val times = eventsInBucket.map { if (it.event.accessTimeMs > 0L) it.event.accessTimeMs else it.event.rejectTimeMs }.filter { it > 0L }
                val firstSeen = times.minOrNull() ?: 0L
                val lastSeen = times.maxOrNull() ?: 0L
                val count = eventsInBucket.sumOf { if (it.event.accessCount > 0) it.event.accessCount else 1 }

                val humanOp = op.removePrefix("OP_").replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                val detail = "$humanOp accessed in background $count time(s) outside foreground window"

                mismatchesList.add(
                    Mismatch(
                        permissionOrOp = op,
                        expectedState = "FOREGROUND_ONLY",
                        observedState = ObservedState.BACKGROUND,
                        evidenceCount = count,
                        firstSeenMs = firstSeen,
                        lastSeenMs = lastSeen,
                        severityScore = bucketPenalty,
                        detail = detail
                    )
                )
            }
        }

        val riskScore = totalPenalty.coerceIn(0, 100)
        val privacyHealth = 100 - riskScore

        val sortedMismatches = mismatchesList.sortedWith(
            compareByDescending<Mismatch> { it.severityScore }.thenByDescending { it.evidenceCount }
        )
        val top3Mismatches = sortedMismatches.take(3)

        return ScanResult(
            packageName = packageName,
            riskScore = riskScore,
            privacyHealth = privacyHealth,
            confidence = confidenceReport.totalConfidence,
            confidenceReport = confidenceReport,
            status = status,
            mismatches = top3Mismatches,
            totalMismatchesCount = sortedMismatches.size
        )
    }
}
