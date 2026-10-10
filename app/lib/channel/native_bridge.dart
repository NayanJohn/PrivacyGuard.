import 'package:flutter/services.dart';

/// MethodChannel bridge for PrivacyGuard native host communication.
/// Includes graceful fallback handling for desktop/development preview.
class NativeBridge {
  static const MethodChannel _channel = MethodChannel('pg/native');

  /// Fetches system capability and permission status.
  /// Returns map with keys: usageAccess, shizukuRunning, shizukuPermission, deviceOwner, apiLevel.
  static Future<Map<String, dynamic>> getAccessStatus() async {
    try {
      final result = await _channel.invokeMapMethod<String, dynamic>('getAccessStatus');
      return result ?? <String, dynamic>{};
    } on MissingPluginException {
      return <String, dynamic>{
        'usageAccess': true,
        'shizukuRunning': true,
        'shizukuPermission': true,
        'deviceOwner': false,
        'apiLevel': 37,
        'platform': 'windows_desktop',
      };
    }
  }

  /// Launches Android Usage Access Settings screen.
  static Future<void> openUsageAccessSettings() async {
    try {
      await _channel.invokeMethod<void>('openUsageAccessSettings');
    } on MissingPluginException {
      // No-op on Windows desktop
    }
  }

  /// Requests Shizuku elevated permission.
  static Future<bool> requestShizukuPermission() async {
    try {
      final result = await _channel.invokeMethod<bool>('requestShizukuPermission');
      return result ?? false;
    } on MissingPluginException {
      return true;
    }
  }

  /// Retrieves lifecycle foreground sessions for [pkg] within [[startMs], [endMs]].
  static Future<List<Map<String, dynamic>>> getForegroundWindows({
    required String pkg,
    required int startMs,
    required int endMs,
  }) async {
    try {
      final result = await _channel.invokeMethod<List<dynamic>>(
        'getForegroundWindows',
        {'pkg': pkg, 'startMs': startMs, 'endMs': endMs},
      );
      return result?.map((e) => Map<String, dynamic>.from(e as Map)).toList() ?? <Map<String, dynamic>>[];
    } on MissingPluginException {
      final now = DateTime.now().millisecondsSinceEpoch;
      return <Map<String, dynamic>>[
        {
          'packageName': pkg,
          'startMs': now - 45000,
          'endMs': now - 5000,
          'durationMs': 40000,
        },
      ];
    }
  }

  /// Retrieves raw AppOps access events for [pkg] since [sinceMs].
  static Future<List<Map<String, dynamic>>> getAccessEvents({
    required String pkg,
    required int sinceMs,
  }) async {
    try {
      final result = await _channel.invokeMethod<List<dynamic>>(
        'getAccessEvents',
        {'pkg': pkg, 'sinceMs': sinceMs},
      );
      return result?.map((e) => Map<String, dynamic>.from(e as Map)).toList() ?? <Map<String, dynamic>>[];
    } on MissingPluginException {
      final now = DateTime.now().millisecondsSinceEpoch;
      return <Map<String, dynamic>>[
        {
          'packageName': pkg,
          'op': 'OP_FINE_LOCATION',
          'accessTimeMs': now - 120000,
          'durationMs': 2289,
          'rejectTimeMs': 0,
          'state': 'BACKGROUND',
        },
        {
          'packageName': pkg,
          'op': 'OP_CAMERA',
          'accessTimeMs': now - 180000,
          'durationMs': 3230,
          'rejectTimeMs': 0,
          'state': 'BACKGROUND',
        },
      ];
    }
  }

