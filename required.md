# PrivacyGuard — Requirements (`required.md`)

> Scope: **Runtime Privacy Audit** (lifecycle tracking, AppOps access tracking, permission baseline, foreground/background risk scoring, background logging) and **Hard App Blocking** (Device Owner + `DevicePolicyManager.setPackagesSuspended`) for apps whose final score is above 90.
> Sources: the *Privacy & Permission Auditor* feature breakdown and the *HARD APP BLOCKING* spec.

---

## 0. Ground rules (apply to everything)

| # | Rule |
|---|------|
| G1 | **No fake or mock data anywhere** in product code, database seeds, UI, docs examples or tests. Every number shown comes from a real scan, a real device or a real config value. |
| G2 | When data does not exist yet, show an **honest empty state** ("No scans yet", "Usage Access not granted"). Never a placeholder score. |
| G3 | **Never pretend a block happened.** UI and API report `blocked` only after `setPackagesSuspended` returned without listing the package as failed *and* `isPackageSuspended` confirms it. |
| G4 | **No hidden behavior.** While hard blocking is on there is a persistent notification and a visible "Hard blocking active" chip. Block pages always name PrivacyGuard as the source. |
| G5 | **Verify every Android API against the installed SDK.** If something differs from this document, tell the owner and record it in `implementation_plan.md` → "API verification log". |
| G6 | Platform: **min SDK 26**. |

---

## 1. Functional requirements

### 1.1 Lifecycle state tracking (foreground vs background)

| ID | Requirement |
|----|-------------|
| FR-L1 | Detect whether `PACKAGE_USAGE_STATS` is granted (`AppOpsManager.OPSTR_GET_USAGE_STATS` check). If not, send the user to `Settings.ACTION_USAGE_ACCESS_SETTINGS`. |
| FR-L2 | Query `UsageStatsManager.queryEvents(start, end)` for a configurable window (default: last 24 h) for each audited package. |
| FR-L3 | Classify events: `ACTIVITY_RESUMED` (type 1) opens a foreground window; `ACTIVITY_STOPPED` (type 23) closes it. On API 26–28 use the legacy `MOVE_TO_FOREGROUND` / `MOVE_TO_BACKGROUND` constants (verify). |
| FR-L4 | Produce a time-ordered list of foreground sessions `[startMs, endMs]` per package; an open session at window end is closed at `now`. |
| FR-L5 | Persist sessions to the local database. |

### 1.2 Runtime hardware and permission access tracking

| ID | Requirement |
|----|-------------|
| FR-A1 | Track ops: `OP_CAMERA`, `OP_RECORD_AUDIO`, `OP_FINE_LOCATION`, `OP_READ_CONTACTS`, `OP_READ_SMS` (+ external-storage ops for the Moderate weight). |
| FR-A2 | Read AppOps history through **Shizuku** elevated privileges (not available to a normal app). Verify binder with `Shizuku.pingBinder()`; check/request Shizuku permission. |
| FR-A3 | If Shizuku is not running/authorized, show "Start Shizuku" guidance and **disable** runtime-access scoring (static audit still works). Never fabricate access data. |
| FR-A4 | Extract per event: **access time**, **duration**, **reject time**. |
| FR-A5 | Deliver data to Flutter through a `MethodChannel` as structured maps. |

### 1.3 Static permission baseline

| ID | Requirement |
|----|-------------|
| FR-B1 | `PackageManager.getPackageInfo(pkg, GET_PERMISSIONS)` for the target package. |
| FR-B2 | For each `requestedPermissions[i]`, test `requestedPermissionsFlags[i] & REQUESTED_PERMISSION_GRANTED` → `GRANTED` or `NOT_GRANTED`. |
| FR-B3 | Declare `QUERY_ALL_PACKAGES` (or `<queries>`) — Android 11+ package visibility otherwise hides other apps. Sideloaded hackathon build only; flag as a store-policy constraint. |

### 1.4 Risk scoring and intersection engine

