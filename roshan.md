# roshan.md — PrivacyGuard App Build Log (Antigravity)

**Scope: this file tracks only the work of creating the PrivacyGuard app inside Antigravity** — code, config, builds, tests, device verification, and the decisions/issues that come up while building. It is not for writing documentation or planning notes. The Antigravity agent updates it **after every build task, before starting the next one**.

Entries are append-only (never rewrite history). Only the **Status board**, **Next action** and **Open questions** are edited in place.

**Honesty rules**
- Log only what actually happened. No invented results, numbers or "passed" claims.
- "Verified" means it was run on a real device/emulator/backend and the output is pasted or referenced. Otherwise write **NOT VERIFIED** and why.
- Record failures and dead ends too.

---

## 1. Status board (edit in place)

| Phase | Scope | Status | Last updated | Evidence |
|-------|-------|--------|--------------|----------|
| P0 | Foundations: repo, Flutter+Kotlin skeleton, channel, CI, backend `/health`, API verification | Done (verified) | 2026-10-10 | Flutter analyze (0 issues) + test (2 passed); backend pytest (2 passed on real PostgreSQL); Alembic migration on PostgreSQL; emulator-5554 APK launch & getAccessStatus verified |
| P1 | Lifecycle tracking (UsageStats) | Done (verified) | 2026-10-10 | UsageTrackerTest 8 passed; flutter analyze 0 issues, flutter test 2 passed; emulator-5554 (API 37) real query: Settings opened 5s -> window 6.5s (05:09:25-05:09:32) |
| P2 | Permission baseline | Done (verified) | 2026-10-10 | PermissionBaselineTest 5 passed (13 total Kotlin tests); flutter analyze 0 issues, flutter test 2 passed; emulator-5554 (API 37) real verification on 3 apps (PrivacyGuard, Settings [155 granted, 7 revoked], Chrome [42 granted, 19 revoked]); dynamic grant/revoke test verified immediately updating counts and lists |
| P3 | AppOps access tracking via Shizuku | Done (verified) | 2026-10-10 | AppOpsParserTest 7 passed (21 total Kotlin tests); flutter analyze 0 issues, flutter test 3 passed; Shizuku elevated binder integrated and authorized on emulator-5554 (API 37); real live hardware ops verified on Chrome (CAMERA 3230ms, FINE_LOCATION 2289ms, RECORD_AUDIO rejected) & Settings (6 events); screenshots p3_events_verified.png, p3_settings_verified.png |
| P4 | Scoring engine + confidence + mismatches | Done (verified) | 2026-10-10 | ScoringEngineTest (13 passed), BlockPolicyTest (8 passed), total 42 Kotlin unit tests passed; Flutter analyze 0 issues, Flutter test 4 passed; emulator-5554 (API 37) real verification on Chrome (0 risk score, 100 health, 100% confidence, 0 mismatches, verdict SAFE) and Settings (0 risk score, 100 health, protected by NeverBlockList -> verdict NOT_BLOCKABLE); screenshots p4_chrome_recalibrated.png, p4_settings_recalibrated.png |
| P5 | Background audit + history + Flutter timelines | Done (verified) | 2026-10-10 | Room schema v1 committed (6 tables matching DATABASE_SCHEMA.md §2); AuditDeduplicationTest 4 passed, total 46 Kotlin tests passed; Flutter analyze 0 issues, Flutter test 5 passed; WorkManager PeriodicWork scheduled (15m) + BootReceiver registered; live emulator-5554 (API 37) real audit cycle executed, deduplication active, Room SQLite populated (17 events, 28 sessions, 4 scores), score evolution & timelines verified; screenshots p5_tab.png, p5_timeline.png, p5_scores_evolution.png |
| P6 | Backend: scans, blocks, allow/block, alerts, parent email/portal, release codes | Not started | – | – |
| P7 | Hard blocking (Device Owner) + warn-only fallback + Release control | Not started | – | – |
| P8 | Hardening, UI to DESIGN.md, acceptance checklist | Not started | – | – |

Status values: `Not started` · `In progress` · `Blocked` · `Done (verified)` · `Done (NOT VERIFIED)`

## 2. Next action (edit in place)

Phase P5 complete and verified. Proceed with Phase P6 (Backend: scans, blocks, allow/block, alerts, parent email/portal, release codes) — Scan engine decision pending (A simplified / B existing backend / C runtime_risk only) per user instruction.

## 3. Open questions for the owner (edit in place)

| # | Question | Default proposed | Status |
|---|----------|------------------|--------|
| 1 | P4 confidence components and weights proposal | Define 5 components (Shizuku status, Usage access, polling coverage, sample event count, API source level) | Resolved (approved & verified in P4) |
| 2 | P6 scan engine strategy (A simplified / B existing backend / C runtime_risk only) | Decision deferred to P6 per user instruction | Pending P6 |

## 4. Entry format (copy for every new entry)

```
### [NNNN] YYYY-MM-DD HH:MM — <short title>
Phase: P?   Type: code | config | build | test | verify | decision | issue
Antigravity: <agent/session name> · artifact: <plan / walkthrough / screenshot / recording reference, or "none">
Files touched: <paths or "none">
What I did: <1–5 lines, factual>
Commands run: <exact commands, or "none">
Result: <what happened, including errors>
Verified: YES (<device/emulator + Android version + evidence>) | NOT VERIFIED (<reason>)
API/doc differences found: <list or "none">
Next: <the single next step>
```

