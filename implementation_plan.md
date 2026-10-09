# Implementation Plan — PrivacyGuard

Sources: (1) *Privacy & Permission Auditor architecture* (features 1–5), (2) *Hard App Blocking via Device Owner* spec (feature 6 + backend).
Rule for everything below: **no fake/mock data**. Verify every Android API against the installed SDK (`compileSdk` sources / `android.jar`) and report any difference.

---

## 0. Reconciling the two source documents

These must be settled first. Defaults are my recommendation; change them in one place (`policy` config) rather than in code.

| # | Issue | Why it matters | Recommended default |
|---|-------|----------------|---------------------|
| 1 | **Score direction conflicts.** Doc 1: `Score = 100 − penalties` (high = safe). Doc 2: block when `score > 90` (high = dangerous). | Implemented literally, the safest apps would be blocked. | Store one canonical value: **`risk_score` (0–100, higher = riskier)**. Display "Privacy health" = `100 − risk_score`. The blocking rule uses `risk_score` (`final_score` in Doc 2). Block ⇔ health < 10. |
| 2 | **Penalty saturation.** Camera weight 15 × background multiplier 10 = 150 for *one* event, already above 100. | One background access could block an app. Hard blocking is high-impact. | Make weights/multipliers config. Clamp to 0–100. Decide stacking rules (see §3.1) and **calibrate on real measurements from real devices before enabling blocking by default.** Ship blocking behind the threshold + confidence gate only. |
| 3 | **`confidence`, "complete vs preliminary", and "top 3 mismatches from the engine"** are referenced by Doc 2 but never defined. | The block rule can't be implemented without them. | Define them in §3.1–3.2 (deterministic, explainable). |
| 4 | **Backend scan-ingest endpoints** are implied but not listed (Doc 2 lists only `/blocks`, `/allow`, `/block`). | Device must upload evidence/scan results. | Add `POST /scans`, `GET /alerts`, parent-portal and release-code endpoints (§3.5). |
| 5 | **Where is `final_score` computed?** Doc 1 computes on-device; Doc 2 has a backend "engine" and backend threshold config. | Privacy and trust boundary. | Device computes evidence + local score; backend owns **policy config** (threshold, confidence minimum, never-block list, weights) and the authoritative block decision. Upload derived summaries, not raw logs, unless the user opts in. |
| 6 | **"Add GET /health field for nothing new"** (Doc 2) is not meaningful. | Ambiguous instruction. | Implement a plain `GET /health` (status, version, db reachable). No new field. Flagged for your confirmation. |
| 7 | **Android version gaps.** minSdk 26, but `ACTIVITY_RESUMED/STOPPED` constants are API 29, per-flag AppOps accessors are API 29, `SHOW_SUSPENDED_APP_DETAILS` is API 28. | Features break on 26–28 if unhandled. | Use legacy `MOVE_TO_FOREGROUND` (1) / `MOVE_TO_BACKGROUND` (2) below API 29; degrade gracefully (lower confidence) where a source is missing. |
| 8 | **Re-scan vs "allowed" state.** Doc 2 says sync covers parent "Allow" and re-scans, but not which wins. | Flip-flopping blocks frustrate users. | `allowed_by_*` persists until a manual re-block (`POST /block`); a re-scan that raises the score only raises a warning/alert. |
| 9 | **Age source** ("Below 18") is undefined. | Decides who can unblock. | Backend-held age category from the account profile; the device never self-declares. Parent link is verified by magic-link email. |

---

## 1. Goals / non-goals

**Goals:** accurate, explainable audit of other apps' sensitive-permission use; honest hard blocking where technically possible; clear fallback to warn-only; full transparency and an always-available exit.

**Non-goals:** hiding the app or its blocking; blocking system/critical packages; claiming a block when none occurred; reading app content; working without Usage access/Shizuku (it degrades and says so).

---

## 2. Architecture & components

| Layer | Component | Responsibility |
|-------|-----------|----------------|
| Flutter | UI, state, charts | Dashboard, app detail, timeline, setup, block pages, alerts, settings |
| Bridge | `MethodChannel` `pg/native` (+ `EventChannel` for live status) | Typed calls, no business logic |
| Kotlin | `LifecycleTracker` | `UsageStatsManager` → foreground windows |
| Kotlin | `AccessTracker` | Shizuku → AppOps → access events |
| Kotlin | `BaselineReader` | `PackageManager` permission baseline |
| Kotlin | `ScoringEngine` (pure, unit-testable) | Intersection + weights → `risk_score`, `confidence`, mismatches |
| Kotlin | `DeviceControl` | DPM suspend/unsuspend/isSuspended/clear owner |
| Kotlin | `PgDeviceAdminReceiver` | `DeviceAdminReceiver` |
| Kotlin | `BlockSync` (WorkManager + on-open) | Make device state match `GET /blocks` |
| Kotlin | `AuditWorker` / foreground service | Periodic/continuous event capture |
| Storage | Room (native) as source of truth; Flutter reads via channel (or shared SQLite file) | Events, windows, scans, block state |
| Backend | REST API + DB + mailer + parent portal | Policy config, scans, block decisions, alerts, email |

