package com.privacyguard.app.bridge

import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class NativeBridgeHandler(messenger: BinaryMessenger) : MethodChannel.MethodCallHandler {
    companion object {
        const val CHANNEL_NAME = "pg/native"
    }

    private val channel = MethodChannel(messenger, CHANNEL_NAME)

    init {
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getAccessStatus",
            "openUsageAccessSettings",
            "requestShizukuPermission",
            "getForegroundWindows",
            "getAccessEvents",
            "getBaseline",
            "runScan",
            "suspend",
            "unsuspend",
            "isSuspended",
            "releaseControl",
            "syncBlocks" -> {
                result.error(
                    "NOT_IMPLEMENTED",
                    "Method '${call.method}' is specified in pg/native contract but not yet implemented in Phase P0",
                    null
                )
            }
            else -> {
                result.notImplemented()
            }
        }
    }
}
