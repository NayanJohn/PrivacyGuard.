package com.privacyguard.app

import com.privacyguard.app.bridge.NativeBridgeHandler
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    private var nativeBridgeHandler: NativeBridgeHandler? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        nativeBridgeHandler = NativeBridgeHandler(this, flutterEngine.dartExecutor.binaryMessenger)
    }
}