---

## 5. Build log (append-only, newest at the bottom)

### [0001] 2026-10-10 02:35 — P0 Environment Check and Tooling Audit
Phase: P0   Type: verify
Antigravity: f2994e3c-6531-4e6c-a9b8-0978dc04e08d · artifact: none
Files touched: none
What I did: Audited Flutter, Android SDK, JDK, and build tool environments against project requirements.
Commands run: `flutter doctor -v`, `adb version`, `python --version`, `java -version`
Result: Flutter 3.41.6 stable (Dart 3.11.4), Android SDK 36.1.0 at `C:\Android\Sdk`, platform `android-36.1`, JDK 17 (Java HotSpot 17.0.12 LTS), Python 3.14.0. No adb devices currently attached.
Verified: YES (Local development environment tools inspected with direct command execution)
API/doc differences found: none
Next: Scaffold Flutter client with applicationId `com.privacyguard.app` and MethodChannel bridge.

### [0002] 2026-10-10 02:40 — P0 Flutter Skeleton and NativeBridge Scaffolding
Phase: P0   Type: code
Antigravity: f2994e3c-6531-4e6c-a9b8-0978dc04e08d · artifact: none
Files touched: `app/android/app/build.gradle.kts`, `app/android/app/src/main/kotlin/com/privacyguard/app/MainActivity.kt`, `app/android/app/src/main/kotlin/com/privacyguard/app/bridge/NativeBridgeHandler.kt`, `app/lib/channel/native_bridge.dart`, `app/test/native_bridge_test.dart`
What I did: Created Flutter project in `app/`, configured `applicationId` and `namespace` to `com.privacyguard.app` with `minSdk = 26`, implemented Kotlin `NativeBridgeHandler` on `pg/native` with all 12 contract methods returning explicit NOT_IMPLEMENTED errors, created typed Flutter `NativeBridge`, and verified with unit tests.
Commands run: `flutter create --org com.privacyguard -a kotlin --project-name privacyguard app`, `flutter analyze`, `flutter test`
Result: `flutter analyze` passed with 0 issues. `flutter test` passed all 2 unit tests (smoke test and native bridge method contract invocation test).
Verified: YES (Flutter test suite executed: `All tests passed!`)
API/doc differences found: none
Next: Create FastAPI backend skeleton with config module, models, Alembic, and `/health` test.

### [0003] 2026-10-10 02:45 — P0 Backend Skeleton, Config, Models, Alembic and Health Test
Phase: P0   Type: code
Antigravity: f2994e3c-6531-4e6c-a9b8-0978dc04e08d · artifact: none
Files touched: `backend/app/config.py`, `backend/app/database.py`, `backend/app/models.py`, `backend/app/main.py`, `backend/alembic/env.py`, `backend/alembic/versions/7514c20f3712_create_initial_schema.py`, `backend/tests/test_health.py`, `backend/tests/test_config.py`, `backend/pytest.ini`, `backend/requirements.txt`
What I did: Set up dedicated Python `.venv` in `backend/`, installed FastAPI, SQLAlchemy, Alembic, Pydantic, and Pytest. Created config module (`BLOCK_THRESHOLD=90`, `BLOCK_MIN_CONFIDENCE=70`, weights, multipliers, TTLs, never-block list), SQLAlchemy models matching `DATABASE_SCHEMA.md` exactly, Alembic initial migration, `GET /health` endpoint with DB connectivity check, and unit test suite.
Commands run: `.\.venv\Scripts\alembic.exe revision --autogenerate -m "create_initial_schema"`, `.\.venv\Scripts\alembic.exe upgrade head`, `.\.venv\Scripts\pytest.exe`
Result: Alembic detected all 11 tables and applied migration `7514c20f3712`. Pytest passed 2/2 tests in 0.97s (`test_health_endpoint` returned 200 with status=ok and database=reachable; `test_config_contract` confirmed all bounds).
Verified: YES (Backend test suite executed with real SQLite database connection)
API/doc differences found: none
Next: Audit real Android SDK API signatures in `android-36.1` and write `docs/api_verification.md`.

### [0004] 2026-10-10 02:51 — P0 Android & Shizuku API Signatures Verification
Phase: P0   Type: verify
Antigravity: f2994e3c-6531-4e6c-a9b8-0978dc04e08d · artifact: `docs/api_verification.md`
Files touched: `docs/api_verification.md`
What I did: Used `javap` to inspect compiled bytecode and constants of `android.jar` in `C:\Android\Sdk\platforms\android-36.1` for all target APIs: `UsageEvents$Event`, `UsageStatsManager`, `PackageManager`, `PackageInfo`, `AppOpsManager`, `DevicePolicyManager`, and Shizuku library signatures. Documented signatures, min SDK, and discrepancies in `docs/api_verification.md`.
Commands run: `javap -constants -cp C:\Android\Sdk\platforms\android-36.1\android.jar ...` on target classes.
Result: Verified exact constants (`ACTIVITY_RESUMED=1`, `ACTIVITY_STOPPED=23`, `MOVE_TO_FOREGROUND=1`, `MOVE_TO_BACKGROUND=2`, `REQUESTED_PERMISSION_GRANTED=2`). Identified that `isPackageSuspended` throws `PackageManager.NameNotFoundException`, `SHOW_SUSPENDED_APP_DETAILS` is a system intent action not exposed in public constants, and `clearDeviceOwnerApp` has test-only restrictions in modern AOSP.
Verified: YES (Real Android SDK `android-36.1` bytecode inspected via `javap`)
API/doc differences found:
1. `SHOW_SUSPENDED_APP_DETAILS` is a system intent string (`"android.intent.action.SHOW_SUSPENDED_APP_DETAILS"`), not a public constant in `Intent.java`.
2. `DevicePolicyManager.isPackageSuspended` throws `PackageManager.NameNotFoundException`.
3. `AppOpsManager.getPackagesForOps` is hidden/SystemApi; must run inside Shizuku's privileged process.
Next: Present Phase P0 completion report and await user go-ahead for Phase P1.

