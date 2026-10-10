package com.privacyguard.app.scoring

object NeverBlockList {
    private val PROTECTED_PREFIXES = listOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.privacyguard.app",
        "moe.shizuku.privileged.api"
    )

    fun isNeverBlock(
        packageName: String,
        isDefaultDialer: Boolean = false,
        isDefaultSms: Boolean = false,
        isCurrentLauncher: Boolean = false,
        isEmergencyApp: Boolean = false,
        isSystemPackage: Boolean = false
    ): Boolean {
        if (packageName == "com.privacyguard.app") return true
        if (isDefaultDialer || isDefaultSms || isCurrentLauncher || isEmergencyApp || isSystemPackage) return true
        return PROTECTED_PREFIXES.any { packageName == it || packageName.startsWith("$it.") }
    }
}