| ID | Requirement |
|----|-------------|
| FR-S1 | Intersect every access timestamp with the foreground sessions: inside a session = **foreground**, otherwise **background**. |
| FR-S2 | Weights: Camera 15, Microphone 15, Fine Location 12, SMS 10, Contacts 10, External Storage 5 (config-driven). |
| FR-S3 | Multipliers: foreground ×1, background ×10 (config-driven). |
| FR-S4 | `Score = 100 − Σ(weight × multiplier)` as given in the source (see Open Decision OD-1/OD-2 for direction and clamping). |
| FR-S5 | Every score ships with its **breakdown** (permission, state, count, penalty) so the UI can explain it. |

### 1.5 Background audit and history

| ID | Requirement |
|----|-------------|
| FR-H1 | WorkManager periodic job (or foreground service when the user opts in) runs AppOps + usage query. |
| FR-H2 | Only new events since the last cursor are stored (dedupe on package + op + access time). |
| FR-H3 | Local DB (Room) stores sessions, access events, scores, block state. |
| FR-H4 | Flutter reads history and renders timelines and score trends. |
| FR-H5 | Re-schedule work after reboot. |

### 1.6 Hard blocking (Device Owner)

| ID | Requirement |
|----|-------------|
| FR-K1 | `BLOCK_THRESHOLD = 90` (backend config). Block **only if** `final_score > 90` **AND** scan is **complete** (not preliminary) **AND** `confidence >= 70`. Otherwise warn only. |
| FR-K2 | **Never suspend:** dialer, default SMS app, Settings, launcher, emergency apps, system packages, PrivacyGuard itself. Any package the OS refuses to suspend is `not_blockable` (log + warning state, not an error). |
| FR-K3 | Under 18: blocking requires a **verified parent link**. 18+: the user controls their own blocks. |
| FR-K4 | `PgDeviceAdminReceiver : DeviceAdminReceiver` + `res/xml/device_admin.xml` + manifest entry. |
| FR-K5 | `DeviceControl`: `isDeviceOwner()`, `suspend(pkg)`, `unsuspend(pkg)`, `isSuspended(pkg)`; `suspend/unsuspend` return the failed-packages array to the caller. |
| FR-K6 | Setup screen "Enable hard blocking": detect Device Owner; if absent, show `adb shell dpm set-device-owner <applicationId>/<receiver class>` + **Re-check** button + the "no accounts on device" explanation. |
| FR-K7 | Without Device Owner: hard blocking disabled, fall back to **warn-only** (full-screen warning + `ACTION_DELETE` uninstall button) and say so. |
| FR-K8 | After each completed scan: if FR-K1 matches → `suspend(pkg)`, save `block_state = blocked` with reasons, notify the user. |
| FR-K9 | Activity handling `android.intent.action.SHOW_SUSPENDED_APP_DETAILS` (API 28+): "Why this app is blocked" (score, top-3 mismatches, verdict, and request/give-permission path by age category). |
| FR-K10 | Block-list sync: WorkManager + on app open → `GET /blocks`, apply suspend/unsuspend so device matches backend. Re-apply after reboot and after the blocked app updates (verify whether updates reset state). |
| FR-K11 | "Release control" (Settings): unsuspend everything, then `clearDeviceOwnerApp`. 18+: confirm dialog. Under 18: parent confirmation code emailed by the backend. Verify API availability. |
| FR-K12 | Persistent notification + "Hard blocking active" chip. |

### 1.7 Backend

| ID | Requirement |
|----|-------------|
| FR-D1 | Scan fields: `block_state` (`none | warned | blocked | allowed_by_parent | allowed_by_user | not_blockable`), `block_reason` (top-3 mismatches), `blocked_at`. |
| FR-D2 | Endpoints: `GET /blocks`, `POST /apps/{package}/allow` (adult user, or parent via magic-link token), `POST /apps/{package}/block` (manual re-block). |
| FR-D3 | Log every block/unblock with **who and when**; surface in the Alerts feed. |
| FR-D4 | Under 18: on block, email the parent — app, score, reasons, **"Allow this app"** button (parent portal, expiring token). Allow → `allowed_by_parent` → device unsuspends on next sync. |
| FR-D5 | Tests for the rule: 90 not blocked; 91 blocked only if final and confidence ≥ 70; preliminary never blocks; never-block list respected. |
| FR-D6 | The existing under-18 high-score alert (score ≥ 80 → Gmail to parent) keeps working and is distinct from the >90 block. |