Pick **one** local DB owner (recommended: Room in Kotlin, because background workers run without the Flutter engine). Flutter reads through the channel.

---

## 3. Contracts

### 3.1 Scoring model

Inputs: foreground windows `W = [(start,end)]`, access events `E = (perm, accessTime, duration, rejectTime?)`, baseline permissions.

1. Classify each event: **foreground** if `accessTime ∈ some window` (± small configurable tolerance); otherwise **background**. On API 29+ also record AppOps' own foreground/background timestamps and use them to corroborate.
2. Weights (from Doc 1, configurable): Camera 15, Microphone 15, Fine location 12, SMS 10, Contacts 10, External storage 5.
3. State multiplier (from Doc 1, configurable): foreground 1×, background 10×.
4. `penalty = Σ weight × multiplier` over the **stacking rule**, then `risk_score = clamp(penalty, 0, 100)`.
   - **Decision needed (calibration):** stacking rule — per event, per permission once per window, or capped per permission. Recommended starting point: *once per permission per state per audit window*, with a per-permission cap; tune from real data.
5. `privacy_health = 100 − risk_score` (display only).
6. **Mismatches** (top 3 for the block page): ranked by `weight × multiplier`, each = `{permission, expected_state, observed_state, evidence_count, first_seen, last_seen}`. "Expected" = foreground-only unless the baseline shows a legitimate background grant (e.g. background location granted).
7. **Confidence (0–100)**, deterministic from data quality, e.g. components: Shizuku connected during the window; Usage access granted; coverage of the window by polling (gaps between polls reduce it); event count; API-level sources available. Define exact weights in config and document them; surface the components in the UI.
8. **Scan status:** `preliminary` until the minimum observation window and one full poll cycle are covered and all sources were available; then `complete`.

### 3.2 Block decision (pure function — unit tested)

```
shouldBlock(scan, pkg, device, user) =
     scan.status == COMPLETE
  && scan.final_score  >  BLOCK_THRESHOLD        // 90, strict
  && scan.confidence   >= MIN_CONFIDENCE         // 70
  && !neverBlock(pkg)
  && device.isDeviceOwner
  && ( user.age >= 18 ? user.hardBlockingEnabled : user.hasVerifiedParentLink )
```

Never-block list (resolved at runtime on the device, not hard-coded names): default dialer, default SMS app, Settings, current launcher(s), emergency apps, any system package, PrivacyGuard itself. Also anything for which `setPackagesSuspended` returns the package in its failed array → `not_blockable` (log + warning state).

### 3.3 MethodChannel surface (`pg/native`)

| Method | Returns |
|--------|---------|
| `getAccessStatus` | `{usageAccess, shizukuRunning, shizukuPermission, deviceOwner, apiLevel}` |
| `openUsageAccessSettings` | void |
| `requestShizukuPermission` | granted bool |
| `getForegroundWindows(pkg, startMs, endMs)` | list of `{start,end}` |
| `getAccessEvents(pkg, sinceMs)` | list of events |
| `getBaseline(pkg)` | `{granted:[], notGranted:[]}` |
| `runScan(pkg)` | scan result (local) |
| `suspend(pkg)` / `unsuspend(pkg)` | `{ok, failedPackages:[]}` |
| `isSuspended(pkg)` | bool |
| `releaseControl(confirmation?)` | `{ok, error?}` |
| `syncBlocks()` | `{applied, failed:[]}` |

### 3.4 Local schema (Room)

`app_package(pkg PK, label, first_seen)` · `foreground_window(id, pkg, start, end)` · `access_event(id, pkg, op, access_time, duration, reject_time, state, source)` · `baseline(pkg, permission, granted, captured_at)` · `scan(id, pkg, started_at, finished_at, status, risk_score, confidence, mismatches_json)` · `block_state(pkg PK, state, reason_json, blocked_at, changed_by, changed_at)` · `sync_cursor(source, last_checked_ms)`.
Unique index on `(pkg, op, access_time)` to de-duplicate repeated polls.

### 3.5 Backend

**Scan fields:** `block_state` (`none | warned | blocked | allowed_by_parent | allowed_by_user | not_blockable`), `block_reason` (top 3 mismatches), `blocked_at`.

**Endpoints**

| Endpoint | Auth | Purpose |
|----------|------|---------|
| `GET /health` | none | liveness |
| `POST /scans` | device token | upload derived scan summary (added) |
| `GET /blocks` | device token | desired block list for this device |
| `POST /apps/{package}/allow` | adult user, or parent magic-link token | sets `allowed_by_user` / `allowed_by_parent` |
| `POST /apps/{package}/block` | adult user / parent | manual re-block |
| `GET /alerts` | user | feed: every block/unblock with who + when (added) |
| `POST /release/request` + `POST /release/confirm` | parent | emailed one-time code for <18 release (added) |
| Parent portal `GET /parent/allow?token=…` | expiring token | "Allow this app" page |