### [0005] 2026-10-10 03:15 — Build Status Audit (Read-Only)
Phase: P0   Type: verify
Antigravity: f2994e3c-6531-4e6c-a9b8-0978dc04e08d · artifact: none
Files touched: none
What I did: Executed comprehensive read-only build status audit across all workspace modules (Checks A through I) per user audit request without altering source code.
Commands run: Check A (`Measure-Object`, `git log`, `git status`), Check B (`flutter pub get`, `flutter analyze`, `flutter test`, `flutter build apk --debug`), Check C (`gradlew assembleDebug`, `gradlew test`), Check D (`alembic upgrade head`, `pytest`, `GET /health`), Check E (`npm install`, `npm run build`), Check F (`gradlew assembleDebug` in probe-app), Check G (`adb devices`), Check H (honesty grep scan), Check I (phase status inspection).
Result: Final Verdict: PARTIAL. P0 foundations are fully built and verified: debug APK produced at `app/build/app/outputs/flutter-apk/app-debug.apk` (144.6 MB), Flutter analyzer 0 issues, Flutter unit tests 2/2 pass, Android gradlew assembleDebug & test pass, FastAPI /health returns 200 OK (status: ok, database: reachable), pytest 2/2 pass. Failures: portal/ and probe-app/ are not yet populated (P6/P8 and P1/P3 scope); local PostgreSQL authentication failed due to unconfigured password; no ADB device connected (Check G NOT RUN).
Verified: YES (All commands executed on Windows PowerShell workspace; raw output inspected)
API/doc differences found: none
Next: Start Phase P1 (Lifecycle tracking).

### [0006] 2026-10-10 04:50 — P0 Verification on PostgreSQL and Android Emulator
Phase: P0   Type: verify
Antigravity: f2994e3c-6531-4e6c-a9b8-0978dc04e08d · artifact: none
Files touched: `backend/.env`, `app/lib/main.dart`
What I did: Configured real PostgreSQL connection (`DATABASE_URL=postgresql+psycopg2://postgres:postgres@localhost:5432/privacyguard`), ran `alembic upgrade head` and `pytest`. Installed `app-debug.apk` onto booted `emulator-5554` (API 37), launched `MainActivity`, observed display screen with `getAccessStatus` MethodChannel call returning explicit `PlatformException: [NOT_IMPLEMENTED]`, confirmed via logcat and screenshot.
Commands run: `.\.venv\Scripts\alembic.exe upgrade head`, `.\.venv\Scripts\pytest.exe -v`, `adb install -r build\app\outputs\flutter-apk\app-debug.apk`, `adb shell am force-stop com.privacyguard.app; adb logcat -c; adb shell am start -n com.privacyguard.app/.MainActivity`, `adb logcat -d -s flutter`, `adb shell screencap -p /sdcard/screen.png; adb pull /sdcard/screen.png ...`
Result: Alembic applied initial migration to real PostgreSQL (`7514c20f3712`), pytest passed 2/2 tests. APK installed and launched successfully on `emulator-5554`. App displayed screen with title "PrivacyGuard Foundations" and status "PlatformException: [NOT_IMPLEMENTED] Method 'getAccessStatus' is specified in pg/native contract but not yet implemented in Phase P0". Logcat confirmed `PG_NATIVE_CHANNEL_RESULT`.
Verified: YES (Real PostgreSQL database `privacyguard` on port 5432 + Android emulator-5554 running Android API 37)
API/doc differences found: none
Next: Execute Phase P1 (Lifecycle tracking).

### [0007] 2026-10-10 05:13 — P1 Lifecycle Tracking Implementation and Verification
Phase: P1   Type: code
Antigravity: f2994e3c-6531-4e6c-a9b8-0978dc04e08d · artifact: none
Files touched: `app/android/app/src/main/AndroidManifest.xml`, `app/android/app/build.gradle.kts`, `app/android/app/src/main/kotlin/com/privacyguard/app/tracking/UsageTracker.kt`, `app/android/app/src/test/kotlin/com/privacyguard/app/tracking/UsageTrackerTest.kt`, `app/android/app/src/main/kotlin/com/privacyguard/app/MainActivity.kt`, `app/android/app/src/main/kotlin/com/privacyguard/app/bridge/NativeBridgeHandler.kt`, `app/lib/channel/native_bridge.dart`, `app/lib/main.dart`, `app/test/widget_test.dart`
What I did: Implemented `UsageTracker` on Android (`hasUsageAccess` via `AppOpsManager.OPSTR_GET_USAGE_STATS`, `openUsageAccessSettings` intent, `queryEvents` iteration, pure window builder pairing `ACTIVITY_RESUMED` [1] and `ACTIVITY_STOPPED` [23] with API 26-28 fallback to `MOVE_TO_FOREGROUND` [1] and `MOVE_TO_BACKGROUND` [2], boundary trailing closure, and activity switch overlap merge). Wired `getAccessStatus`, `openUsageAccessSettings`, and `getForegroundWindows` into `NativeBridgeHandler` and Flutter `NativeBridge`. Built Flutter `LifecycleAuditScreen` with honest empty states and status indicators. Tested Kotlin unit tests (8/8 passed) and Flutter tests (2/2 passed). Deployed debug APK to `emulator-5554` (API 37).
Commands run: `.\gradlew.bat test`, `flutter analyze`, `flutter test`, `flutter build apk --debug`, `adb install -r build\app\outputs\flutter-apk\app-debug.apk`, `adb shell appops set com.privacyguard.app GET_USAGE_STATS allow`, manual launch test: opened `com.android.settings` for 5 seconds and queried foreground windows.
Result: Real lifecycle session extracted for `com.android.settings`: duration 6.5s from `2026-10-10 05:09:25` to `2026-10-10 05:09:32`, accurately matching manual execution. All 8 Kotlin unit tests passed; Flutter analyze 0 issues; Flutter tests 2 passed.
Verified: YES (Android emulator-5554 running Android API 37, x86_64; screenshots `p1_before_grant.png`, `p1_granted.png`, `p1_settings_success.png`)
API/doc differences found: none
Next: Await user approval to proceed with Phase P2 (Permission baseline).

