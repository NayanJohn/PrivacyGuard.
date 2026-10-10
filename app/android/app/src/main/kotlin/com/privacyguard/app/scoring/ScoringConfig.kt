package com.privacyguard.app.scoring

data class ScoringConfig(
    val weights: Map<String, Int> = mapOf(
        "OP_CAMERA" to 15,
        "OP_RECORD_AUDIO" to 15,
        "OP_FINE_LOCATION" to 12,
        "OP_MONITOR_LOCATION" to 12,
        "OP_READ_SMS" to 10,
        "OP_SEND_SMS" to 10,
        "OP_RECEIVE_SMS" to 10,
        "OP_READ_CONTACTS" to 10,
        "OP_WRITE_CONTACTS" to 10,
        "OP_READ_EXTERNAL_STORAGE" to 5,
        "OP_WRITE_EXTERNAL_STORAGE" to 5,
        "OP_MANAGE_EXTERNAL_STORAGE" to 5,
        "OP_READ_MEDIA_AUDIO" to 5,
        "OP_READ_MEDIA_VIDEO" to 5,
        "OP_READ_MEDIA_IMAGES" to 5,
        "OP_SYSTEM_ALERT_WINDOW" to 8
    ),
    val defaultWeight: Int = 5,
    val foregroundMultiplier: Double = 1.0,
    val grantedForegroundMultiplier: Double = 0.0,
    val backgroundMultiplier: Double = 10.0,
    val blockThreshold: Int = 90, // strict: score > 90 to block
    val minConfidence: Int = 70,  // confidence >= 70 to block
    val foregroundWindowToleranceMs: Long = 5000L, // 5s transition padding
    val confidenceWeights: ConfidenceWeights = ConfidenceWeights()
)

data class ConfidenceWeights(
    val shizukuWeight: Double = 0.35,
    val usageStatsWeight: Double = 0.25,
    val pollingCoverageWeight: Double = 0.20,
    val sampleDensityWeight: Double = 0.10,
    val apiCompletenessWeight: Double = 0.10
)
