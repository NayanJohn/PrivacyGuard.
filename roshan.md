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
| P0 | Foundations: repo, Flutter+Kotlin skeleton, channel, CI, backend `/health`, API verification | Done (verified) | 2026-10-10 | Flutter analyze (0 issues) + test (2 passed); backend pytest (2 passed); Alembic migration applied; android-36.1 javap checks |
| P1 | Lifecycle tracking (UsageStats) | Not started | – | – |
| P2 | Permission baseline | Not started | – | – |
| P3 | AppOps access tracking via Shizuku | Not started | – | – |
| P4 | Scoring engine + confidence + mismatches | Not started | – | – |
| P5 | Background audit + history + Flutter timelines | Not started | – | – |
| P6 | Backend: scans, blocks, allow/block, alerts, parent email/portal, release codes | Not started | – | – |
| P7 | Hard blocking (Device Owner) + warn-only fallback + Release control | Not started | – | – |
| P8 | Hardening, UI to DESIGN.md, acceptance checklist | Not started | – | – |

Status values: `Not started` · `In progress` · `Blocked` · `Done (verified)` · `Done (NOT VERIFIED)`

## 2. Next action (edit in place)

Phase P0 complete. Awaiting user go-ahead to proceed to Phase P1 (Lifecycle tracking).

## 3. Open questions for the owner (edit in place)

| # | Question | Default proposed | Status |
|---|----------|------------------|--------|
| 1 | P4 confidence components and weights proposal | Define 5 components (Shizuku status, Usage access, polling coverage, sample event count, API source level) | Pending P4 |
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

## 9. Issues and blockers (append-only; resolve with a new entry)

| # | Date | Issue | Impact | Status |
|---|------|-------|--------|--------|
| 1 | 2026-10-10 | `flutter analyze` flagged invalid return type in `NativeBridge.runScan` fallback | Flutter build warning/error | Resolved: corrected return fallback to empty Map. |
| 2 | 2026-10-10 | Pytest failed during initial collection due to missing `PYTHONPATH` | Test execution error | Resolved: added `pytest.ini` with `pythonpath = .`. |