### [0008] 2026-10-10 05:40 — P2 Permission Baseline Implementation and Dynamic Verification
Phase: P2   Type: code
Antigravity: f2994e3c-6531-4e6c-a9b8-0978dc04e08d · artifact: none
Files touched: `app/android/app/src/main/kotlin/com/privacyguard/app/baseline/PermissionBaseline.kt`, `app/android/app/src/test/kotlin/com/privacyguard/app/baseline/PermissionBaselineTest.kt`, `app/android/app/src/main/kotlin/com/privacyguard/app/bridge/NativeBridgeHandler.kt`, `app/lib/channel/native_bridge.dart`, `app/lib/main.dart`, `app/test/widget_test.dart`
What I did: Implemented `PermissionBaseline` on Android utilizing `PackageManager.getPackageInfo(pkg, GET_PERMISSIONS)` with API 33+ adaptive flags (`PackageInfoFlags.of`). Implemented pure parser `parseRequestedPermissions` validating against `PackageInfo.REQUESTED_PERMISSION_GRANTED` (bit 2). Wired `getBaseline` into `NativeBridgeHandler` with `PACKAGE_NOT_FOUND` error mapping and Flutter `NativeBridge`. Built 2-tab Flutter UI with quick package chips, granted/revoked summaries, and detailed permission tiles. Tested Kotlin unit tests (5/5 passed, 13/13 total Kotlin tests), Flutter analyzer (0 issues), Flutter unit tests (2/2 passed). Deployed to emulator-5554 (API 37).
Commands run: `.\gradlew.bat test`, `flutter analyze`, `flutter test`, `flutter build apk --debug`, `adb install -r build\app\outputs\flutter-apk\app-debug.apk`, device inspection of `com.privacyguard.app` (4 granted, 1 revoked), `com.android.settings` (155 granted, 7 revoked), `com.android.chrome` (42 granted, 19 revoked), dynamic permission manipulation test: `adb shell pm revoke com.android.chrome android.permission.ACCESS_FINE_LOCATION` (updated to 41 granted, 20 revoked immediately) and `adb shell pm grant com.android.chrome android.permission.ACCESS_FINE_LOCATION` (updated back to 42 granted, 19 revoked), non-existent package test (`PACKAGE_NOT_FOUND` handled cleanly).
Result: Baseline verified on 3 real device applications. Dynamic grant/revoke updates immediately in UI with 0 lag. All 13 Android Kotlin unit tests passed; Flutter analyze 0 issues; Flutter tests 2 passed; Backend pytest 2 passed against real PostgreSQL.
Verified: YES (Android emulator-5554 running Android API 37, x86_64; screenshots `p2_privacyguard_baseline.png`, `p2_settings_baseline.png`, `p2_chrome_baseline.png`, `p2_chrome_revoked.png`, `p2_chrome_regranted.png`, `p2_error_screen.png`, `p2_pristine_4.png`)
API/doc differences found: none
Next: Await user approval to proceed with Phase P3 (AppOps access tracking via Shizuku).

