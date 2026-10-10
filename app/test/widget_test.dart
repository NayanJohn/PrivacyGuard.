import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:privacyguard/main.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('pg/native');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
      if (methodCall.method == 'getAccessStatus') {
        return <String, dynamic>{
          'usageAccess': false,
          'shizukuRunning': false,
          'shizukuPermission': false,
          'deviceOwner': false,
          'apiLevel': 34,
        };
      }
      if (methodCall.method == 'getBaseline') {
        return <String, dynamic>{
          'package': 'com.privacyguard.app',
          'granted': <String>['android.permission.INTERNET'],
          'notGranted': <String>['android.permission.CAMERA'],
          'targetSdkVersion': 34,
          'versionName': '1.0.0',
        };
      }
      if (methodCall.method == 'getAccessEvents') {
        return <Map<String, dynamic>>[
          <String, dynamic>{
            'op': 'OP_CAMERA',
            'packageName': 'com.android.chrome',
            'accessTimeMs': 1700000000000,
            'durationMs': 1500,
            'rejectTimeMs': 0,
            'accessCount': 1,
            'rejectCount': 0,
            'uidState': 'fg',
            'source': 'history',
          },
          <String, dynamic>{
            'op': 'OP_FINE_LOCATION',
            'packageName': 'com.android.chrome',
            'accessTimeMs': 1700000005000,
            'durationMs': 0,
            'rejectTimeMs': 0,
            'accessCount': 2,
            'rejectCount': 0,
            'uidState': 'top',
            'source': 'history',
          },
        ];
      }
      if (methodCall.method == 'runScan') {
        return <String, dynamic>{
          'scan': <String, dynamic>{
            'packageName': 'com.android.chrome',
            'riskScore': 85,
            'privacyHealth': 15,
            'confidence': 88,
            'status': 'COMPLETE',
            'confidenceReport': <String, dynamic>{
              'totalConfidence': 88,
              'components': <Map<String, dynamic>>[
                <String, dynamic>{
                  'name': 'Shizuku elevated binder status',
                  'scoreContribution': 35,
                  'detail': 'Active & authorized',
                },
              ],
            },
            'mismatches': <Map<String, dynamic>>[
              <String, dynamic>{
                'permissionOrOp': 'OP_CAMERA',
                'expectedState': 'FOREGROUND_ONLY',
                'observedState': 'BACKGROUND',
                'evidenceCount': 2,
                'severityScore': 150,
                'detail': 'Camera accessed in background 2 time(s)',
              },
            ],
            'totalMismatchesCount': 1,
          },
          'blockDecision': <String, dynamic>{
            'verdict': 'WARN_ONLY',
            'shouldBlock': false,
            'explanation': 'Risk score 85 is below threshold 90.',
          },
          'windowsCount': 3,
          'eventsCount': 5,
          'baselineGrantedCount': 12,
        };
      }
      if (methodCall.method == 'getAuditStatus') {
        return <String, dynamic>{
          'isPeriodicScheduled': true,
          'lastAuditCompletedMs': 1700000000000,
          'totalEventsCount': 14,
          'totalSessionsCount': 6,
          'totalScoresCount': 3,
        };
      }
      if (methodCall.method == 'getAuditHistory') {
        return <Map<String, dynamic>>[
          <String, dynamic>{
            'id': 1,
            'packageName': 'com.android.chrome',
            'computedAtMs': 1700000000000,
            'runtimeRisk': 0,
            'rawPenalty': 0,
            'breakdownJson': '[]',
          }
        ];
      }
      if (methodCall.method == 'getTimelineEvents') {
        return <Map<String, dynamic>>[
          <String, dynamic>{
            'id': 1,
            'packageName': 'com.android.chrome',
            'op': 'OP_CAMERA',
            'accessTimeMs': 1700000000000,
            'durationMs': 1200,
            'rejectTimeMs': 0,
            'state': 'FOREGROUND',
          }
        ];
      }
      if (methodCall.method == 'triggerAudit') {
        return <String, dynamic>{
          'eventsAdded': 2,
          'packagesAudited': 1,
          'timestamp': 1700000000000,
          'message': 'Audit completed: 1 packages audited, 2 new events saved',
        };
      }
      return null;
    });
  });


  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  testWidgets('MainAuditScreen displays tabs and inspects permission baseline', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(1080, 2400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    await tester.pumpWidget(const PrivacyGuardApp());
    await tester.pumpAndSettle();

    expect(find.text('PrivacyGuard Auditor'), findsOneWidget);
    expect(find.text('Lifecycle (P1)'), findsOneWidget);
    expect(find.text('Permissions (P2)'), findsOneWidget);
    expect(find.text('Access Events (P3)'), findsOneWidget);
    expect(find.text('Scoring (P4)'), findsOneWidget);

    // Switch to Permissions tab
    await tester.tap(find.text('Permissions (P2)'));
    await tester.pumpAndSettle();

    expect(find.text('Permission Baseline Inspector'), findsOneWidget);
    expect(find.text('Inspect Baseline'), findsOneWidget);

    // Tap Inspect Baseline button
    await tester.tap(find.widgetWithText(ElevatedButton, 'Inspect Baseline'));
    await tester.pumpAndSettle();

    expect(find.text('1 Granted'), findsOneWidget);
    expect(find.text('1 Revoked / Not Granted'), findsOneWidget);
    expect(find.text('INTERNET'), findsOneWidget);
    expect(find.text('CAMERA'), findsOneWidget);
  });

  testWidgets('MainAuditScreen displays Access Events (P3) tab and queries AppOps access events', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(1080, 2400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    await tester.pumpWidget(const PrivacyGuardApp());
    await tester.pumpAndSettle();

    // Switch to Access Events tab
    await tester.tap(find.text('Access Events (P3)'));
    await tester.pumpAndSettle();

    expect(find.text('AppOps Access Event Tracker'), findsOneWidget);
    expect(find.text('Query AppOps Events'), findsOneWidget);

    // Tap Query AppOps Events button
    await tester.tap(find.widgetWithText(ElevatedButton, 'Query AppOps Events'));
    await tester.pumpAndSettle();

    expect(find.text('2 Access Events Detected'), findsOneWidget);
    expect(find.text('OP_CAMERA'), findsOneWidget);
    expect(find.text('OP_FINE_LOCATION'), findsOneWidget);
    expect(find.text('1500ms'), findsOneWidget);
    expect(find.text('FG'), findsOneWidget);
    expect(find.text('TOP'), findsOneWidget);
  });

  testWidgets('MainAuditScreen displays Scoring (P4) tab and runs risk assessment audit', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(1080, 2400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    await tester.pumpWidget(const PrivacyGuardApp());
    await tester.pumpAndSettle();

    // Switch to Scoring (P4) tab
    await tester.tap(find.text('Scoring (P4)'));
    await tester.pumpAndSettle();

    expect(find.text('Risk Scoring & Policy Engine'), findsOneWidget);
    expect(find.text('Run Risk & Scoring Audit'), findsOneWidget);

    // Tap Run Risk & Scoring Audit button
    await tester.tap(find.widgetWithText(ElevatedButton, 'Run Risk & Scoring Audit'));
    await tester.pumpAndSettle();

    expect(find.text('85'), findsOneWidget);
    expect(find.text('Risk Score'), findsOneWidget);
    expect(find.text('Health: 15'), findsOneWidget);
    expect(find.text('88%'), findsOneWidget);
    expect(find.text('WARN_ONLY'), findsOneWidget);
    expect(find.text('OP_CAMERA'), findsOneWidget);
    expect(find.text('Severity 150'), findsOneWidget);
    expect(find.text('Camera accessed in background 2 time(s)'), findsOneWidget);
  });

  testWidgets('MainAuditScreen displays History (P5) tab and checks Room DB metrics', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(1200, 2400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() => tester.view.resetPhysicalSize());

    await tester.pumpWidget(const PrivacyGuardApp());
    await tester.pumpAndSettle();

    expect(find.text('History (P5)'), findsOneWidget);

    // Switch to History (P5) tab
    await tester.ensureVisible(find.text('History (P5)'));
    await tester.tap(find.text('History (P5)'));
    await tester.pumpAndSettle();

    expect(find.text('Background Audit Worker (WorkManager & Room)'), findsOneWidget);
    expect(find.text('SCHEDULED (15m)'), findsOneWidget);
    expect(find.text('14'), findsOneWidget); // Total Events
    expect(find.text('6'), findsOneWidget);  // Total Sessions
    expect(find.text('3'), findsOneWidget);  // Risk Scores
    expect(find.text('Run Audit Now'), findsOneWidget);
    expect(find.text('Cancel Worker'), findsOneWidget);
    expect(find.text('Privacy Health: 100 / 100'), findsOneWidget);
    expect(find.text('OP_CAMERA'), findsOneWidget);
    expect(find.text('FOREGROUND'), findsOneWidget);

    // Tap Run Audit Now button
    await tester.tap(find.widgetWithText(ElevatedButton, 'Run Audit Now'));
    await tester.pumpAndSettle();

    expect(find.text('Audit completed: 1 packages audited, 2 new events saved'), findsOneWidget);
  });
}



