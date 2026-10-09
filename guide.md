# PrivacyGuard — Developer Guide

Practical how-to for building, provisioning and debugging. For the "what and why" see [implementation_plan.md](implementation_plan.md).

> No fake/mock data. If a source is unavailable (no Shizuku, no usage access), the app must say so and lower confidence — not fill in values.

---

## 1. Prerequisites

| Need | Notes |
|------|-------|
| Flutter stable, Android SDK, JDK 17+, Kotlin | `compileSdk` should be the latest installed so you can read real signatures |
| `adb` | USB debugging or wireless debugging |
| Shizuku app on the audit device | Start via wireless ADB (Android 11+) or root; must be restarted after each reboot when using ADB |
| Test device: **real phone** (spare) or **emulator without Google account** | Needed for Device Owner |

## 2. Suggested project layout

```
privacyguard/
├─ app/ (Flutter)           lib/ui, lib/state, lib/channel
├─ android/app/src/main/kotlin/…/
│   ├─ bridge/              MethodChannel handlers
│   ├─ tracking/            LifecycleTracker, AccessTracker, BaselineReader
│   ├─ scoring/             ScoringEngine, BlockPolicy (pure Kotlin)
│   ├─ control/             DeviceControl, PgDeviceAdminReceiver, BlockSync
│   ├─ work/                AuditWorker, BootReceiver
│   └─ data/                Room entities, DAOs
├─ android/app/src/main/res/xml/device_admin.xml
├─ backend/
├─ probe-app/               real test app that exercises permissions (see TESTING.md)
└─ docs/ (these .md files)
```

## 3. Permissions the user must grant

1. **Usage access** — cannot be requested at runtime. Check with `AppOpsManager.checkOpNoThrow(OPSTR_GET_USAGE_STATS, uid, pkg)` and if not allowed, open `Settings.ACTION_USAGE_ACCESS_SETTINGS`.
2. **Shizuku permission** — `Shizuku.pingBinder()` → `Shizuku.checkSelfPermission()` → `Shizuku.requestPermission(code)`.
3. **Notifications** (API 33+) for the persistent blocking notification and alerts.
4. **Device Owner** — provisioned via ADB only (next section).

## 4. Provisioning Device Owner (hard blocking)

Prerequisites: **no accounts** on the device (Google, work, etc.), user setup complete, only the primary user (some Android versions are stricter — read the error text).

```bash
# 1. install the app
adb install -r app-debug.apk

# 2. make it Device Owner
adb shell dpm set-device-owner <applicationId>/<receiver class>
#   e.g. <applicationId>/.control.PgDeviceAdminReceiver   (use your real values)

# 3. confirm
adb shell dumpsys device_policy | grep -i "device owner"
```

Common errors

| Message | Meaning / fix |
|---------|---------------|
| `Not allowed to set the device owner because there are already some accounts on the device` | Remove all accounts or factory-reset; on emulators use an image without Google APIs/Play |
| `…already several users on the device` | Remove secondary users |
| `Unknown admin` / receiver not found | Wrong class name or receiver not exported/registered; check manifest + `device_admin.xml` |
| `…device is already provisioned` (older behaviour) | Factory-reset, or use an emulator you can wipe; read the exact message on your API level |

**Removing it:** the supported path is the in-app **Release control** button (`DevicePolicyManager.clearDeviceOwnerApp`; confirm availability/deprecation in your SDK). `adb shell dpm remove-active-admin` only works for test-only builds (`android:testOnly="true"`, as produced by some debug installs). **A normal Device Owner you cannot clear in-app requires a factory reset — test Release control before anything else.**

## 5. Shizuku setup

1. Install Shizuku, start it (wireless debugging pairing on Android 11+, or `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`; check the Shizuku app for the current command).
2. In PrivacyGuard add the Shizuku API dependency and `ShizukuProvider` per the Shizuku docs.
3. Use a **UserService** (privileged process) for AppOps calls — direct `Shizuku.newProcess` is not available to apps in recent Shizuku versions; confirm against the version you depend on.
4. Handle binder death: register `Shizuku.addBinderDeadListener`, flip the UI to "Shizuku stopped", mark in-flight scans `preliminary`.

## 6. API cheat-sheet (verify each against your SDK)

| Need | API | Min API | Gotchas |
|------|-----|---------|---------|
| Foreground start | `UsageEvents.Event.ACTIVITY_RESUMED` (=1) | 29 (`MOVE_TO_FOREGROUND`=1 earlier) | Same numeric value; constant renamed/deprecated |
| Foreground end | `ACTIVITY_STOPPED` (=23) | 29 | Below 29 use `MOVE_TO_BACKGROUND` (=2). Pair events per component/instance, not just per package |
| Events | `UsageStatsManager.queryEvents(begin, end)` | 21 | Retention is limited — persist windows as you capture |
| Baseline | `PackageManager.getPackageInfo(pkg, GET_PERMISSIONS)` → `requestedPermissions[i]` + `requestedPermissionsFlags[i] & REQUESTED_PERMISSION_GRANTED` | 16 | Package visibility rules (API 30+) via `<queries>`/`QUERY_ALL_PACKAGES` |
| Access log | `AppOpsManager.getPackagesForOps(int[])` (hidden) → `PackageOps` → `OpEntry` | hidden | Run in Shizuku's process; per-flag accessors (last access fg/bg, reject time, duration) exist from API 29 — check exact names in your SDK |
| Access via shell | `appops get <pkg>` | — | Text output; parse defensively; usually only the **latest** access per op |
| Suspend | `DevicePolicyManager.setPackagesSuspended(admin, String[], boolean)` → failed packages | 24 | Returns packages that could not be suspended; must be Device/Profile Owner |
| Query suspend | `DevicePolicyManager.isPackageSuspended(admin, pkg)` | 24 | Throws `NameNotFoundException` if not installed |
| Details screen | intent action `android.intent.action.SHOW_SUSPENDED_APP_DETAILS` | 28 | Activity must be in the suspending package; check required permission/attributes |
| Owner check | `dpm.isDeviceOwnerApp(packageName)` | 18 | — |
| Release | `dpm.clearDeviceOwnerApp(packageName)` | 18 | Suspended packages should be un-suspended first; check deprecation |
| Periodic job | `WorkManager` | — | Minimum periodic interval 15 min, OEM battery limits |