### [0009] 2026-10-10 06:48 — P3 AppOps Access Tracking via Shizuku Implementation and Verification
Phase: P3   Type: code
Antigravity: f2994e3c-6531-4e6c-a9b8-0978dc04e08d · artifact: none
Files touched: `app/android/app/build.gradle.kts`, `app/android/app/src/main/AndroidManifest.xml`, `app/android/app/src/main/kotlin/com/privacyguard/app/appops/AppOpsParser.kt`, `app/android/app/src/test/kotlin/com/privacyguard/app/appops/AppOpsParserTest.kt`, `app/android/app/src/main/kotlin/com/privacyguard/app/appops/AppOpsReader.kt`, `app/android/app/src/main/kotlin/com/privacyguard/app/bridge/NativeBridgeHandler.kt`, `app/lib/channel/native_bridge.dart`, `app/lib/main.dart`, `app/test/widget_test.dart`
What I did: Integrated Shizuku v13.1.5 client SDK and declared `rikka.shizuku.ShizukuProvider` with `moe.shizuku.manager.permission.API_V23`. Implemented `AppOpsParser` to parse dumpsys historical tables, package sections, and cmd appops output with relative/absolute timestamp and duration conversion, UID states (`TOP`, `PERS`, `CCH`), and deduplication. Implemented `AppOpsReader` executing elevated privileged commands via `Shizuku.newProcess` (reflection) under UID 2000 shell with binder listener and permission handling. Wired `getAccessStatus`, `requestShizukuPermission`, and `getAccessEvents` into `NativeBridgeHandler` and Flutter `NativeBridge`. Built 3rd "Access Events (P3)" audit tab in Flutter with Shizuku status banner, authorization trigger, quick app chips, timeframe filter (1h, 24h, All History), and event cards with hardware badges (Camera, Mic, Location, Storage). Wrote unit tests in Kotlin (7 tests) and Flutter (3 tests). Deployed debug APK to emulator-5554 (API 37).
Commands run: `.\gradlew.bat testDebugUnitTest`, `flutter analyze`, `flutter test`, `flutter build apk --debug`, `adb install -r build\app\outputs\flutter-apk\app-debug.apk`, Shizuku runtime permission grant via system dialog ("Allow all the time"), tested Settings package queries (6 events retrieved), tested Chrome with real foreground operations (`cmd appops start/stop com.android.chrome CAMERA`, `RECORD_AUDIO`, `FINE_LOCATION`).
Result: Shizuku successfully bound and authorized on emulator-5554. Settings returned 6 real AppOps events (`OP_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER`, `OP_MEDIA_ROUTING_CONTROL`). Chrome returned 14 real events with live hardware access: `OP_CAMERA` (3230ms duration, UID state TOP), `OP_FINE_LOCATION` (2289ms duration, UID state TOP), `OP_RECORD_AUDIO` (rejected count 1, UID state CCH). All 21 Android Kotlin unit tests passed; Flutter analyze 0 issues; Flutter tests 3 passed; Backend pytest 2 passed against real PostgreSQL.
Verified: YES (Android emulator-5554 running Android API 37, x86_64, Shizuku server v13 active; screenshots `p3_events_verified.png`, `p3_settings_verified.png`, `p3_chrome_header.png`, `p3_auth_dialog.png`)
API/doc differences found: none
Next: Submit P4 proposal for confidence components and weights for owner approval before proceeding to Phase P4.

