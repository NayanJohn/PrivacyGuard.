package com.privacyguard.app.bridge

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.privacyguard.app.baseline.PermissionBaseline
import com.privacyguard.app.tracking.UsageTracker
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NativeBridgeHandler(
    private val context: Context,
    messenger: BinaryMessenger
) : MethodChannel.MethodCallHandler {

    companion object {
        const val CHANNEL_NAME = "pg/native"
    }

    private val channel = MethodChannel(messenger, CHANNEL_NAME)
    private val usageTracker = UsageTracker(context)
    private val permissionBaseline = PermissionBaseline(context)
    private val appOpsReader = com.privacyguard.app.appops.AppOpsReader(context)

    init {
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getAccessStatus" -> {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                val isDeviceOwner = dpm?.isDeviceOwnerApp(context.packageName) ?: false
                val usageAccess = usageTracker.hasUsageAccess()
                val shizukuRunning = appOpsReader.isShizukuRunning()
                val shizukuPermission = appOpsReader.hasShizukuPermission()

                val status = mapOf(
                    "usageAccess" to usageAccess,
                    "shizukuRunning" to shizukuRunning,
                    "shizukuPermission" to shizukuPermission,
                    "deviceOwner" to isDeviceOwner,
                    "apiLevel" to Build.VERSION.SDK_INT
                )
                result.success(status)
            }

            "openUsageAccessSettings" -> {
                try {
                    usageTracker.openUsageAccessSettings()
                    result.success(null)
                } catch (e: Exception) {
                    result.error("INTENT_ERROR", e.localizedMessage, null)
                }
            }

            "getForegroundWindows" -> {
                val pkg = call.argument<String>("pkg")
                val startMs = call.argument<Number>("startMs")?.toLong()
                val endMs = call.argument<Number>("endMs")?.toLong()

                if (pkg == null || startMs == null || endMs == null) {
                    result.error("INVALID_ARGUMENTS", "pkg, startMs, and endMs are required", null)
                    return
                }

                try {
                    val windows = usageTracker.getForegroundWindows(pkg, startMs, endMs)
                    val resultList = windows.map { it.toMap() }
                    result.success(resultList)
                } catch (e: Exception) {
                    result.error("QUERY_ERROR", e.localizedMessage, null)
                }
            }

            "getBaseline" -> {
                val pkg = call.argument<String>("pkg")
                if (pkg.isNullOrBlank()) {
                    result.error("INVALID_ARGUMENTS", "pkg is required", null)
                    return
                }

                try {
                    val baseline = permissionBaseline.getBaseline(pkg)
                    result.success(baseline.toMap())
                } catch (e: PackageManager.NameNotFoundException) {
                    result.error("PACKAGE_NOT_FOUND", "Package '$pkg' was not found on this device", null)
                } catch (e: Exception) {
                    result.error("BASELINE_ERROR", e.localizedMessage, null)
                }
            }

            "requestShizukuPermission" -> {
                try {
                    appOpsReader.requestPermission()
                    result.success(true)
                } catch (e: Exception) {
                    result.error("SHIZUKU_ERROR", e.localizedMessage, null)
                }
            }

            "getAccessEvents" -> {
                val pkg = call.argument<String>("pkg")
                val sinceMs = call.argument<Number>("sinceMs")?.toLong()

                if (pkg.isNullOrBlank()) {
                    result.error("INVALID_ARGUMENTS", "pkg is required", null)
                    return
                }

                try {
                    val events = appOpsReader.getAccessEvents(pkg, sinceMs)
                    val resultList = events.map { it.toMap() }
                    result.success(resultList)
                } catch (e: Exception) {
                    result.error("APPOPS_ERROR", e.localizedMessage, null)
                }
            }

            "runScan" -> {
                val pkg = call.argument<String>("pkg")
                if (pkg.isNullOrBlank()) {
                    result.error("INVALID_ARGUMENTS", "pkg is required", null)
                    return
                }

                val windowMs = call.argument<Number>("windowMs")?.toLong() ?: 86400000L
                val userAge = call.argument<Number>("userAge")?.toInt() ?: 25
                val hasParentLink = call.argument<Boolean>("hasParentLink") ?: false
                val hardBlockingEnabled = call.argument<Boolean>("hardBlockingEnabled") ?: true

                try {
                    val nowMs = System.currentTimeMillis()
                    val startMs = nowMs - windowMs

                    val foregroundWindows = usageTracker.getForegroundWindows(pkg, startMs, nowMs)
                    val baseline = try {
                        permissionBaseline.getBaseline(pkg)
                    } catch (_: Exception) {
                        null
                    }
                    val accessEvents = appOpsReader.getAccessEvents(pkg, startMs)

                    val shizukuConnected = appOpsReader.isShizukuRunning() && appOpsReader.hasShizukuPermission()
                    val usageAccess = usageTracker.hasUsageAccess()

                    val dataSources = com.privacyguard.app.scoring.DataSourceStatus(
                        shizukuConnected = shizukuConnected,
                        usageAccessGranted = usageAccess,
                        unmonitoredGapMs = 0L,
                        totalWindowMs = windowMs,
                        totalEventsObserved = accessEvents.size,
                        apiLevel = Build.VERSION.SDK_INT
                    )

                    val scanResult = com.privacyguard.app.scoring.ScoringEngine.evaluate(
                        packageName = pkg,
                        foregroundWindows = foregroundWindows,
                        accessEvents = accessEvents,
                        baseline = baseline,
                        dataSources = dataSources
                    )

                    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                    val isDeviceOwner = dpm?.isDeviceOwnerApp(context.packageName) ?: false

                    val blockDecision = com.privacyguard.app.scoring.BlockPolicy.evaluate(
                        scan = scanResult,
                        packageName = pkg,
                        device = com.privacyguard.app.scoring.DeviceContext(isDeviceOwner = isDeviceOwner),
                        user = com.privacyguard.app.scoring.UserContext(
                            age = userAge,
                            hardBlockingEnabled = hardBlockingEnabled,
                            hasVerifiedParentLink = hasParentLink
                        )
                    )

                    val response = mapOf(
                        "scan" to scanResult.toMap(),
                        "blockDecision" to blockDecision.toMap(),
                        "windowsCount" to foregroundWindows.size,
                        "eventsCount" to accessEvents.size,
                        "baselineGrantedCount" to (baseline?.granted?.size ?: 0)
                    )

                    // Persist to Room in background
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = com.privacyguard.app.db.AppDatabase.getInstance(context)
                            val mismatchesJson = org.json.JSONArray().apply {
                                scanResult.mismatches.forEach { m ->
                                    put(org.json.JSONObject().apply {
                                        put("permissionOrOp", m.permissionOrOp)
                                        put("expectedState", m.expectedState)
                                        put("observedState", m.observedState.name)
                                        put("evidenceCount", m.evidenceCount)
                                        put("severityScore", m.severityScore)
                                        put("detail", m.detail)
                                    })
                                }
                            }.toString()

                            val scoreRow = com.privacyguard.app.db.RuntimeScore(
                                packageName = pkg,
                                computedAtMs = nowMs,
                                runtimeRisk = scanResult.riskScore,
                                rawPenalty = scanResult.riskScore,
                                breakdownJson = mismatchesJson
                            )
                            db.runtimeScoreDao().insertScore(scoreRow)

                            db.localBlockStateDao().upsertBlockState(
                                com.privacyguard.app.db.LocalBlockState(
                                    packageName = pkg,
                                    state = blockDecision.verdict.name.lowercase(),
                                    scanId = null,
                                    finalScore = scanResult.riskScore.toDouble(),
                                    confidence = scanResult.confidence.toDouble(),
                                    verdict = blockDecision.verdict.name,
                                    reasonsJson = mismatchesJson,
                                    failedSuspend = false,
                                    blockedAtMs = if (blockDecision.verdict == com.privacyguard.app.scoring.BlockVerdict.BLOCKED) nowMs else null,
                                    updatedAtMs = nowMs
                                )
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("NativeBridge", "Failed to persist scan to Room", e)
                        }
                    }

                    result.success(response)
                } catch (e: Exception) {
                    result.error("SCAN_ERROR", e.localizedMessage, null)
                }
            }

            "triggerAudit" -> {
                val packages = call.argument<List<String>>("packages") ?: emptyList()
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val runner = com.privacyguard.app.background.AuditRunner(context)
                        val cycleResult = runner.runAuditCycle(packages)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            result.success(mapOf(
                                "eventsAdded" to cycleResult.eventsAdded,
                                "packagesAudited" to cycleResult.packagesAudited,
                                "timestamp" to cycleResult.timestamp,
                                "message" to cycleResult.message
                            ))
                        }
                    } catch (e: Exception) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            result.error("AUDIT_ERROR", e.localizedMessage, null)
                        }
                    }
                }
            }

            "getAuditStatus" -> {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val isScheduled = com.privacyguard.app.background.AuditScheduler.isPeriodicAuditScheduled(context)
                        val db = com.privacyguard.app.db.AppDatabase.getInstance(context)
                        val lastAudit = db.auditCursorDao().getCursor("last_audit_completed") ?: 0L
                        val eventCount = db.accessEventDao().getEventCount()
                        val sessionCount = db.foregroundSessionDao().getSessionCount()
                        val scoreCount = db.runtimeScoreDao().getScoreCount()

                        val status = mapOf(
                            "isPeriodicScheduled" to isScheduled,
                            "lastAuditCompletedMs" to lastAudit,
                            "totalEventsCount" to eventCount,
                            "totalSessionsCount" to sessionCount,
                            "totalScoresCount" to scoreCount
                        )
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            result.success(status)
                        }
                    } catch (e: Exception) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            result.error("DB_ERROR", e.localizedMessage, null)
                        }
                    }
                }
            }

            "schedulePeriodicAudit" -> {
                val intervalMinutes = call.argument<Number>("intervalMinutes")?.toLong() ?: 15L
                try {
                    com.privacyguard.app.background.AuditScheduler.schedulePeriodicAudit(context, intervalMinutes)
                    result.success(true)
                } catch (e: Exception) {
                    result.error("SCHEDULE_ERROR", e.localizedMessage, null)
                }
            }

            "cancelPeriodicAudit" -> {
                try {
                    com.privacyguard.app.background.AuditScheduler.cancelPeriodicAudit(context)
                    result.success(true)
                } catch (e: Exception) {
                    result.error("SCHEDULE_ERROR", e.localizedMessage, null)
                }
            }

            "getAuditHistory" -> {
                val pkg = call.argument<String>("pkg")
                if (pkg.isNullOrBlank()) {
                    result.error("INVALID_ARGUMENTS", "pkg is required", null)
                    return
                }

                val limit = call.argument<Number>("limit")?.toInt() ?: 50
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val db = com.privacyguard.app.db.AppDatabase.getInstance(context)
                        val history = db.runtimeScoreDao().getScoreHistory(pkg, limit)
                        val list = history.map { s ->
                            mapOf(
                                "id" to s.id,
                                "packageName" to s.packageName,
                                "computedAtMs" to s.computedAtMs,
                                "runtimeRisk" to s.runtimeRisk,
                                "rawPenalty" to s.rawPenalty,
                                "breakdownJson" to s.breakdownJson
                            )
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            result.success(list)
                        }
                    } catch (e: Exception) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            result.error("DB_ERROR", e.localizedMessage, null)
                        }
                    }
                }
            }

            "getTimelineEvents" -> {
                val pkg = call.argument<String>("pkg")
                val limit = call.argument<Number>("limit")?.toInt() ?: 100
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val db = com.privacyguard.app.db.AppDatabase.getInstance(context)
                        val events = if (!pkg.isNullOrBlank()) {
                            db.accessEventDao().getEventsForPackage(pkg, 0L, limit)
                        } else {
                            db.accessEventDao().getRecentEvents(limit)
                        }
                        val list = events.map { e ->
                            mapOf(
                                "id" to e.id,
                                "packageName" to e.packageName,
                                "op" to e.op,
                                "accessTimeMs" to e.accessTimeMs,
                                "durationMs" to e.durationMs,
                                "rejectTimeMs" to e.rejectTimeMs,
                                "state" to e.state
                            )
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            result.success(list)
                        }
                    } catch (e: Exception) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            result.error("DB_ERROR", e.localizedMessage, null)
                        }
                    }
                }
            }

            "suspend",
            "unsuspend",
            "isSuspended",
            "releaseControl",
            "syncBlocks" -> {
                result.error(
                    "NOT_IMPLEMENTED",
                    "Method '${call.method}' is specified in pg/native contract but not yet implemented in Phase P5 (P6/P7 scope)",
                    null
                )
            }


            else -> {
                result.notImplemented()
            }
        }
    }
}