Operation constants to track: `OPSTR_CAMERA`, `OPSTR_RECORD_AUDIO`, `OPSTR_FINE_LOCATION`, `OPSTR_READ_CONTACTS`, `OPSTR_READ_SMS` (and storage ops). Prefer the string constants (stable) over numeric `OP_*` ints.

## 7. Implementation tips per feature

**Lifecycle.** Build windows by pairing resume/stop per `(package, className)`; close any window still open at query end with `end = queryEnd` and mark it open. Merge overlapping windows from multi-activity apps.

**Access tracking.** Store `(pkg, op, accessTime, duration, rejectTime, source)`. De-duplicate with the unique index. Record `lastCheckedMs` so each poll is incremental. Keep which source produced the row (hidden API vs shell).

**Scoring.** Keep `ScoringEngine` pure (inputs in, outputs out) so it is unit-testable without Android. Weights/multipliers/threshold come from config, not constants.

**Background audit.** WorkManager is fine for coarse history; use a foreground service only if you need tighter polling, and then show its notification. Account for gaps in `confidence`.

**Blocking.**
- After each **complete** scan call the pure `shouldBlock`. If true → `DeviceControl.suspend`. Persist state only **after** the DPM call, using its actual result.
- If the package is in the failed array → `not_blockable`, show warning state, log it.
- Never block the never-block set (resolve the default dialer via `TelecomManager`, default SMS via `Telephony.Sms.getDefaultSmsPackage`, launchers via `ACTION_MAIN`+`CATEGORY_HOME` query, Settings, emergency apps, system flag `ApplicationInfo.FLAG_SYSTEM`, and your own package).
- Block page (`SHOW_SUSPENDED_APP_DETAILS`): read the suspended package from the intent extras (verify the extra name on your SDK), show score, top-3 mismatches, verdict, and the allow/request path by age category.
- Sync: on app open and in a WorkManager job call `GET /blocks`; diff against `isSuspended`; apply; report failures. Re-run after boot and package updates.
- **Warn-only fallback** (no Device Owner): full-screen warning + `Intent.ACTION_DELETE` (`package:<name>`) uninstall button. State text must say "Not blocked — hard blocking is off."

## 8. Configuration (server-owned)

| Key | Default | Notes |
|-----|---------|-------|
| `BLOCK_THRESHOLD` | 90 | strict `>` |
| `MIN_CONFIDENCE` | 70 | `>=` |
| weights / multipliers | from Doc 1 | configurable; see calibration note |
| never-block rules | see above | resolved on device |
| parent token TTL | set by you | expiring magic links |
| release-code TTL | set by you | one-time |

## 9. Troubleshooting

| Symptom | Likely cause |
|---------|--------------|
| `getAccessEvents` empty | Shizuku not running/permitted, or app not yet used since start; check `getAccessStatus` |
| Usage windows empty | Usage access not granted, or queried outside retention |
| `SecurityException` on AppOps | Not running in Shizuku's process |
| Suspend returns the package as failed | OS refuses (launcher, dialer, SMS, settings, admin package, etc.) → `not_blockable` |
| Block page never opens | Missing intent filter/permission on the details activity, or not in the suspending package |
| App became launchable again | Sync did not re-run; check receivers and WorkManager constraints; verify OS persistence |
| Scores always 0 or always 100 | Weights/multiplier saturation — see implementation_plan §0.2 |

## 10. Distribution & policy notes

Usage access, `QUERY_ALL_PACKAGES`, accessibility-adjacent behaviour and Device Owner are policy-sensitive on public stores, and Device Owner requires an unprovisioned device. Plan for side-loaded or managed distribution, review store policy before submission, and describe the data use plainly in the privacy policy.

## 11. Ethics & transparency

Blocking is visible and attributable: persistent notification, "Hard blocking active" chip, block pages name PrivacyGuard, every action is logged with who/when, and **Release control** always works. Minors' controls require a verified parent link; adults control their own blocks.

## 12. Glossary

**Device Owner** — app with the highest device-management role, set by ADB at setup. **Suspended** — package disabled for launch; icon greyed. **AppOps** — Android's per-operation permission accounting. **Shizuku** — lets an app call privileged APIs via a shell-level process. **Preliminary / complete scan** — whether enough real data has been observed to trust the result.
