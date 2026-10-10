package com.privacyguard.app.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockPolicyTest {

    private fun createScanResult(
        riskScore: Int,
        confidence: Int,
        status: ScanStatus = ScanStatus.COMPLETE
    ): ScanResult {
        return ScanResult(
            packageName = "com.untrusted.sample",
            riskScore = riskScore,
            privacyHealth = 100 - riskScore,
            confidence = confidence,
            confidenceReport = ConfidenceReport(confidence, emptyList()),
            status = status,
            mismatches = emptyList(),
            totalMismatchesCount = 0
        )
    }

    @Test
    fun testRow1_Score90StrictThresholdNotBlocked() {
        // final_score: 90, status: complete, confidence: 70, never-block: no, Device Owner: yes -> not blocked (strict >)
        val scan = createScanResult(riskScore = 90, confidence = 70, status = ScanStatus.COMPLETE)
        val device = DeviceContext(isDeviceOwner = true)
        val user = UserContext(age = 25, hardBlockingEnabled = true)

        val decision = BlockPolicy.evaluate(scan, scan.packageName, device, user)
        assertFalse("Score of exactly 90 must not block (strict > 90 required)", decision.shouldBlock)
        assertEquals(BlockVerdict.WARN_ONLY, decision.verdict)
        assertEquals(BlockReasonType.WARN_ONLY_LOW_SCORE, decision.reasonType)
    }

    @Test
    fun testRow2_Score91Confidence70IsBlocked() {
        // final_score: 91, status: complete, confidence: 70, never-block: no, Device Owner: yes -> blocked
        val scan = createScanResult(riskScore = 91, confidence = 70, status = ScanStatus.COMPLETE)
        val device = DeviceContext(isDeviceOwner = true)
        val user = UserContext(age = 25, hardBlockingEnabled = true)

        val decision = BlockPolicy.evaluate(scan, scan.packageName, device, user)
        assertTrue("Score of 91 with confidence 70 must be blocked", decision.shouldBlock)
        assertEquals(BlockVerdict.BLOCKED, decision.verdict)
        assertEquals(BlockReasonType.BLOCKED_HIGH_RISK, decision.reasonType)
    }

    @Test
    fun testRow3_Score91Confidence69WarnOnly() {
        // final_score: 91, status: complete, confidence: 69, never-block: no, Device Owner: yes -> warn only
        val scan = createScanResult(riskScore = 91, confidence = 69, status = ScanStatus.COMPLETE)
        val device = DeviceContext(isDeviceOwner = true)
        val user = UserContext(age = 25, hardBlockingEnabled = true)

        val decision = BlockPolicy.evaluate(scan, scan.packageName, device, user)
        assertFalse("Confidence 69 must not block (confidence >= 70 required)", decision.shouldBlock)
        assertEquals(BlockVerdict.WARN_ONLY, decision.verdict)
        assertEquals(BlockReasonType.WARN_ONLY_LOW_CONFIDENCE, decision.reasonType)
    }

    @Test
    fun testRow4_Score91PreliminaryNeverBlocks() {
        // final_score: 91, status: preliminary, confidence: 100, never-block: no, Device Owner: yes -> never blocks
        val scan = createScanResult(riskScore = 91, confidence = 100, status = ScanStatus.PRELIMINARY)
        val device = DeviceContext(isDeviceOwner = true)
        val user = UserContext(age = 25, hardBlockingEnabled = true)

        val decision = BlockPolicy.evaluate(scan, scan.packageName, device, user)
        assertFalse("Preliminary scan must never block", decision.shouldBlock)
        assertEquals(BlockVerdict.WARN_ONLY, decision.verdict)
        assertEquals(BlockReasonType.WARN_ONLY_PRELIMINARY, decision.reasonType)
    }

    @Test
    fun testRow5_Score100NeverBlockCategoriesNotBlockable() {
        // final_score: 100, status: complete, confidence: 100, never-block: yes -> not blockable
        val scan = createScanResult(riskScore = 100, confidence = 100, status = ScanStatus.COMPLETE)
        val device = DeviceContext(isDeviceOwner = true)
        val user = UserContext(age = 25, hardBlockingEnabled = true)

        val protectedApps = listOf(
            "com.android.settings",
            "com.privacyguard.app",
            "com.android.systemui",
            "android",
            "moe.shizuku.privileged.api"
        )

        for (pkg in protectedApps) {
            val decision = BlockPolicy.evaluate(scan, pkg, device, user)
            assertFalse("Protected app $pkg must not be blockable", decision.shouldBlock)
            assertEquals(BlockVerdict.NOT_BLOCKABLE, decision.verdict)
            assertEquals(BlockReasonType.NOT_BLOCKABLE_SYSTEM_PROTECTED, decision.reasonType)
        }
    }

    @Test
    fun testRow6_Score91NotDeviceOwnerWarnOnlyFallback() {
        // final_score: 91, status: complete, confidence: 70, never-block: no, Device Owner: no -> warn-only fallback
        val scan = createScanResult(riskScore = 91, confidence = 70, status = ScanStatus.COMPLETE)
        val device = DeviceContext(isDeviceOwner = false)
        val user = UserContext(age = 25, hardBlockingEnabled = true)

        val decision = BlockPolicy.evaluate(scan, scan.packageName, device, user)
        assertFalse("Without Device Owner, blocking cannot execute; must warn only", decision.shouldBlock)
        assertEquals(BlockVerdict.WARN_ONLY, decision.verdict)
        assertEquals(BlockReasonType.WARN_ONLY_NOT_DEVICE_OWNER, decision.reasonType)
    }

    @Test
    fun testRow7_Score91AgeUnder18NoParentLinkWarnOnly() {
        // final_score: 91, status: complete, confidence: 70, never-block: no, Device Owner: yes, age < 18, no parent link -> warn only
        val scan = createScanResult(riskScore = 91, confidence = 70, status = ScanStatus.COMPLETE)
        val device = DeviceContext(isDeviceOwner = true)
        val user = UserContext(age = 15, hardBlockingEnabled = true, hasVerifiedParentLink = false)

        val decision = BlockPolicy.evaluate(scan, scan.packageName, device, user)
        assertFalse("Under-18 user without verified parent link must warn only", decision.shouldBlock)
        assertEquals(BlockVerdict.WARN_ONLY, decision.verdict)
        assertEquals(BlockReasonType.WARN_ONLY_UNDER_18_NO_PARENT, decision.reasonType)
    }

    @Test
    fun testRow8_Score91AgeUnder18WithParentLinkBlocked() {
        // final_score: 91, status: complete, confidence: 70, never-block: no, Device Owner: yes, age < 18, parent link -> blocked
        val scan = createScanResult(riskScore = 91, confidence = 70, status = ScanStatus.COMPLETE)
        val device = DeviceContext(isDeviceOwner = true)
        val user = UserContext(age = 15, hardBlockingEnabled = true, hasVerifiedParentLink = true)

        val decision = BlockPolicy.evaluate(scan, scan.packageName, device, user)
        assertTrue("Under-18 user with verified parent link must be blocked", decision.shouldBlock)
        assertEquals(BlockVerdict.BLOCKED, decision.verdict)
        assertEquals(BlockReasonType.BLOCKED_HIGH_RISK, decision.reasonType)
    }
}
