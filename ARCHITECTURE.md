# PrivacyGuard — Architecture

## 1. System overview

```
┌──────────────────────────── Android device ─────────────────────────────┐
│  Flutter UI (Dart)                                                      │
│   dashboard · timelines · setup · block page · alerts · settings        │
│        │ MethodChannel / EventChannel                                   │
│  Kotlin native host                                                     │
│   ├─ lifecycle/   UsageTracker      (UsageStatsManager)                 │
│   ├─ appops/      AppOpsReader      (Shizuku → AppOps)                  │
│   ├─ baseline/    PermissionBaseline(PackageManager)                    │
│   ├─ scoring/     RuntimeScorer     (intersection + weights)            │
│   ├─ audit/       AuditWorker       (WorkManager)                       │
│   ├─ device/      PgDeviceAdminReceiver, DeviceControl (DPM)            │
│   ├─ sync/        BlockSyncWorker, ApiClient                            │
│   ├─ ui/          SuspendedAppDetailsActivity                           │
│   └─ data/        Room DB (local)                                       │
└───────────────┬─────────────────────────────────────────────────────────┘
                │ HTTPS (device token)
┌───────────────▼──────────────── Backend (FastAPI) ──────────────────────┐
│  Scan engine: Androguard (extractor) + RiskInDroid (ml_risk)            │
│               + Heuristics (heuristic_risk) + MobSF (static_risk)       │
│               → weighted blend → final_score, confidence, status        │
│  Block service: rule evaluation, block_state, /blocks, allow/block      │
│  Parent service: magic-link tokens, portal API, email sender            │
│  Alerts/audit log                                                       │
└───────────────┬─────────────────────────────────────────────────────────┘
                │
        PostgreSQL            React/Vite/TS/Tailwind dashboard + parent portal
```

## 2. Responsibilities and the single source of truth

| Concern | Owner |
|---------|-------|
| Static score, confidence, complete/preliminary status | Backend engine |
| **Block decision** (`final_score > 90 ∧ complete ∧ confidence ≥ 70 ∧ age/parent gate ∧ not never-block`) | **Backend** (config `BLOCK_THRESHOLD`, `BLOCK_MIN_CONFIDENCE`) |
| **Enforcement** (suspend/unsuspend) | **Device** (`DeviceControl`) — only the device can do it |
| Never-block classification | Both: device resolves real roles (dialer, SMS, launcher, system); backend holds the configured allowlist. Either side vetoes. |
| Runtime access + foreground sessions | Device (raw data stays local) |
| Allow / release authorization | Backend (adult session, parent token, release code) |

The device applies the decision returned at scan completion immediately, and `GET /blocks` converges any drift (parent Allow, re-scans, reboot, app update).

## 3. Android module design

### 3.1 UsageTracker
`UsageStatsManager.queryEvents(start, end)` → iterate `UsageEvents.Event`; `getPackageName()` filter; type 1 (`ACTIVITY_RESUMED`) opens, type 23 (`ACTIVITY_STOPPED`) closes. API 26–28 fall back to `MOVE_TO_FOREGROUND` / `MOVE_TO_BACKGROUND`. Output `List<Session(startMs,endMs)>`. Refinement to evaluate on a real device: foreground-service start/stop events, so a legitimately running foreground service isn't scored as silent background use.

### 3.2 AppOpsReader (Shizuku)
- Gate: `Shizuku.pingBinder()`, `Shizuku.checkSelfPermission()`, `Shizuku.requestPermission()`.
- Run privileged calls in the Shizuku process (a bound **UserService** or `ShizukuBinderWrapper` around the AppOps binder), because hidden-API calls from the app process are restricted and `Shizuku.newProcess` is not the supported path in recent Shizuku versions *(verify against the installed Shizuku API)*.
- Data source options, in order of preference: AppOps historical ops (`getHistoricalOps`, API 29+) → `appops get <pkg>` / `cmd appops` shell output. Shell output reports **relative** times ("time=+…ago"); convert with the query-time clock and store absolute timestamps. Parser must be tested per API level because the format differs across versions.
- Output per op: `accessTime`, `duration`, `rejectTime`.

### 3.3 PermissionBaseline
`getPackageInfo(pkg, GET_PERMISSIONS)`; zip `requestedPermissions` with `requestedPermissionsFlags`; `GRANTED` if `flag & REQUESTED_PERMISSION_GRANTED != 0`. Needs package visibility (`QUERY_ALL_PACKAGES`).

### 3.4 RuntimeScorer
```
for each access event e in window:
    state = FOREGROUND if e.accessTime ∈ some session else BACKGROUND
    bucket[(e.permission, state)] += 1            # applied once per bucket
penalty  = Σ over buckets  weight[perm] × multiplier[state]
runtime_risk = clamp(penalty, 0, 100)
breakdown    = list(permission, state, count, weight, multiplier, penalty)
```
Weights and multipliers come from config (Camera 15, Mic 15, Fine Location 12, SMS 10, Contacts 10, Storage 5; fg ×1, bg ×10). Display-only `safety = 100 − runtime_risk`.

