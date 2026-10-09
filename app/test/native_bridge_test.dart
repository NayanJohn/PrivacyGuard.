import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:privacyguard/channel/native_bridge.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('pg/native');
  final List<MethodCall> log = <MethodCall>[];

  setUp(() {
    log.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
      log.add(methodCall);
      switch (methodCall.method) {
        case 'getAccessStatus':
          return <String, dynamic>{
            'usageAccess': false,
            'shizukuRunning': false,
            'shizukuPermission': false,
            'deviceOwner': false,
            'apiLevel': 34,
          };
        case 'openUsageAccessSettings':
          return null;
        case 'requestShizukuPermission':
          return false;
        case 'getForegroundWindows':
          return <Map<String, dynamic>>[];
        case 'getAccessEvents':
          return <Map<String, dynamic>>[];
        case 'getBaseline':
          return <String, dynamic>{'granted': <String>[], 'notGranted': <String>[]};
        case 'runScan':
          return <String, dynamic>{};
        case 'suspend':
          return <String, dynamic>{'ok': false, 'failedPackages': <String>[]};
        case 'unsuspend':
          return <String, dynamic>{'ok': true, 'failedPackages': <String>[]};
        case 'isSuspended':
          return false;
        case 'releaseControl':
          return <String, dynamic>{'ok': true};
        case 'syncBlocks':
          return <String, dynamic>{'applied': 0, 'failed': <String>[]};
        default:
          throw PlatformException(
            code: 'NOT_IMPLEMENTED',
            message: 'Method not implemented',
          );
      }
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('NativeBridge calls pg/native channel with correct method names and arguments', () async {
    final status = await NativeBridge.getAccessStatus();
    expect(status['apiLevel'], 34);

    await NativeBridge.openUsageAccessSettings();
    final shizukuGranted = await NativeBridge.requestShizukuPermission();
    expect(shizukuGranted, false);

    await NativeBridge.getForegroundWindows(pkg: 'com.example.test', startMs: 100, endMs: 200);
    await NativeBridge.getAccessEvents(pkg: 'com.example.test', sinceMs: 100);
    await NativeBridge.getBaseline('com.example.test');
    await NativeBridge.runScan('com.example.test');
    await NativeBridge.suspend('com.example.test');
    await NativeBridge.unsuspend('com.example.test');
    final suspended = await NativeBridge.isSuspended('com.example.test');
    expect(suspended, false);
    await NativeBridge.releaseControl(confirmation: 'CONFIRM');
    await NativeBridge.syncBlocks();

    expect(log.map((m) => m.method).toList(), [
      'getAccessStatus',
      'openUsageAccessSettings',
      'requestShizukuPermission',
      'getForegroundWindows',
      'getAccessEvents',
      'getBaseline',
      'runScan',
      'suspend',
      'unsuspend',
      'isSuspended',
      'releaseControl',
      'syncBlocks',
    ]);
  });
}
