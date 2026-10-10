package com.privacyguard.app.scoring

object BlockPolicy {

    fun evaluate(
        scan: ScanResult,
        packageName: String,
        device: DeviceContext,
        user: UserContext,
        isNeverBlock: Boolean = false,
        config: ScoringConfig = ScoringConfig()
    ): BlockDecision {
        // 1. Never-block check
        if (isNeverBlock || NeverBlockList.isNeverBlock(packageName)) {
            return BlockDecision(
                verdict = BlockVerdict.NOT_BLOCKABLE,
                reasonType = BlockReasonType.NOT_BLOCKABLE_SYSTEM_PROTECTED,
                shouldBlock = false,
                explanation = "Package '$packageName' is protected by system never-block policy (critical system, dialer, SMS, launcher, emergency, or PrivacyGuard)."
            )
        }

        // 2. Scan status: preliminary scans NEVER block!
        if (scan.status == ScanStatus.PRELIMINARY) {
            return BlockDecision(
                verdict = BlockVerdict.WARN_ONLY,
                reasonType = BlockReasonType.WARN_ONLY_PRELIMINARY,
                shouldBlock = false,
                explanation = "Scan is preliminary (observation window incomplete or data source interrupted). Preliminary scans never block."
            )
        }

        // 3. Strict score threshold: must be strictly > 90
        if (scan.riskScore <= config.blockThreshold) {
            val verdict = if (scan.riskScore > 30) BlockVerdict.WARN_ONLY else BlockVerdict.SAFE
            val reason = if (scan.riskScore > 30) BlockReasonType.WARN_ONLY_LOW_SCORE else BlockReasonType.SAFE_LOW_RISK
            return BlockDecision(
                verdict = verdict,
                reasonType = reason,
                shouldBlock = false,
                explanation = "Risk score (${scan.riskScore}) is at or below block threshold (${config.blockThreshold}). App is not subject to blocking."
            )
        }

        // 4. Confidence threshold: must be >= 70
        if (scan.confidence < config.minConfidence) {
            return BlockDecision(
                verdict = BlockVerdict.WARN_ONLY,
                reasonType = BlockReasonType.WARN_ONLY_LOW_CONFIDENCE,
                shouldBlock = false,
                explanation = "Confidence (${scan.confidence}%) is below minimum threshold (${config.minConfidence}%). Fallback to warn-only to prevent false positives."
            )
        }

        // 5. Device Owner check
        if (!device.isDeviceOwner) {
            return BlockDecision(
                verdict = BlockVerdict.WARN_ONLY,
                reasonType = BlockReasonType.WARN_ONLY_NOT_DEVICE_OWNER,
                shouldBlock = false,
                explanation = "Device Owner privileges not active on this device. Fallback to warn-only notification."
            )
        }

        // 6. User Age & Parental Controls
        if (user.age < 18) {
            if (!user.hasVerifiedParentLink) {
                return BlockDecision(
                    verdict = BlockVerdict.WARN_ONLY,
                    reasonType = BlockReasonType.WARN_ONLY_UNDER_18_NO_PARENT,
                    shouldBlock = false,
                    explanation = "User is under 18 without verified parent link. Warn-only fallback per policy."
                )
            } else {
                return BlockDecision(
                    verdict = BlockVerdict.BLOCKED,
                    reasonType = BlockReasonType.BLOCKED_HIGH_RISK,
                    shouldBlock = true,
                    explanation = "High risk score (${scan.riskScore} > ${config.blockThreshold}) with verified parent link. App blocked and parent notified."
                )
            }
        } else {
            if (!user.hardBlockingEnabled) {
                return BlockDecision(
                    verdict = BlockVerdict.WARN_ONLY,
                    reasonType = BlockReasonType.WARN_ONLY_DISABLED_BY_USER,
                    shouldBlock = false,
                    explanation = "Hard blocking is disabled by user preference. Warn-only alert issued."
                )
            } else {
                return BlockDecision(
                    verdict = BlockVerdict.BLOCKED,
                    reasonType = BlockReasonType.BLOCKED_HIGH_RISK,
                    shouldBlock = true,
                    explanation = "High risk score (${scan.riskScore} > ${config.blockThreshold}) with confidence ${scan.confidence}%. App suspended via Device Owner."
                )
            }
        }
    }
}
