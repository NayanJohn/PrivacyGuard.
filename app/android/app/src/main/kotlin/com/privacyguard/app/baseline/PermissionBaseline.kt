package com.privacyguard.app.baseline

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

data class PermissionBaselineResult(
    val packageName: String,
    val granted: List<String>,
    val notGranted: List<String>,
    val targetSdkVersion: Int = 0,
    val versionName: String? = null
) {
    fun toMap(): Map<String, Any> = mapOf(
        "package" to packageName,
        "granted" to granted,
        "notGranted" to notGranted,
        "targetSdkVersion" to targetSdkVersion,
        "versionName" to (versionName ?: "")
    )
}

class PermissionBaseline(private val context: Context) {

    companion object {
        /**
         * Pure parser: maps requested permissions and flags into GRANTED and NOT_GRANTED lists.
         * Tested against PackageInfo.REQUESTED_PERMISSION_GRANTED (value 2).
         */
        fun parseRequestedPermissions(
            packageName: String,
            requestedPermissions: Array<String>?,
            requestedPermissionsFlags: IntArray?,
            targetSdkVersion: Int = 0,
            versionName: String? = null
        ): PermissionBaselineResult {
            if (requestedPermissions == null || requestedPermissionsFlags == null) {
                return PermissionBaselineResult(
                    packageName = packageName,
                    granted = emptyList(),
                    notGranted = emptyList(),
                    targetSdkVersion = targetSdkVersion,
                    versionName = versionName
                )
            }

            val grantedList = mutableListOf<String>()
            val notGrantedList = mutableListOf<String>()

            val size = minOf(requestedPermissions.size, requestedPermissionsFlags.size)
            for (i in 0 until size) {
                val perm = requestedPermissions[i]
                val flag = requestedPermissionsFlags[i]
                val isGranted = (flag and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0

                if (isGranted) {
                    grantedList.add(perm)
                } else {
                    notGrantedList.add(perm)
                }
            }

            return PermissionBaselineResult(
                packageName = packageName,
                granted = grantedList.sorted(),
                notGranted = notGrantedList.sorted(),
                targetSdkVersion = targetSdkVersion,
                versionName = versionName
            )
        }
    }

    /**
     * FR-B1, FR-B2: Retrieve declared vs granted permissions for target package.
     * Throws PackageManager.NameNotFoundException if package is not installed.
     */
    fun getBaseline(packageName: String): PermissionBaselineResult {
        val pm = context.packageManager

        val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        }

        val targetSdk = packageInfo.applicationInfo?.targetSdkVersion ?: 0

        return parseRequestedPermissions(
            packageName = packageName,
            requestedPermissions = packageInfo.requestedPermissions,
            requestedPermissionsFlags = packageInfo.requestedPermissionsFlags,
            targetSdkVersion = targetSdk,
            versionName = packageInfo.versionName
        )
    }
}