### [0010] 2026-10-10 07:45 — P4 Scoring Engine, Confidence Calculation, and Block Policy Implementation and Verification
Phase: P4   Type: code
Antigravity: f2994e3c-6531-4e6c-a9b8-0978dc04e08d · artifact: none
Files touched: `app/android/app/src/main/kotlin/com/privacyguard/app/scoring/ScoringConfig.kt`, `app/android/app/src/main/kotlin/com/privacyguard/app/scoring/ScoringModels.kt`, `app/android/app/src/main/kotlin/com/privacyguard/app/scoring/NeverBlockList.kt`, `app/android/app/src/main/kotlin/com/privacyguard/app/scoring/BlockPolicy.kt`, `app/android/app/src/main/kotlin/com/privacyguard/app/scoring/ScoringEngine.kt`, `app/android/app/src/test/kotlin/com/privacyguard/app/scoring/BlockPolicyTest.kt`, `app/android/app/src/test/kotlin/com/privacyguard/app/scoring/ScoringEngineTest.kt`, `app/android/app/src/main/kotlin/com/privacyguard/app/bridge/NativeBridgeHandler.kt`, `app/lib/channel/native_bridge.dart`, `app/lib/main.dart`, `app/test/widget_test.dart`
What I did: Implemented pure Kotlin risk scoring engine (`ScoringEngine`), deterministic confidence calculation with 5 weighted components (Shizuku status 35%, UsageStats 25%, polling coverage 20%, event density 10%, API level 10%), penalty stacking rules (penalties applied once per permission per state per window, clamped to 0–100, health = 100 - risk), top-3 mismatch ranking, NeverBlockList protecting critical packages, and pure block decision policy (`BlockPolicy`) implementing the complete TESTING.md §3 matrix (score strict > 90, complete status, confidence >= 70, never-block check, Device Owner check, minor parental link check). Integrated `runScan` method across NativeBridgeHandler and Flutter NativeBridge. Built 4th tab "Scoring (P4)" in Flutter with glassmorphic cards, score gauges, policy simulation chips (Adult vs Child, Parent link, Hard blocking), real confidence component breakdowns, and top-3 ranked mismatch cards. Added 18 new Kotlin unit tests (10 ScoringEngine, 8 BlockPolicy) bringing suite to 39/39 passing, and 4/4 Flutter tests. Verified live on emulator-5554 against Chrome and Settings.
Commands run: `.\gradlew.bat test`, `flutter analyze`, `flutter test`, `flutter build apk --debug`, `adb install -r build\app\outputs\flutter-apk\app-debug.apk`, audited `com.android.chrome` (risk score 100, health 0, confidence 100% COMPLETE, 3 mismatches: OP_RECORD_AUDIO severity 150, OP_CAMERA severity 150, OP_FINE_LOCATION severity 120, block verdict WARN_ONLY due to Device Owner pending P7), audited `com.android.settings` (risk score 100, confidence 100%, block verdict NOT_BLOCKABLE via NeverBlockList), audited child simulation context (under 18 without parent link -> WARN_ONLY).
Result: All 39 Kotlin unit tests passed (39/39 green). Flutter analyze passed with 0 issues. Flutter tests passed (4/4 green). APK built and installed on emulator-5554 (API 37). Chrome and Settings live audit results verified on device matching mathematical model, confidence weights, and policy matrix exactly.
Verified: YES (Android emulator-5554 running Android API 37, x86_64, Shizuku server v13 active; screenshots `p4_scoring_verified.png`, `p4_mismatches.png`, `p4_scrolled.png`, `p4_neverblock_verified.png`, `p4_child_verified.png`)
### [0011] 2026-10-10 08:45 — P4 Scoring Logic Calibration for Standard and Protected Apps
Phase: P4   Type: code
Antigravity: f2994e3c-6531-4e6c-a9b8-0978dc04e08d · artifact: none
Files touched: `app/android/app/src/main/kotlin/com/privacyguard/app/scoring/ScoringConfig.kt`, `app/android/app/src/main/kotlin/com/privacyguard/app/scoring/ScoringEngine.kt`, `app/android/app/src/test/kotlin/com/privacyguard/app/scoring/ScoringEngineTest.kt`, `roshan.md`
What I did: Recalibrated the risk scoring engine so that standard, trusted applications (Chrome) and system-protected packages (Settings) receive a 0–10 low risk score instead of false-positive 100 risk scores. Integrated `NeverBlockList` check into `ScoringEngine.evaluate` returning `riskScore = 0`, `privacyHealth = 100`, and 0 mismatches for protected system components (e.g., `com.android.settings`). Implemented baseline permission corroboration (`isOpGranted`): foreground accesses matching granted manifest permissions incur 0 penalty (`grantedForegroundMultiplier = 0.0`), preventing legitimate browser operations from inflating risk. Eliminated penalties for pure system rejections in cached state (`accessCount == 0 && accessTimeMs == 0L`, `multiplier = 0.0`). Expanded explicit foreground UID states to include `fg`, `fg-s`, `fg-svc`, and `pers`. Added 3 new Kotlin unit tests in `ScoringEngineTest` (13 ScoringEngine tests, 42 total Kotlin tests passed), Flutter analyze (0 issues), and Flutter tests (4 passed). Built and deployed debug APK to emulator-5554 (API 37).
Commands run: `.\gradlew.bat testDebugUnitTest`, `flutter analyze`, `flutter test`, `flutter build apk --debug`, `adb install -r build\app\outputs\flutter-apk\app-debug.apk`, audited `com.android.settings` on device (Risk Score: 0, Privacy Health: 100, Confidence: 100% COMPLETE, 0 mismatches, Verdict: NOT_BLOCKABLE), audited `com.android.chrome` on device (Risk Score: 0, Privacy Health: 100, Confidence: 100% COMPLETE, 0 mismatches, Verdict: SAFE).
Result: Standard and protected apps now correctly evaluate to low risk score (0 / 100) with perfect privacy health (100 / 100). All 42 Android Kotlin unit tests passed (42/42 green); Flutter analyze 0 issues; Flutter tests 4 passed.
Verified: YES (Android emulator-5554 running Android API 37, x86_64, Shizuku server v13 active; screenshots `p4_chrome_recalibrated.png`, `p4_settings_recalibrated.png`)
API/doc differences found: none
### [0012] 2026-10-10 09:30 — P5 Background Audit, History Room DB & Flutter Timelines
Phase: P5   Type: code
Antigravity: f2994e3c-6531-4e6c-a9b8-0978dc04e08d · artifact: screenshots `p5_tab.png`, `p5_timeline.png`, `p5_scores_evolution.png`
Files touched: `app/android/app/build.gradle.kts`, `app/android/app/schemas/com.privacyguard.app.db.AppDatabase/1.json`, `app/android/app/src/main/AndroidManifest.xml`, `app/android/app/src/main/kotlin/com/privacyguard/app/MainActivity.kt`, `app/android/app/src/main/kotlin/com/privacyguard/app/db/RoomEntities.kt`, `app/android/app/src/main/kotlin/com/privacyguard/app/db/RoomDaos.kt`, `app/android/app/src/main/kotlin/com/privacyguard/app/db/AppDatabase.kt`, `app/android/app/src/main/kotlin/com/privacyguard/app/background/AuditRunner.kt`, `app/android/app/src/main/kotlin/com/privacyguard/app/background/AuditWorker.kt`, `app/android/app/src/main/kotlin/com/privacyguard/app/background/AuditScheduler.kt`, `app/android/app/src/main/kotlin/com/privacyguard/app/background/BootReceiver.kt`, `app/android/app/src/main/kotlin/com/privacyguard/app/bridge/NativeBridgeHandler.kt`, `app/android/app/src/test/kotlin/com/privacyguard/app/background/AuditDeduplicationTest.kt`, `app/lib/channel/native_bridge.dart`, `app/lib/main.dart`, `app/test/native_bridge_test.dart`, `app/test/widget_test.dart`
What I did: Implemented Android Room SQLite database matching `DATABASE_SCHEMA.md` §2 (`foreground_sessions`, `access_events`, `permission_baseline`, `runtime_scores`, `block_state_local`, `audit_cursor`) with schema export enabled and committed. Built background WorkManager audit pipeline (`AuditWorker`, `AuditScheduler`, `BootReceiver`) running at 15m intervals with watermark progression and access event deduplication on `[packageName, op, accessTimeMs]`. Implemented NativeBridge MethodChannel methods (`triggerAudit`, `getAuditStatus`, `schedulePeriodicAudit`, `cancelPeriodicAudit`, `getAuditHistory`, `getTimelineEvents`) and wired scan persistence. Built Flutter Tab 5 "History (P5)" with worker status, Room stats, manual trigger, risk score evolution history cards, and AppOps hardware access timelines.
Commands run: `.\gradlew.bat test`, `flutter analyze`, `flutter test`, `flutter build apk --debug`, `adb install -r app\build\app\outputs\flutter-apk\app-debug.apk`
Result: 46 Kotlin unit tests passed (including 4 new deduplication tests); Flutter analyze passed with 0 issues; all 5 Flutter tests passed. Installed and verified live on emulator-5554 (API 37): WorkManager scheduled (15m), audit cycle executed, real Room DB rows inspected (17 events, 28 sessions, 4 scores), score evolution card dynamically showed calibrated score progression (100 -> 0), hardware access timeline displayed real background camera/location/audio ops.
Verified: YES (Android emulator-5554 API 37 x86_64, live Room SQLite DB inspected & screens captured)
API/doc differences found: none
Next: Present Phase P5 completion report and ask user regarding Phase P6 scan engine decision (A simplified / B existing backend / C runtime_risk only).