---

## 2. Non-functional requirements

| Area | Requirement |
|------|-------------|
| Privacy | Raw access logs stay on device by default; only summaries + scan results sync. Parent/child data visibility is disclosed in-app. |
| Security | HTTPS only; device tokens; parent tokens single-use, hashed at rest, expiring; release codes hashed, rate-limited. |
| Reliability | Sync is idempotent; device converges to backend state; failures surface as visible states. |
| Battery | Periodic job interval configurable; no continuous polling unless foreground service opted in. |
| Observability | Structured logs for every suspend/unsuspend call incl. failed-package arrays. |
| Compatibility | Min SDK 26; behavior differences per API level documented (usage events, notification permission API 33, FGS types API 34). |

## 3. Manifest / permission checklist

`PACKAGE_USAGE_STATS` (user-granted in Settings), `QUERY_ALL_PACKAGES`, `POST_NOTIFICATIONS` (API 33+), `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` (+ declared type on API 34+, only if foreground-service mode is built), `INTERNET`, Shizuku provider + `moe.shizuku.manager.permission.API_V23`, `SEND_SHOW_SUSPENDED_APP_DETAILS` on the details activity. *(Verify each against the installed SDK and Shizuku version.)*

## 4. Open decisions (need the owner's answer — defaults shown)

| ID | Issue found in the sources | Default used in these docs |
|----|----------------------------|----------------------------|
| OD-1 | **Score direction conflicts.** Runtime model is `100 − penalties` (higher = safer). Blocking says block when score **> 90** (higher = riskier). | Canonical **Risk Score 0–100, higher = riskier**. Runtime model outputs `runtime_risk = clamp(Σ penalties, 0, 100)`; "Safety = 100 − risk" is display-only. Blocking uses `final_score` (risk). |
| OD-2 | **Saturation.** With ×10 background multiplier, a single background camera access = 150 penalty (> 100). Any background camera/mic/location/SMS/contacts access already maxes the runtime score. | Clamp to 0–100, apply once per `(permission, state)` bucket per window, keep the full breakdown. Weights/multipliers config-driven so they can be tuned on real data. |
| OD-3 | How runtime risk joins the backend's 3-component static score (`ml_risk`, `heuristic_risk`, `static_risk`). | `final_score` stays the backend's weighted blend; `runtime_risk` is uploaded and shown separately until a blend weight is chosen from real data. |
| OD-4 | Spec line "Add GET /health field for nothing new" is ambiguous. | Interpreted as **no change to `/health`**. |
| OD-5 | Never-block list beyond the spec. | Also protect default IME (keyboard), Shizuku Manager, and the backend-configured allowlist. |
| OD-6 | UI stack: source 1 says Flutter + Kotlin native host. | Flutter UI + Kotlin native layer (Room for local DB). |
| OD-7 | Existing backend endpoints beyond the four named. | Marked "confirm" in `API_DOCUMENTATION.md`. |

## 5. Traceability

| Source section | Requirement IDs |
|----------------|-----------------|
| Auditor §1 Lifecycle | FR-L1…L5 |
| Auditor §2 Runtime access | FR-A1…A5 |
| Auditor §3 Baseline | FR-B1…B3 |
| Auditor §4 Scoring | FR-S1…S5 |
| Auditor §5 Background | FR-H1…H5 |
| Blocking: Rules | FR-K1…K3, FR-D5 |
| Blocking: Android 1–8 | FR-K4…K12 |
| Blocking: Backend | FR-D1…D4 |
| Blocking: Verify | `TESTING.md` §5 |