  /// Retrieves declared vs granted permission baseline for [pkg].
  static Future<Map<String, dynamic>> getBaseline(String pkg) async {
    try {
      final result = await _channel.invokeMapMethod<String, dynamic>(
        'getBaseline',
        {'pkg': pkg},
      );
      return result ?? <String, dynamic>{};
    } on MissingPluginException {
      return <String, dynamic>{
        'packageName': pkg,
        'granted': <String>[
          'android.permission.INTERNET',
          'android.permission.ACCESS_NETWORK_STATE',
          'android.permission.ACCESS_FINE_LOCATION',
          'android.permission.CAMERA',
        ],
        'notGranted': <String>[
          'android.permission.RECORD_AUDIO',
          'android.permission.READ_CONTACTS',
        ],
      };
    }
  }

  /// Triggers a local audit scan and risk scoring for [pkg].
  static Future<Map<String, dynamic>> runScan(
    String pkg, {
    int? windowMs,
    int? userAge,
    bool? hasParentLink,
    bool? hardBlockingEnabled,
  }) async {
    try {
      final args = <String, dynamic>{'pkg': pkg};
      if (windowMs != null) args['windowMs'] = windowMs;
      if (userAge != null) args['userAge'] = userAge;
      if (hasParentLink != null) args['hasParentLink'] = hasParentLink;
      if (hardBlockingEnabled != null) args['hardBlockingEnabled'] = hardBlockingEnabled;

      final result = await _channel.invokeMapMethod<String, dynamic>(
        'runScan',
        args,
      );
      return result ?? <String, dynamic>{};
    } on MissingPluginException {
      return <String, dynamic>{
        'packageName': pkg,
        'riskScore': 0,
        'privacyHealth': 100,
        'confidence': 100,
        'confidenceLabel': 'COMPLETE',
        'isBlockable': false,
        'blockVerdict': 'SAFE',
        'topMismatches': <Map<String, dynamic>>[],
        'mode': 'WINDOWS_DESKTOP',
      };
    }
  }

  /// Requests suspension of [pkg] via DevicePolicyManager.
  static Future<Map<String, dynamic>> suspend(String pkg) async {
    try {
      final result = await _channel.invokeMapMethod<String, dynamic>(
        'suspend',
        {'pkg': pkg},
      );
      return result ?? <String, dynamic>{};
    } on MissingPluginException {
      return <String, dynamic>{'ok': false, 'reason': 'Unsupported on Windows'};
    }
  }

  /// Requests un-suspension of [pkg] via DevicePolicyManager.
  static Future<Map<String, dynamic>> unsuspend(String pkg) async {
    try {
      final result = await _channel.invokeMapMethod<String, dynamic>(
        'unsuspend',
        {'pkg': pkg},
      );
      return result ?? <String, dynamic>{};
    } on MissingPluginException {
      return <String, dynamic>{'ok': true};
    }
  }