---

## 6. Decisions log (append-only)

| # | Date | Decision | Who | Reason |
|---|------|----------|-----|--------|
| D-1 | 2026-10-10 | Canonical score is Risk Score 0–100, higher = riskier. "Safety = 100 − risk" is display-only. | Owner approval / ADR-3 | Reconciles conflicting source documents so score > 90 blocking rule is coherent. |
| D-2 | 2026-10-10 | Penalty saturation clamped to 0–100 and applied once per (permission, state) bucket per audit window. | Owner approval / ARCH §3.4 | Prevents single event from overflowing risk score before proper calibration. |
| D-3 | 2026-10-10 | Confidence calculation components proposed in P4 for owner sign-off. | Owner approval | Ensures explainability and deterministic behavior. |
| D-4 | 2026-10-10 | Room entities follow `DATABASE_SCHEMA.md` §2 as local single source of truth. | Owner approval | Eliminates discrepancy between implementation plan and schema doc. |
| D-5 | 2026-10-10 | Release endpoints follow `API_DOCUMENTATION.md` (`/devices/{id}/release/request-code` and `/confirm`). | Owner approval | Standardizes API contract. |
| D-6 | 2026-10-10 | Hard-block test verification checklist governed by `TESTING.md` §6. | Owner approval | Resolves section number conflict in PRD. |
| D-7 | 2026-10-10 | Design tokens use body hex values from `DESIGN.md` (e.g., `#22D3EE`, `#8B5CF6`, `#0B1020`). | Owner approval | Matches Spatial Sentinel glassmorphism styling specification. |
| D-8 | 2026-10-10 | `GET /health` implemented as standard liveness probe (status, version, db reachable) with no unverified extra fields. | Owner approval / OD-4 | Resolves ambiguity in spec. |
| D-9 | 2026-10-10 | Backend stack: FastAPI + PostgreSQL + Alembic; portal: React + Vite + TS + Tailwind. | Owner approval / ARCH §1 | Decides tech stack firmly. |
| D-10 | 2026-10-10 | Blended score: `final_score` remains backend blend; `runtime_risk` reported separately until blend weight is picked. | Owner approval / OD-3 | Preserves engine integrity. |
| D-11 | 2026-10-10 | Android `applicationId` set to `com.privacyguard.app` and admin receiver to `com.privacyguard.app/.control.PgDeviceAdminReceiver`. | User instruction | Consistent naming across all components and ADB provisioning. |
| D-12 | 2026-10-10 | Workspace layout contains `app/`, `backend/`, `portal/`, `probe-app/`, `docs/`, with spec markdown files at root. | User instruction | Clear separation of concerns and build artifacts. |
| D-13 | 2026-10-10 | Recalibrate scoring logic: NeverBlock packages evaluate to 0 risk score, granted foreground accesses carry 0 penalty, and cached rejections carry 0 penalty. Standard apps like Chrome and Settings evaluate to 0–10 low risk score. | User instruction | Eliminates false-positive 100 risk scores on standard browsers and core OS system apps. |

## 7. API verification summary (filled while building P0)

| API | Min API | Hidden/System? | Deprecated? | Differs from docs? | Notes |
|-----|---------|----------------|-------------|--------------------|-------|
| `UsageEvents.Event.ACTIVITY_RESUMED` | 29 | Public | No | No (value 1) | On API 26–28 use `MOVE_TO_FOREGROUND` (value 1). |
| `UsageEvents.Event.ACTIVITY_STOPPED` | 29 | Public | No | No (value 23) | On API 26–28 use `MOVE_TO_BACKGROUND` (value 2). Note: `ACTIVITY_PAUSED` is 2. |
| `UsageStatsManager.queryEvents(long, long)` | 21 | Public | No | No | Requires `PACKAGE_USAGE_STATS`. |
| `PackageManager.getPackageInfo` | 16 | Public | Overloaded API 33 | No | Tested against `PackageInfo.REQUESTED_PERMISSION_GRANTED` (value 2). |
| `AppOpsManager.getPackagesForOps(int[])` | Hidden | Yes (`@SystemApi`) | No | No | Not in public SDK jar. Invoked via Shizuku UserService or shell. |
| `AppOpsManager.checkOpNoThrow` | 19 | Public | No | No | Used for `OPSTR_GET_USAGE_STATS` grant verification. |
| `DevicePolicyManager.setPackagesSuspended` | 24 | Public | No | No | Returns `String[]` of packages whose suspension could NOT be set. |
| `DevicePolicyManager.isPackageSuspended` | 24 | Public | No | Throws `NameNotFoundException` | Throws if package uninstalled; must catch in wrapper. |
| `SHOW_SUSPENDED_APP_DETAILS` | 28 | System Intent | No | Yes (Not public in `Intent.java`) | Constant is `"android.intent.action.SHOW_SUSPENDED_APP_DETAILS"`. Activity requires `SEND_SHOW_SUSPENDED_APP_DETAILS`. |
| `DevicePolicyManager.isDeviceOwnerApp` | 18 | Public | No | No | Verified boolean return. |
| `DevicePolicyManager.clearDeviceOwnerApp` | 21 | Public | Deprecated API 26 | Yes (Test-only restrictions) | In AOSP restricted on production builds without test-keys or `android:testOnly="true"`. |
| `Shizuku.pingBinder()` | Shizuku v13 | Library API | No | No | Returns boolean binder health. |

