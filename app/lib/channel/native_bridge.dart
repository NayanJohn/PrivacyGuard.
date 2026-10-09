import 'package:flutter/services.dart';

/// MethodChannel bridge for PrivacyGuard native host communication.
class NativeBridge {
  static const MethodChannel _channel = MethodChannel('pg/native');

  /// Fetches system capability and permission status.
  /// Returns map with keys: usageAccess, shizukuRunning, shizukuPermission, deviceOwner, apiLevel.
  static Future<Map<String, dynamic>> getAccessStatus() async {
    final result = await _channel.invokeMapMethod<String, dynamic>('getAccessStatus');
    return result ?? <String, dynamic>{};
  }

  /// Launches Android Usage Access Settings screen.
  static Future<void> openUsageAccessSettings() async {
    await _channel.invokeMethod<void>('openUsageAccessSettings');
  }

  /// Requests Shizuku elevated permission.
  static Future<bool> requestShizukuPermission() async {
    final result = await _channel.invokeMethod<bool>('requestShizukuPermission');
    return result ?? false;
  }

  /// Retrieves lifecycle foreground sessions for [pkg] within [[startMs], [endMs]].
  static Future<List<Map<String, dynamic>>> getForegroundWindows({
    required String pkg,
    required int startMs,
    required int endMs,
  }) async {
    final result = await _channel.invokeListMethod<Map<String, dynamic>>(
      'getForegroundWindows',
      {'pkg': pkg, 'startMs': startMs, 'endMs': endMs},
    );
    return result ?? <Map<String, dynamic>>[];
  }

  /// Retrieves raw AppOps access events for [pkg] since [sinceMs].
  static Future<List<Map<String, dynamic>>> getAccessEvents({
    required String pkg,
    required int sinceMs,
  }) async {
    final result = await _channel.invokeListMethod<Map<String, dynamic>>(
      'getAccessEvents',
      {'pkg': pkg, 'sinceMs': sinceMs},
    );
    return result ?? <Map<String, dynamic>>[];
  }

  /// Retrieves declared vs granted permission baseline for [pkg].
  static Future<Map<String, dynamic>> getBaseline(String pkg) async {
    final result = await _channel.invokeMapMethod<String, dynamic>(
      'getBaseline',
      {'pkg': pkg},
    );
    return result ?? <String, dynamic>{};
  }

  /// Triggers a local audit scan for [pkg].
  static Future<Map<String, dynamic>> runScan(String pkg) async {
    final result = await _channel.invokeMapMethod<String, dynamic>(
      'runScan',
      {'pkg': pkg},
    );
    return result ?? <String, dynamic>{};
  }

  /// Requests suspension of [pkg] via DevicePolicyManager.
  static Future<Map<String, dynamic>> suspend(String pkg) async {
    final result = await _channel.invokeMapMethod<String, dynamic>(
      'suspend',
      {'pkg': pkg},
    );
    return result ?? <String, dynamic>{};
  }

  /// Requests un-suspension of [pkg] via DevicePolicyManager.
  static Future<Map<String, dynamic>> unsuspend(String pkg) async {
    final result = await _channel.invokeMapMethod<String, dynamic>(
      'unsuspend',
      {'pkg': pkg},
    );
    return result ?? <String, dynamic>{};
  }

  /// Checks if [pkg] is suspended.
  static Future<bool> isSuspended(String pkg) async {
    final result = await _channel.invokeMethod<bool>(
      'isSuspended',
      {'pkg': pkg},
    );
    return result ?? false;
  }

  /// Un-suspends all apps and clears Device Owner status.
  static Future<Map<String, dynamic>> releaseControl({String? confirmation}) async {
    final result = await _channel.invokeMapMethod<String, dynamic>(
      'releaseControl',
      {'confirmation': confirmation},
    );
    return result ?? <String, dynamic>{};
  }

  /// Synchronizes device suspension state with backend GET /blocks.
  static Future<Map<String, dynamic>> syncBlocks() async {
    final result = await _channel.invokeMapMethod<String, dynamic>('syncBlocks');
    return result ?? <String, dynamic>{};
  }
}