  /// Checks if [pkg] is suspended.
  static Future<bool> isSuspended(String pkg) async {
    try {
      final result = await _channel.invokeMethod<bool>(
        'isSuspended',
        {'pkg': pkg},
      );
      return result ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  /// Un-suspends all apps and clears Device Owner status.
  static Future<Map<String, dynamic>> releaseControl({String? confirmation}) async {
    try {
      final result = await _channel.invokeMapMethod<String, dynamic>(
        'releaseControl',
        {'confirmation': confirmation},
      );
      return result ?? <String, dynamic>{};
    } on MissingPluginException {
      return <String, dynamic>{'ok': true};
    }
  }

  /// Synchronizes device suspension state with backend GET /blocks.
  static Future<Map<String, dynamic>> syncBlocks() async {
    try {
      final result = await _channel.invokeMapMethod<String, dynamic>('syncBlocks');
      return result ?? <String, dynamic>{};
    } on MissingPluginException {
      return <String, dynamic>{'applied': 0, 'failed': <String>[]};
    }
  }

  /// Triggers an immediate background audit cycle and Room persistence.
  static Future<Map<String, dynamic>> triggerAudit({List<String>? packages}) async {
    try {
      final args = <String, dynamic>{};
      if (packages != null) args['packages'] = packages;
      final result = await _channel.invokeMapMethod<String, dynamic>('triggerAudit', args);
      return result ?? <String, dynamic>{};
    } on MissingPluginException {
      return <String, dynamic>{
        'eventsAdded': 0,
        'packagesAudited': packages?.length ?? 1,
        'timestamp': DateTime.now().millisecondsSinceEpoch,
        'message': 'Windows Desktop Audit Complete',
      };
    }
  }

  /// Retrieves background audit worker and Room database persistence status.
  static Future<Map<String, dynamic>> getAuditStatus() async {
    try {
      final result = await _channel.invokeMapMethod<String, dynamic>('getAuditStatus');
      return result ?? <String, dynamic>{};
    } on MissingPluginException {
      return <String, dynamic>{
        'isPeriodicScheduled': true,
        'lastAuditCompletedMs': DateTime.now().millisecondsSinceEpoch - 180000,
        'totalEventsCount': 17,
        'totalSessionsCount': 28,
        'totalScoresCount': 4,
      };
    }
  }

  /// Schedules periodic background auditing via WorkManager.
  static Future<bool> schedulePeriodicAudit({int intervalMinutes = 15}) async {
    try {
      final result = await _channel.invokeMethod<bool>(
        'schedulePeriodicAudit',
        {'intervalMinutes': intervalMinutes},
      );
      return result ?? false;
    } on MissingPluginException {
      return true;
    }
  }

  /// Cancels periodic background auditing.
  static Future<bool> cancelPeriodicAudit() async {
    try {
      final result = await _channel.invokeMethod<bool>('cancelPeriodicAudit');
      return result ?? false;
    } on MissingPluginException {
      return true;
    }
  }

  /// Retrieves historical risk scores stored in Room DB for [pkg].
  static Future<List<Map<String, dynamic>>> getAuditHistory(String pkg, {int limit = 50}) async {
    try {
      final result = await _channel.invokeMethod<List<dynamic>>(
        'getAuditHistory',
        {'pkg': pkg, 'limit': limit},
      );
      return result?.map((e) => Map<String, dynamic>.from(e as Map)).toList() ?? <Map<String, dynamic>>[];
    } on MissingPluginException {
      final now = DateTime.now().millisecondsSinceEpoch;
      return <Map<String, dynamic>>[
        {
          'id': 2,
          'packageName': pkg,
          'computedAtMs': now - 180000,
          'runtimeRisk': 0,
          'rawPenalty': 0,
          'breakdownJson': '[]',
        },
        {
          'id': 1,
          'packageName': pkg,
          'computedAtMs': now - 540000,
          'runtimeRisk': 100,
          'rawPenalty': 100,
          'breakdownJson': '[]',
        },
      ];
    }
  }

  /// Retrieves historical AppOps access events stored in Room DB.
  static Future<List<Map<String, dynamic>>> getTimelineEvents({String? pkg, int limit = 100}) async {
    try {
      final args = <String, dynamic>{'limit': limit};
      if (pkg != null && pkg.isNotEmpty) args['pkg'] = pkg;
      final result = await _channel.invokeMethod<List<dynamic>>(
        'getTimelineEvents',
        args,
      );
      return result?.map((e) => Map<String, dynamic>.from(e as Map)).toList() ?? <Map<String, dynamic>>[];
    } on MissingPluginException {
      final now = DateTime.now().millisecondsSinceEpoch;
      final targetPkg = pkg ?? 'com.android.chrome';
      return <Map<String, dynamic>>[
        {
          'id': 1,
          'packageName': targetPkg,
          'op': 'OP_FINE_LOCATION',
          'accessTimeMs': now - 180000,
          'durationMs': 2289,
          'rejectTimeMs': 0,
          'state': 'BACKGROUND',
        },
        {
          'id': 2,
          'packageName': targetPkg,
          'op': 'OP_CAMERA',
          'accessTimeMs': now - 240000,
          'durationMs': 3230,
          'rejectTimeMs': 0,
          'state': 'BACKGROUND',
        },
      ];
    }
  }
}
