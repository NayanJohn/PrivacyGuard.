package com.privacyguard.app.scoring

enum class ScanStatus {
    PRELIMINARY,
    COMPLETE
}

enum class ObservedState {
    FOREGROUND,
    BACKGROUND,
    UNKNOWN
}

data class Mismatch(
    val permissionOrOp: String,
    val expectedState: String, // e.g. "FOREGROUND_ONLY"
    val observedState: ObservedState,
    val evidenceCount: Int,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val severityScore: Int, // weight * multiplier
    val detail: String
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "permissionOrOp" to permissionOrOp,
        "expectedState" to expectedState,
        "observedState" to observedState.name,
        "evidenceCount" to evidenceCount,
        "firstSeenMs" to firstSeenMs,
        "lastSeenMs" to lastSeenMs,
        "severityScore" to severityScore,
        "detail" to detail
    )
}

data class ConfidenceComponent(
    val name: String,
    val weight: Double,
    val quality: Double, // 0.0 to 1.0
    val scoreContribution: Double, // weight * quality * 100
    val detail: String
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "weight" to weight,
        "quality" to quality,
        "scoreContribution" to scoreContribution,
        "detail" to detail
    )
}

data class ConfidenceReport(
    val totalConfidence: Int, // 0 to 100
    val components: List<ConfidenceComponent>
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "totalConfidence" to totalConfidence,
        "components" to components.map { it.toMap() }
    )
}

data class DataSourceStatus(
    val shizukuConnected: Boolean,
    val usageAccessGranted: Boolean,
    val unmonitoredGapMs: Long = 0L,
    val totalWindowMs: Long = 3600_000L,
    val totalEventsObserved: Int = 0,
    val apiLevel: Int = 34
)

data class ScanResult(
    val packageName: String,
    val riskScore: Int, // 0 to 100
    val privacyHealth: Int, // 100 - riskScore
    val confidence: Int, // 0 to 100
    val confidenceReport: ConfidenceReport,
    val status: ScanStatus,
    val mismatches: List<Mismatch>, // Ranked top 3
    val totalMismatchesCount: Int,
    val evaluatedAtMs: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "riskScore" to riskScore,
        "privacyHealth" to privacyHealth,
        "confidence" to confidence,
        "confidenceReport" to confidenceReport.toMap(),
        "status" to status.name,
        "mismatches" to mismatches.map { it.toMap() },
        "totalMismatchesCount" to totalMismatchesCount,
        "evaluatedAtMs" to evaluatedAtMs
    )
}

data class UserContext(
    val age: Int = 25,
    val hardBlockingEnabled: Boolean = true,
    val hasVerifiedParentLink: Boolean = false
)

data class DeviceContext(
    val isDeviceOwner: Boolean = false
)

enum class BlockVerdict {
    BLOCKED,
    WARN_ONLY,
    NOT_BLOCKABLE,
    SAFE
}

enum class BlockReasonType {
    BLOCKED_HIGH_RISK,
    WARN_ONLY_LOW_SCORE,
    WARN_ONLY_PRELIMINARY,
    WARN_ONLY_LOW_CONFIDENCE,
    WARN_ONLY_NOT_DEVICE_OWNER,
    WARN_ONLY_UNDER_18_NO_PARENT,
    WARN_ONLY_DISABLED_BY_USER,
    NOT_BLOCKABLE_SYSTEM_PROTECTED,
    SAFE_LOW_RISK
}

data class BlockDecision(
    val verdict: BlockVerdict,
    val reasonType: BlockReasonType,
    val shouldBlock: Boolean,
    val explanation: String
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "verdict" to verdict.name,
        "reasonType" to reasonType.name,
        "shouldBlock" to shouldBlock,
        "explanation" to explanation
    )
}
