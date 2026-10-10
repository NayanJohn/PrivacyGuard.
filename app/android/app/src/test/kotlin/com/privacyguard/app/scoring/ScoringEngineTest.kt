package com.privacyguard.app.scoring

import com.privacyguard.app.appops.AccessEvent
import com.privacyguard.app.baseline.PermissionBaselineResult
import com.privacyguard.app.tracking.ForegroundWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringEngineTest {

    @Test
    fun testClassifyEventInsideForegroundWindow() {
        val window = ForegroundWindow(startMs = 10_000L, endMs = 20_000L)
        val event = AccessEvent(
            op = "OP_CAMERA",
            packageName = "com.sample.app",
            accessTimeMs = 15_000L,
            uidState = "unknown"
        )

        val state = ScoringEngine.classifyEvent(event, listOf(window), toleranceMs = 2000L)
        assertEquals(ObservedState.FOREGROUND, state)
    }

    @Test
    fun testClassifyEventWithinToleranceBoundary() {
        val window = ForegroundWindow(startMs = 10_000L, endMs = 20_000L)
        // 1000ms before window start (within 2000ms tolerance)
        val boundaryEvent = AccessEvent(
            op = "OP_FINE_LOCATION",
            packageName = "com.sample.app",
            accessTimeMs = 9_000L,
            uidState = "unknown"
        )

        val state = ScoringEngine.classifyEvent(boundaryEvent, listOf(window), toleranceMs = 2000L)
        assertEquals(ObservedState.FOREGROUND, state)
    }

    @Test
    fun testClassifyEventOutsideWindowIsBackground() {
        val window = ForegroundWindow(startMs = 10_000L, endMs = 20_000L)
        val outsideEvent = AccessEvent(
            op = "OP_RECORD_AUDIO",
            packageName = "com.sample.app",
            accessTimeMs = 25_000L,
            uidState = "cch"
        )

        val state = ScoringEngine.classifyEvent(outsideEvent, listOf(window), toleranceMs = 2000L)
        assertEquals(ObservedState.BACKGROUND, state)
    }

    @Test
    fun testStackingRuleAndScoreClamping() {
        // Camera (weight 15 * 10 = 150) -> clamped to 100
        val cameraBg = AccessEvent(
            op = "OP_CAMERA",
            packageName = "com.sample.app",
            accessTimeMs = 30_000L,
            uidState = "cch"
        )

        val dataSources = DataSourceStatus(
            shizukuConnected = true,
            usageAccessGranted = true,
            totalEventsObserved = 5,
            apiLevel = 34
        )

        val result = ScoringEngine.evaluate(
            packageName = "com.sample.app",
            foregroundWindows = emptyList(),
            accessEvents = listOf(cameraBg),
            baseline = null,
            dataSources = dataSources
        )

        assertEquals(100, result.riskScore)
        assertEquals(0, result.privacyHealth)
        assertEquals(1, result.mismatches.size)
        assertEquals("OP_CAMERA", result.mismatches[0].permissionOrOp)
        assertEquals(150, result.mismatches[0].severityScore)
    }

    @Test
    fun testForegroundAccessModerateScore() {
        // Foreground Fine Location (weight 12 * 1 = 12)
        val window = ForegroundWindow(startMs = 10_000L, endMs = 20_000L)
        val locFg = AccessEvent(
            op = "OP_FINE_LOCATION",
            packageName = "com.sample.app",
            accessTimeMs = 15_000L,
            uidState = "top"
        )

        val dataSources = DataSourceStatus(
            shizukuConnected = true,
            usageAccessGranted = true,
            totalEventsObserved = 2,
            apiLevel = 34
        )

        val result = ScoringEngine.evaluate(
            packageName = "com.sample.app",
            foregroundWindows = listOf(window),
            accessEvents = listOf(locFg),
            baseline = null,
            dataSources = dataSources
        )

        assertEquals(12, result.riskScore)
        assertEquals(88, result.privacyHealth)
        assertTrue("Foreground access without background is not a mismatch", result.mismatches.isEmpty())
    }

    @Test
    fun testConfidenceCalculationAllComponents() {
        val fullData = DataSourceStatus(
            shizukuConnected = true,
            usageAccessGranted = true,
            unmonitoredGapMs = 0L,
            totalWindowMs = 3600_000L,
            totalEventsObserved = 10,
            apiLevel = 34
        )

        val report = ScoringEngine.calculateConfidence(fullData)
        assertEquals(100, report.totalConfidence)
        assertEquals(5, report.components.size)
    }

    @Test
    fun testConfidenceDegradesWithoutShizukuOrUsageAccess() {
        val degradedData = DataSourceStatus(
            shizukuConnected = false,
            usageAccessGranted = false,
            unmonitoredGapMs = 0L,
            totalWindowMs = 3600_000L,
            totalEventsObserved = 0,
            apiLevel = 34
        )

        val report = ScoringEngine.calculateConfidence(degradedData)
        // 35% shizuku lost + 25% usage lost = 60% lost.
        // Remaining: coverage 20% + density 2% + api 10% = 32%
        assertTrue("Confidence must degrade when critical sources are missing", report.totalConfidence < 40)
    }

    @Test
    fun testScanStatusPreliminaryWhenShizukuMissing() {
        val missingShizuku = DataSourceStatus(
            shizukuConnected = false,
            usageAccessGranted = true
        )

        val result = ScoringEngine.evaluate(
            packageName = "com.sample.app",
            foregroundWindows = emptyList(),
            accessEvents = emptyList(),
            baseline = null,
            dataSources = missingShizuku
        )

        assertEquals(ScanStatus.PRELIMINARY, result.status)
    }

    @Test
    fun testTop3MismatchesRanking() {
        // Create 4 background events with different weights:
        // Storage: 5 * 10 = 50
        // SMS: 10 * 10 = 100
        // Mic: 15 * 10 = 150
        // Camera: 15 * 10 = 150 (with 2 counts)
        val evStorage = AccessEvent(op = "OP_READ_EXTERNAL_STORAGE", packageName = "p", accessTimeMs = 1000L, accessCount = 1)
        val evSms = AccessEvent(op = "OP_READ_SMS", packageName = "p", accessTimeMs = 2000L, accessCount = 1)
        val evMic = AccessEvent(op = "OP_RECORD_AUDIO", packageName = "p", accessTimeMs = 3000L, accessCount = 1)
        val evCam = AccessEvent(op = "OP_CAMERA", packageName = "p", accessTimeMs = 4000L, accessCount = 2)

        val dataSources = DataSourceStatus(shizukuConnected = true, usageAccessGranted = true, totalEventsObserved = 5)

        val result = ScoringEngine.evaluate(
            packageName = "p",
            foregroundWindows = emptyList(),
            accessEvents = listOf(evStorage, evSms, evMic, evCam),
            baseline = null,
            dataSources = dataSources
        )

        assertEquals(3, result.mismatches.size)
        // Top 1: Camera (severity 150, count 2)
        assertEquals("OP_CAMERA", result.mismatches[0].permissionOrOp)
        // Top 2: Mic (severity 150, count 1)
        assertEquals("OP_RECORD_AUDIO", result.mismatches[1].permissionOrOp)
        // Top 3: SMS (severity 100)
        assertEquals("OP_READ_SMS", result.mismatches[2].permissionOrOp)
    }

    @Test
    fun testLegitimateBackgroundLocationSuppressesMismatch() {
        val locBg = AccessEvent(
            op = "OP_FINE_LOCATION",
            packageName = "com.sample.app",
            accessTimeMs = 1000L,
            uidState = "cch"
        )

        val baselineWithBg = PermissionBaselineResult(
            packageName = "com.sample.app",
            granted = listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION"),
            notGranted = emptyList()
        )

        val dataSources = DataSourceStatus(shizukuConnected = true, usageAccessGranted = true, totalEventsObserved = 1)

        val result = ScoringEngine.evaluate(
            packageName = "com.sample.app",
            foregroundWindows = emptyList(),
            accessEvents = listOf(locBg),
            baseline = baselineWithBg,
            dataSources = dataSources
        )

        assertTrue("Legitimate background location grant should not flag as unexpected mismatch", result.mismatches.isEmpty())
    }

    @Test
    fun testNeverBlockSettingsReturnsZeroRiskScore() {
        val settingsEvents = listOf(
            AccessEvent(op = "OP_CAMERA", packageName = "com.android.settings", accessTimeMs = 1000L, uidState = "cch"),
            AccessEvent(op = "OP_RECORD_AUDIO", packageName = "com.android.settings", accessTimeMs = 2000L, uidState = "cch")
        )

        val dataSources = DataSourceStatus(shizukuConnected = true, usageAccessGranted = true, totalEventsObserved = 2)

        val result = ScoringEngine.evaluate(
            packageName = "com.android.settings",
            foregroundWindows = emptyList(),
            accessEvents = settingsEvents,
            baseline = null,
            dataSources = dataSources
        )

        assertEquals("Settings must have 0 risk score", 0, result.riskScore)
        assertEquals("Settings must have 100 privacy health", 100, result.privacyHealth)
        assertTrue("Settings must have no mismatches", result.mismatches.isEmpty())
    }

    @Test
    fun testChromeGrantedForegroundAccessReturnsZeroRiskScore() {
        val window = ForegroundWindow(startMs = 10_000L, endMs = 20_000L)
        val chromeEvents = listOf(
            AccessEvent(op = "OP_CAMERA", packageName = "com.android.chrome", accessTimeMs = 15_000L, accessCount = 1, uidState = "top-s"),
            AccessEvent(op = "OP_FINE_LOCATION", packageName = "com.android.chrome", accessTimeMs = 16_000L, accessCount = 1, uidState = "top-s"),
            AccessEvent(op = "OP_RECORD_AUDIO", packageName = "com.android.chrome", accessTimeMs = 0L, rejectTimeMs = 12_000L, rejectCount = 1, uidState = "cch-s")
        )

        val baseline = PermissionBaselineResult(
            packageName = "com.android.chrome",
            granted = listOf(
                "android.permission.CAMERA",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.RECORD_AUDIO"
            ),
            notGranted = emptyList()
        )

        val dataSources = DataSourceStatus(shizukuConnected = true, usageAccessGranted = true, totalEventsObserved = 3)

        val result = ScoringEngine.evaluate(
            packageName = "com.android.chrome",
            foregroundWindows = listOf(window),
            accessEvents = chromeEvents,
            baseline = baseline,
            dataSources = dataSources
        )

        assertTrue("Chrome risk score must be 0-10 low risk", result.riskScore in 0..10)
        assertEquals("Chrome risk score with granted permissions is 0", 0, result.riskScore)
        assertEquals("Chrome privacy health is 100", 100, result.privacyHealth)
        assertTrue("Chrome must have no mismatches", result.mismatches.isEmpty())
    }

    @Test
    fun testPureRejectionsDoNotGeneratePenaltiesOrMismatches() {
        val rejectOnly = AccessEvent(
            op = "OP_CAMERA",
            packageName = "com.sample.app",
            accessTimeMs = 0L,
            rejectTimeMs = 25_000L,
            accessCount = 0,
            rejectCount = 1,
            uidState = "cch"
        )

        val dataSources = DataSourceStatus(shizukuConnected = true, usageAccessGranted = true, totalEventsObserved = 1)

        val result = ScoringEngine.evaluate(
            packageName = "com.sample.app",
            foregroundWindows = emptyList(),
            accessEvents = listOf(rejectOnly),
            baseline = null,
            dataSources = dataSources
        )

        assertEquals("Pure rejections must not add risk score penalty", 0, result.riskScore)
        assertEquals("Privacy health remains 100", 100, result.privacyHealth)
        assertTrue("Pure rejections must not generate mismatches", result.mismatches.isEmpty())
    }
}