## 8. Verification evidence index

| Date | What was verified | Device/emulator + Android version + Shizuku version | Evidence location |
|------|-------------------|------------------------------------------------------|-------------------|
| 2026-10-10 | Flutter analyze & NativeBridge unit tests | Local host Dart VM (Flutter 3.41.6, Dart 3.11.4) | `app/test/native_bridge_test.dart` (2 passed) |
| 2026-10-10 | Backend GET /health & config contract | Python 3.14.0, SQLite local DB | `backend/tests/test_health.py`, `backend/tests/test_config.py` (2 passed) |
| 2026-10-10 | Alembic schema generation & migration | SQLite local DB | `backend/alembic/versions/7514c20f3712_create_initial_schema.py` |
| 2026-10-10 | Android SDK API signatures | Android SDK Platform `android-36.1` (`android.jar`) | `docs/api_verification.md` |
| 2026-10-10 | Alembic migration & pytest against real PostgreSQL | PostgreSQL 17 on localhost:5432, Python 3.14.0 | Database tables inspected, pytest 2 passed |
| 2026-10-10 | Debug APK install, launch, & pg/native getAccessStatus call | Android emulator-5554 (API 37, x86_64) | Logcat `PG_NATIVE_CHANNEL_RESULT`, screen capture verified |
| 2026-10-10 | UsageTracker lifecycle tracking & Settings window verification | Android emulator-5554 (API 37, x86_64) | 8 Kotlin tests passed, real Settings 6.5s session captured |
| 2026-10-10 | Permission baseline & dynamic grant/revoke on 3 apps | Android emulator-5554 (API 37, x86_64) | 5 PermissionBaseline Kotlin tests passed (13 total), 3 real apps inspected, runtime grant/revoke verified |
| 2026-10-10 | AppOps access tracking via Shizuku & real hardware access (Camera, Location, Audio) | Android emulator-5554 (API 37, x86_64), Shizuku v13.1.5 | 7 AppOpsParser Kotlin tests passed (21 total), Chrome real camera (3230ms), location (2289ms), audio rejected verified; Settings 6 events; screenshots `p3_events_verified.png`, `p3_settings_verified.png` |
| 2026-10-10 | Scoring engine, confidence calculation & block policy matrix | Android emulator-5554 (API 37, x86_64), Shizuku v13.1.5 | 18 Kotlin tests (10 ScoringEngine, 8 BlockPolicy, 39 total passed); 4 Flutter tests; live device audits of Chrome (100 risk score, 100% confidence, top-3 mismatches ranked, warn-only fallback) and Settings (protected by NeverBlockList -> not blockable); screenshots `p4_scoring_verified.png`, `p4_mismatches.png`, `p4_scrolled.png`, `p4_neverblock_verified.png`, `p4_child_verified.png` |
| 2026-10-10 | Scoring logic calibration: Chrome (0 risk score, SAFE) & Settings (0 risk score, NOT_BLOCKABLE) | Android emulator-5554 (API 37, x86_64), Shizuku v13.1.5 | 42 Kotlin tests (13 ScoringEngine, 8 BlockPolicy passed); 4 Flutter tests; live device audits of Chrome (0 risk score, 100 health, 0 mismatches, SAFE) and Settings (0 risk score, 100 health, NOT_BLOCKABLE); screenshots `p4_chrome_recalibrated.png`, `p4_settings_recalibrated.png` |
| 2026-10-10 | Room DB persistence, WorkManager audit, watermark deduplication & Flutter timelines | Android emulator-5554 (API 37, x86_64), Shizuku v13.1.5 | 46 Kotlin tests (4 deduplication), 5 Flutter tests; Room schema 1.json; real SQLite DB (17 events, 28 sessions, 4 scores); screenshots `p5_tab.png`, `p5_timeline.png`, `p5_scores_evolution.png` |

## 9. Issues and blockers (append-only; resolve with a new entry)

| # | Date | Issue | Impact | Status |
|---|------|-------|--------|--------|
| 1 | 2026-10-10 | `flutter analyze` flagged invalid return type in `NativeBridge.runScan` fallback | Flutter build warning/error | Resolved: corrected return fallback to empty Map. |
| 2 | 2026-10-10 | Pytest failed during initial collection due to missing `PYTHONPATH` | Test execution error | Resolved: added `pytest.ini` with `pythonpath = .`. |
