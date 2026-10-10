package com.privacyguard.app.background

import android.content.Context
import android.util.Log
import com.privacyguard.app.appops.AppOpsReader
import com.privacyguard.app.baseline.PermissionBaseline
import com.privacyguard.app.db.AccessEvent
import com.privacyguard.app.db.AppDatabase
import com.privacyguard.app.db.AuditCursor
import com.privacyguard.app.db.ForegroundSession
import com.privacyguard.app.db.RuntimeScore
import com.privacyguard.app.scoring.ScoringEngine
import com.privacyguard.app.tracking.UsageTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AuditCycleResult(
    val eventsAdded: Int,
    val packagesAudited: Int,
    val timestamp: Long,
    val message: String
)

class AuditRunner(private val context: Context) {
    private val tag = "AuditRunner"
    private val db = AppDatabase.getInstance(context)
    private val usageTracker = UsageTracker(context)
    private val permissionBaseline = PermissionBaseline(context)
    private val appOpsReader = AppOpsReader(context)

    suspend fun runAuditCycle(targetPackages: List<String> = emptyList()): AuditCycleResult = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        Log.i(tag, "Starting background audit cycle at $nowMs")

        // 1. Verify required permissions and services
        val hasUsage = usageTracker.hasUsageAccess()
        val shizukuRunning = appOpsReader.isShizukuRunning()
        val shizukuGranted = appOpsReader.hasShizukuPermission()

        if (!hasUsage) {
            Log.w(tag, "Usage access not granted; skipping audit cycle")
            return@withContext AuditCycleResult(0, 0, nowMs, "Usage access not granted")
        }

        // 2. Determine audit window from cursor
        val cursorKey = "appops_last_checked"
        val lastCheckedMs = db.auditCursorDao().getCursor(cursorKey)
            ?: (nowMs - 24 * 60 * 60 * 1000L) // Default: past 24h

        // 3. Resolve packages to audit
        val packagesToAudit = mutableSetOf<String>()
        if (targetPackages.isNotEmpty()) {
            packagesToAudit.addAll(targetPackages)
        } else {
            packagesToAudit.add("com.android.chrome")
            packagesToAudit.add("com.android.settings")
            packagesToAudit.add(context.packageName)
        }

        var totalEventsAdded = 0
        var totalAudited = 0

        // 4. Audit each package
        for (pkg in packagesToAudit) {
            try {
                // A. Collect foreground windows
                val windows = usageTracker.getForegroundWindows(pkg, lastCheckedMs, nowMs)
                if (windows.isNotEmpty()) {
                    val sessions = windows.map { w ->
                        ForegroundSession(
                            packageName = pkg,
                            startMs = w.startMs,
                            endMs = w.endMs
                        )
                    }
                    db.foregroundSessionDao().insertSessions(sessions)
                }

                // Query all sessions for this package to classify AppOps
                val allSessions = db.foregroundSessionDao().getSessionsForPackage(pkg, lastCheckedMs)

                // B. Fetch AppOps events via Shizuku if available
                if (shizukuRunning && shizukuGranted) {
                    val rawEvents = appOpsReader.getAccessEvents(pkg, lastCheckedMs)
                    if (rawEvents.isNotEmpty()) {
                        val roomEvents = rawEvents.map { e ->
                            val isFg = allSessions.any { s ->
                                (e.accessTimeMs in s.startMs..s.endMs) ||
                                (s.startMs <= e.accessTimeMs + e.durationMs && e.accessTimeMs <= s.endMs)
                            }
                            val stateStr = if (isFg) "FOREGROUND" else "BACKGROUND"
                            AccessEvent(
                                packageName = pkg,
                                op = e.op,
                                accessTimeMs = e.accessTimeMs,
                                durationMs = e.durationMs,
                                rejectTimeMs = e.rejectTimeMs,
                                state = stateStr
                            )
                        }
                        val insertedIds = db.accessEventDao().insertEvents(roomEvents)
                        totalEventsAdded += insertedIds.count { it > 0 }
                    }
                }

                // C. Capture Permission Baseline
                val baseline = try {
                    permissionBaseline.getBaseline(pkg)
                } catch (_: Exception) {
                    null
                }

                // D. Run Scoring Engine
                val accessEventsForScoring = db.accessEventDao().getEventsForPackage(pkg, lastCheckedMs, 200).map { ae ->
                    com.privacyguard.app.appops.AccessEvent(
                        op = ae.op,
                        packageName = ae.packageName,
                        accessTimeMs = ae.accessTimeMs,
                        durationMs = ae.durationMs ?: 0L,
                        rejectTimeMs = ae.rejectTimeMs ?: 0L,
                        uidState = ae.state
                    )
                }

                val dataSources = com.privacyguard.app.scoring.DataSourceStatus(
                    shizukuConnected = shizukuRunning && shizukuGranted,
                    usageAccessGranted = hasUsage,
                    unmonitoredGapMs = 0L,
                    totalWindowMs = (nowMs - lastCheckedMs).coerceAtLeast(1000L),
                    totalEventsObserved = accessEventsForScoring.size,
                    apiLevel = android.os.Build.VERSION.SDK_INT
                )

                val evalResult = ScoringEngine.evaluate(
                    packageName = pkg,
                    foregroundWindows = windows,
                    accessEvents = accessEventsForScoring,
                    baseline = baseline,
                    dataSources = dataSources
                )

                // Serialize mismatches to JSON
                val mismatchesJson = JSONArray().apply {
                    evalResult.mismatches.forEach { m ->
                        put(JSONObject().apply {
                            put("permissionOrOp", m.permissionOrOp)
                            put("expectedState", m.expectedState)
                            put("observedState", m.observedState.name)
                            put("evidenceCount", m.evidenceCount)
                            put("severityScore", m.severityScore)
                            put("detail", m.detail)
                        })
                    }
                }.toString()

                // Save RuntimeScore to Room
                val scoreRow = RuntimeScore(
                    packageName = pkg,
                    computedAtMs = nowMs,
                    runtimeRisk = evalResult.riskScore,
                    rawPenalty = evalResult.riskScore,
                    breakdownJson = mismatchesJson
                )
                db.runtimeScoreDao().insertScore(scoreRow)
                totalAudited++
            } catch (e: Exception) {
                Log.e(tag, "Failed to audit package $pkg", e)
            }
        }

        // 5. Update cursor watermarks
        db.auditCursorDao().setCursor(AuditCursor(cursorKey, nowMs))
        db.auditCursorDao().setCursor(AuditCursor("last_audit_completed", nowMs))

        val summary = "Audit cycle completed: $totalAudited packages audited, $totalEventsAdded new events saved"
        Log.i(tag, summary)
        return@withContext AuditCycleResult(totalEventsAdded, totalAudited, nowMs, summary)
    }
}