### 3.5 Audit & persistence
`AuditWorker` (WorkManager periodic; optional foreground service) → UsageTracker + AppOpsReader → dedupe on `(package, op, accessTime)` → Room → recompute `RuntimeScorer` → expose to Flutter. Cursor (`last_checked_ms`) stored in Room. Re-enqueued on `BOOT_COMPLETED`.

### 3.6 Hard-block components
- **PgDeviceAdminReceiver** + `device_admin.xml` + manifest `BIND_DEVICE_ADMIN` entry.
- **DeviceControl**
  - `isDeviceOwner()` → `dpm.isDeviceOwnerApp(packageName)`
  - `suspend(pkg)` / `unsuspend(pkg)` → `dpm.setPackagesSuspended(admin, arrayOf(pkg), true|false)` returns `Array<String>` of **failed** packages → returned to caller
  - `isSuspended(pkg)` → `dpm.isPackageSuspended(admin, pkg)`
  - `release()` → unsuspend all, then `dpm.clearDeviceOwnerApp(packageName)` *(verify availability/deprecation on installed SDK)*
- **NeverBlockResolver** — resolves at runtime: `TelecomManager.getDefaultDialerPackage()`, default SMS (`Telephony.Sms.getDefaultSmsPackage`), launcher (resolve `CATEGORY_HOME`), `FLAG_SYSTEM`, Settings, emergency apps, default IME, Shizuku Manager, own package.
- **SuspendedAppDetailsActivity** — intent filter `android.intent.action.SHOW_SUSPENDED_APP_DETAILS` (API 28+), guarded by `SEND_SHOW_SUSPENDED_APP_DETAILS`; reads local block state + reasons.
- **BlockSyncWorker** — periodic + on-open; `GET /blocks` → diff against `isSuspended` → apply → `POST` status back.

## 4. Key sequences

### 4.1 Scan completes → block
```
Device                         Backend                       Parent
  │ new package detected          │                            │
  │── POST /scans ───────────────▶│ run engine                 │
  │◀── scan (final, confidence) ──│ evaluate rule              │
  │                               │ set block_state            │
  │ if blocked: DeviceControl.suspend(pkg)                     │
  │   failed[] non-empty? → not_blockable (log, warn UI)       │
  │ verify isSuspended(pkg) → notify user                      │
  │── POST status ───────────────▶│ log block_event            │
  │                               │ if under-18: email ───────▶│
```

### 4.2 Parent allow
```
Parent clicks "Allow this app" (expiring token)
  → Backend verifies token (hash, expiry, single-use) → block_state=allowed_by_parent
  → block_event(actor=parent) logged
Device BlockSyncWorker: GET /blocks → package no longer blocked → unsuspend(pkg)
```

### 4.3 Release control
Adult: confirm dialog → unsuspend all → `clearDeviceOwnerApp`.
Minor: request code → backend emails parent → parent shares code → device submits to backend → backend authorizes → same sequence.

## 5. Security and threat model

| Threat | Control |
|--------|---------|
| Forged "allow" | Parent token hashed, single-use, short expiry, tied to device + package |
| Child self-releases | Release requires parent code (<18) |
| Stolen device token | Per-device tokens, revocable; rate limits |
| Backend outage | Device keeps last known state; UI shows "last synced" honestly |
| Over-blocking | Confidence + complete gate, never-block list, Allow paths |
| Abuse as spyware | Persistent notification, visible chip, source named, parent link verified, disclosure at first run |
| Data exposure | Raw logs local; HTTPS; minimal fields synced |

## 6. Failure modes

| Failure | Behavior |
|---------|----------|
| Usage Access not granted | Lifecycle features disabled, grant prompt |
| Shizuku stopped | Runtime scoring disabled, start guidance; static audit continues |
| Not Device Owner | Warn-only mode, says so |
| `setPackagesSuspended` returns the package | `not_blockable`, logged, warning UI |
| Reboot | Re-enqueue workers; sync re-applies suspension |
| Target app updates | Sync verifies `isPackageSuspended`; re-suspends if reset |

## 7. Architecture decisions

| ADR | Decision | Reason |
|-----|----------|--------|
| 1 | Backend decides, device enforces | One audit trail; parent Allow works remotely |
| 2 | Room for local DB | Native workers write while UI is closed; Flutter reads over channels |
| 3 | Risk Score = higher is riskier | Matches the >90 block rule |
| 4 | Raw runtime logs stay on device | Privacy minimization |
| 5 | Config-driven weights | Tune from real data, not guesses |