**Policy config (server):** `BLOCK_THRESHOLD=90`, `MIN_CONFIDENCE=70`, weights, multipliers, never-block rules, token TTL.
**Audit log:** append-only `(actor, action, package, from_state, to_state, at)`.
**Email (<18 on block):** app, score, reasons, "Allow this app" button (expiring token).

---

## 4. Phases

Each phase ends with its **exit criteria verified on a real device/emulator**.

| Phase | Work | Exit criteria |
|-------|------|---------------|
| **P0 – Foundations** | Repo, Flutter+Kotlin skeleton, channel scaffolding, CI, backend skeleton + `/health`, config module. Confirm `compileSdk` and read the real signatures of every API in §3.3. | Builds; API verification notes written (differences reported). |
| **P1 – Lifecycle tracking** | Usage-access detection + redirect to `ACTION_USAGE_ACCESS_SETTINGS`; `queryEvents`; API<29 fallback; window builder. | Windows match manually opening/closing a real app. |
| **P2 – Baseline** | `getPackageInfo(GET_PERMISSIONS)`, flag parsing (`REQUESTED_PERMISSION_GRANTED`). | Baseline equals Settings → App permissions for several real apps. |
| **P3 – Access tracking** | Shizuku integration (permission, binder, UserService); AppOps read (hidden API path and/or `appops` shell path, whichever the SDK actually supports); event de-dup. | Real camera/mic/location use by a real app appears with correct time/duration. |
| **P4 – Scoring** | `ScoringEngine`, config, confidence, mismatches, preliminary/complete; unit tests for pure logic. | Probe-app ground truth reproduced (see TESTING.md). |
| **P5 – Background audit & history** | WorkManager (min periodic interval is 15 min) + optional foreground service; Room persistence; Flutter timeline/charts. | Events captured with screen off; gaps measured and reflected in confidence. |
| **P6 – Backend** | Scan ingest, policy, `/blocks`, allow/block, alerts, parent email + portal, release code, tests. | Endpoint tests pass against a real DB. |
| **P7 – Hard blocking** | Receiver + `device_admin.xml` + manifest; `DeviceControl`; setup screen + ADB instructions + Re-check; post-scan block; `SHOW_SUSPENDED_APP_DETAILS` activity; sync job; reboot/update re-apply; Release control; persistent notification + chip; warn-only fallback. | All "Verify" items from Doc 2 pass (TESTING.md §6). |
| **P8 – Hardening** | Permission-denied/Shizuku-died/offline paths, battery, accessibility, store/distribution review, docs. | Acceptance checklist signed off. |

## 5. Android manifest / platform checklist

- `PACKAGE_USAGE_STATS` (+ `tools:ignore="ProtectedPermissions"`), Shizuku `ShizukuProvider`, `QUERY_ALL_PACKAGES` or `<queries>` (package-visibility rules, API 30+; note Play policy).
- `PgDeviceAdminReceiver` with `BIND_DEVICE_ADMIN`, `@xml/device_admin`, `DEVICE_ADMIN_ENABLED` filter.
- Activity with `SHOW_SUSPENDED_APP_DETAILS` intent filter, `exported`, and the permission the system requires for that action (**verify** exact attribute requirements on the installed SDK).
- `BOOT_COMPLETED` receiver, `POST_NOTIFICATIONS` (API 33+), foreground-service type declarations if using an FGS.
- Boot/update re-apply: re-run sync after reboot and after `PACKAGE_REPLACED`/package-changed events for suspended apps; **verify** whether the OS already preserves suspension (do not assume).

## 6. Risks

| Risk | Mitigation |
|------|-----------|
| False-positive blocks of legitimate apps | Strict gate (complete + confidence ≥ 70 + >90), never-block list, easy Allow path, calibration on real data |
| Device Owner cannot be removed without factory reset if `clearDeviceOwnerApp` fails | Ship Release control; test it first; know that `dpm remove-active-admin` works only for test-only builds |
| Shizuku stops (reboot/ADB loss) | Detect, surface, lower confidence; never score with missing data silently |
| Polling gaps lose events (`appops get` exposes only the latest access per op) | Poll frequently, prefer historical ops where the SDK supports them, expose coverage in confidence |
| UsageStats retention is limited | Persist windows locally as they are captured |
| Distribution (Usage access, QUERY_ALL_PACKAGES, Device Owner are policy-sensitive) | Plan side-load / managed distribution; review store policy early |
| Privacy of collected data | Minimise upload, encrypt in transit/at rest, retention limits, delete-on-request |

## 7. Open decisions (need your answer)

1. Confirm the score-direction resolution (§0.1).
2. Stacking/calibration rule for penalties (§3.1.4) and whether to gate hard blocking on a calibration sign-off.
3. Confirm "GET /health field for nothing new" interpretation (§0.6).
4. Backend technology/hosting (not specified). Suggested: typed REST service + PostgreSQL + transactional email provider.
5. Re-scan vs allowed behaviour (§0.8) and parent-link verification method.
