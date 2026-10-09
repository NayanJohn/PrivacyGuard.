# PrivacyGuard — Privacy & Permission Auditor

PrivacyGuard audits how other Android apps use sensitive permissions (camera, microphone, location, SMS, contacts, storage), decides whether that use is legitimate or suspicious by comparing it with the app's foreground/background lifecycle, scores the result, and — on devices where PrivacyGuard is the **Device Owner** — can **suspend** apps whose risk score is above a threshold.

> **Data policy for this project: no fake or mock data, anywhere.** Every number shown in the UI, every row in a database and every test result must come from a real device, a real emulator, or a real backend. Empty states are rendered honestly ("No data yet"). See [TESTING.md](TESTING.md).

---

## Features

| # | Feature | Core API | Needs |
|---|---------|----------|-------|
| 1 | Lifecycle state tracking (foreground vs background) | `UsageStatsManager.queryEvents` | `PACKAGE_USAGE_STATS` (user grants in Settings) |
| 2 | Runtime hardware/permission access tracking | `AppOpsManager` (hidden APIs) via **Shizuku** | Shizuku running + granted |
| 3 | Static permission baseline | `PackageManager.getPackageInfo(GET_PERMISSIONS)` | none |
| 4 | Risk scoring & timestamp-intersection engine | in-app + backend engine | outputs of 1–3 |
| 5 | Background audit & historical logging | `WorkManager` / foreground service + SQLite | Shizuku, Usage access |
| 6 | **Hard app blocking** when risk > 90 | `DevicePolicyManager.setPackagesSuspended` | Device Owner (ADB-provisioned) |
| 7 | Parent/adult controls, alerts feed | Backend + parent portal | Backend, email |

## Architecture (one screen)

```
 Flutter UI (Dart)  ── MethodChannel ──►  Kotlin native host
   timeline charts                          ├─ UsageStatsManager     (lifecycle)
   risk dashboard                           ├─ Shizuku → AppOps      (access log)
   block / allow UI                         ├─ PackageManager        (baseline)
        ▲                                   ├─ DeviceControl (DPM)   (suspend/unsuspend)
        │ reads                             ├─ WorkManager / FGS     (background audit)
  Local DB (Room/SQLite)  ◄─────────────────┘
        │
        └── HTTPS ──► Backend: scans, scoring policy, /blocks, allow/block,
                              alerts feed, parent portal, email
```

## Requirements

- Android **minSdk 26**. Some APIs need higher levels (e.g. `ACTIVITY_STOPPED` = 29, `SHOW_SUSPENDED_APP_DETAILS` = 28); the plan handles fallbacks.
- Flutter (stable) + Android SDK + Kotlin.
- [Shizuku](https://shizuku.rikka.app/) installed and started on the audit device (wireless ADB or root).
- For hard blocking: a phone/emulator with **no accounts added** (fresh or factory-reset), USB debugging, and `adb`.

## Quick start (developer)

```bash
# 1. Build & install
flutter pub get
flutter build apk --debug
adb install -r build/app/outputs/flutter-apk/app-debug.apk

# 2. Grant Usage access inside the app (it opens Settings.ACTION_USAGE_ACCESS_SETTINGS)
# 3. Start Shizuku, then approve PrivacyGuard's Shizuku permission prompt

# 4. (Optional) enable hard blocking — device must have no accounts
adb shell dpm set-device-owner <applicationId>/<receiver class>
#    then open Setup → "Enable hard blocking" → Re-check
```

`<applicationId>` and `<receiver class>` come from your Gradle config and manifest (`PgDeviceAdminReceiver`). Full steps and troubleshooting: [guide.md](guide.md).

## Blocking rule (summary)

Block **only if** all are true:

`final_score > 90` **and** scan is **complete** (not preliminary) **and** `confidence >= 70` **and** the package is not on the never-block list **and** PrivacyGuard is Device Owner **and** (age ≥ 18, or a verified parent link exists).

Otherwise PrivacyGuard only **warns**. If the OS refuses to suspend a package, it is marked `not_blockable` and a warning state is shown — never an error, and never a pretend block.

## Documentation map

| File | Purpose |
|------|---------|
| [implementation_plan.md](implementation_plan.md) | Scope, assumptions, contracts, schemas, phased task list, risks, open decisions |
| [guide.md](guide.md) | Developer guide: setup, provisioning, API cheat-sheet, troubleshooting, policy notes |
| [workflow.md](workflow.md) | End-to-end and per-feature flow diagrams, block state machine |
| [TESTING.md](TESTING.md) | Test strategy, real-device verification, acceptance checklist |
| [BUILD_PROMPT.md](BUILD_PROMPT.md) | Ready-to-paste prompt for a coding agent to build the app |
| [stitch.md](stitch.md) | Prompt for Google Stitch to generate the UI designs |

## Read this before relying on the scores

The source specs contain a few contradictions and uncalibrated values (score direction, a 10× background multiplier that saturates the score almost immediately, undefined `confidence`). They are listed in **implementation_plan.md → §0** and must be resolved before hard blocking is enabled for real users.

## Transparency commitments

- Persistent notification + visible **"Hard blocking active"** chip while enabled.
- Block pages always name PrivacyGuard as the source.
- Every block/unblock is logged (who, when, why) and shown in the Alerts feed.
- A **Release control** button always exists and cleanly removes Device Owner.
