package com.privacyguard.app.baseline

import android.content.pm.PackageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionBaselineTest {

    @Test
    fun testNullPermissionsArraysReturnEmptyLists() {
        val result = PermissionBaseline.parseRequestedPermissions(
            packageName = "com.test.app",
            requestedPermissions = null,
            requestedPermissionsFlags = null
        )

        assertEquals("com.test.app", result.packageName)
        assertTrue(result.granted.isEmpty())
        assertTrue(result.notGranted.isEmpty())
    }

    @Test
    fun testGrantedFlagParsing() {
        val perms = arrayOf("android.permission.CAMERA")
        val flags = intArrayOf(PackageInfo.REQUESTED_PERMISSION_GRANTED) // value 2

        val result = PermissionBaseline.parseRequestedPermissions(
            packageName = "com.test.app",
            requestedPermissions = perms,
            requestedPermissionsFlags = flags
        )

        assertEquals(1, result.granted.size)
        assertEquals("android.permission.CAMERA", result.granted[0])
        assertTrue(result.notGranted.isEmpty())
    }

    @Test
    fun testRevokedFlagParsing() {
        val perms = arrayOf("android.permission.RECORD_AUDIO")
        val flags = intArrayOf(0) // Not granted

        val result = PermissionBaseline.parseRequestedPermissions(
            packageName = "com.test.app",
            requestedPermissions = perms,
            requestedPermissionsFlags = flags
        )

        assertTrue(result.granted.isEmpty())
        assertEquals(1, result.notGranted.size)
        assertEquals("android.permission.RECORD_AUDIO", result.notGranted[0])
    }

    @Test
    fun testMixedDangerousPermissions() {
        val perms = arrayOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_SMS"
        )
        val flags = intArrayOf(
            PackageInfo.REQUESTED_PERMISSION_GRANTED, // Camera -> Granted
            0,                                         // Audio -> Revoked
            PackageInfo.REQUESTED_PERMISSION_GRANTED, // Fine Location -> Granted
            0,                                         // Contacts -> Revoked
            PackageInfo.REQUESTED_PERMISSION_GRANTED  // SMS -> Granted
        )

        val result = PermissionBaseline.parseRequestedPermissions(
            packageName = "com.test.dangerous",
            requestedPermissions = perms,
            requestedPermissionsFlags = flags
        )

        assertEquals(3, result.granted.size)
        assertEquals(
            listOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.CAMERA",
                "android.permission.READ_SMS"
            ),
            result.granted
        )

        assertEquals(2, result.notGranted.size)
        assertEquals(
            listOf(
                "android.permission.READ_CONTACTS",
                "android.permission.RECORD_AUDIO"
            ),
            result.notGranted
        )
    }

    @Test
    fun testMismatchedArrayLengthsSafelyHandled() {
        val perms = arrayOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO")
        val flags = intArrayOf(PackageInfo.REQUESTED_PERMISSION_GRANTED) // Only 1 flag

        val result = PermissionBaseline.parseRequestedPermissions(
            packageName = "com.test.mismatch",
            requestedPermissions = perms,
            requestedPermissionsFlags = flags
        )

        assertEquals(1, result.granted.size)
        assertEquals("android.permission.CAMERA", result.granted[0])
        assertTrue(result.notGranted.isEmpty())
    }
}
