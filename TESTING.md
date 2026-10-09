# PrivacyGuard — Testing

## 1. Ground rules: no fake or mock data

- Tests run against **real devices or emulators**, a **real database**, and a **real backend instance** (a local/staging server with a real DB, not a stub).
- **Do not** mock `UsageStatsManager`, `AppOpsManager`, Shizuku, `DevicePolicyManager`, Room, or the HTTP layer to produce results, and do not seed databases with invented rows to make screens look populated.
- The one allowed exception is **input vectors for pure functions** (e.g. `shouldBlock(score, confidence, …)`). These are boundary values for a rule, not simulated app data, and the rule itself defines them (see §3).
- Ground truth for scoring comes from a **probe app we build** that really uses permissions in known ways (§4), plus real third-party apps used manually.
- An empty result is a valid result. Tests must assert honest empty/"unavailable" states.

## 2. Environments

| Env | Purpose |
|-----|---------|
| Emulator, no Google account, API 26 | minSdk behaviour, legacy event types |
| Emulator/phone API 28 | `SHOW_SUSPENDED_APP_DETAILS` first available |
| Phone/emulator API 29–30 | `ACTIVITY_STOPPED`, per-flag AppOps data |
| Phone API 33–34+ | notifications permission, current restrictions |
| Spare **real phone**, factory-reset, no accounts | Device Owner, reboot, update, battery behaviour |
| Local backend + PostgreSQL (or chosen DB) + real mail sink (e.g. a local SMTP capture server that receives real messages) | API and email tests |

Record device model, Android version, patch level, and Shizuku version in every result.

## 3. Unit tests (pure logic only)

**Block rule** — `shouldBlock`: one test per row, each changing a single factor.

| final_score | status | confidence | never-block | Device Owner | Expected |
|-------------|--------|------------|-------------|--------------|----------|
| 90 | complete | ≥ 70 | no | yes | **not blocked** (strict `>`) |
| 91 | complete | 70 | no | yes | **blocked** |
| 91 | complete | 69 | no | yes | warn only |
| 91 | preliminary | any | no | yes | **never blocks** |
| 100 | complete | 100 | yes (each category: dialer, default SMS, Settings, launcher, emergency, system, PrivacyGuard) | yes | not blockable |
| 91 | complete | 70 | no | no | warn-only fallback |
| 91 | complete | 70 | no | yes, age < 18, no parent link | warn only |
| 91 | complete | 70 | no | yes, age < 18, parent link | blocked |

**Other pure units:** window builder (resume/stop pairing, open window at query end, overlap merge); fg/bg classifier (inside, on boundary, outside); scoring (clamp 0–100, weights, multipliers, stacking rule, health = 100 − risk); confidence components; top-3 mismatch ranking; permission-flag parsing; AppOps/`appops` text parsing using **captured real output files** saved from a real device (store them under `testdata/real/` with device + date).

## 4. The probe app (ground-truth generator)

Build a small separate app (`probe-app`) that **really** performs, on command, with timestamps it logs itself:

| Behaviour | Notes |
|-----------|-------|
| Foreground camera use for N seconds | visible activity |
| Foreground microphone use for N seconds | visible activity |
| Foreground fine-location use | visible |
| Background location use | requires background-location grant |
| Foreground-service microphone/camera use | declare correct `foregroundServiceType`; modern Android restricts background camera/mic, so this is the realistic "background" path |
| Contacts / SMS read (as permitted) | grant on test device |
| Idle (no access) | negative control |

The probe logs its own start/stop times; tests compare PrivacyGuard's captured windows/events/duration with those logs (tolerance defined by the polling interval). A second build can be tuned to produce a very high background-use pattern to reach risk > 90 for blocking tests — or temporarily lower `BLOCK_THRESHOLD` (documented as a test override, never the default).

## 5. Integration tests (device/emulator + real backend)

| Area | Checks |
|------|--------|
| Usage access | Not granted → redirect to Settings; granted → windows returned; matches manual open/close of a real app |
| Shizuku | Not running → prompt + degraded confidence; running → events; kill Shizuku mid-scan → UI shows stopped, scan stays preliminary |
| Baseline | Output equals Settings → App permissions for ≥ 3 real apps; revoke a permission and re-read |
| Access tracking | Probe events appear once (de-dup across repeated polls) |
| Persistence | Reboot device → history still present; unique index prevents duplicates |
| Background worker | Screen off for a test period → events captured; measured gaps reflected in confidence |
| Backend | Each endpoint with real DB: `/health`, `/scans`, `/blocks`, `/apps/{pkg}/allow` (adult, parent token valid/expired/reused), `/apps/{pkg}/block`, `/alerts`, release code request/confirm (valid/wrong/expired) |
| Audit log | Every block/unblock row has actor + timestamp and appears in the Alerts feed |
| Email | Under-18 block sends a real email with app, score, reasons, working "Allow this app" link; expired token rejected |

## 6. Hard-blocking verification (from the spec — real device/emulator)

1. Provision an emulator or spare phone with **no accounts**; run `adb shell dpm set-device-owner <applicationId>/<receiver class>`; confirm `isDeviceOwner()` is `true` in the app and in `dumpsys device_policy`.
2. Install a test app that scores above 90 (probe app, or temporarily lower `BLOCK_THRESHOLD` for the test); scan it (complete, confidence ≥ 70); confirm it is **suspended**: greyed out, cannot launch, tapping opens PrivacyGuard's details page.
3. Details page shows score, top 3 mismatches, verdict, names PrivacyGuard as source, and the correct allow/request path for the age category.
4. **Never-block apps** (dialer, default SMS, Settings, launcher, emergency, a system app, PrivacyGuard) remain usable even when forced above threshold.
5. **Failed suspension reported honestly**: force a package the OS refuses; app shows `not_blockable`/warning state, logs the failed array, and never claims a block.
6. Block state **survives reboot** and an **update of the blocked app** (install a newer build of the probe app over the old one).
7. **Parent "Allow"** (under-18 account): app unsuspends within one sync cycle; audit log shows `allowed_by_parent`.
8. **Adult "Allow"/"Block"** via `POST /apps/{pkg}/allow` and `/block` works and syncs.
9. **Release control**: adult → confirm dialog; under-18 → emailed code required (wrong code fails). Result: all packages unsuspended first, Device Owner cleared (`dumpsys device_policy` no longer lists it), UI returns to warn-only.
10. **Without Device Owner**: hard blocking disabled, full-screen warning + `ACTION_DELETE` button works, text states plainly that nothing is blocked.
11. Persistent notification and "Hard blocking active" chip appear exactly while enabled; disappear after release.

## 7. UI and UX checks

Empty states on a fresh install show no numbers or charts with invented values; loading/error/permission-missing states exist; TalkBack labels; large font; dark mode; rotation/process death keeps state; no crash when Shizuku or usage access is revoked at runtime.

## 8. Non-functional

| Area | Check |
|------|-------|
| Battery | 24 h run with the audit worker; compare to baseline via `adb shell dumpsys batterystats` |
| Performance | Scan time for an app with large event history; DB size growth |
| Security | Tokens expire and are single-use; device endpoints reject missing/invalid device token; no raw logs uploaded without opt-in; TLS enforced |
| Privacy | Verify exactly which fields leave the device (capture real traffic with a proxy on the test device) |

## 9. Acceptance checklist

- [ ] §3 unit tests pass, including every boundary row.
- [ ] Probe-app ground truth matches within the documented tolerance on every device in §2.
- [ ] §6 items 1–11 verified on a real device or emulator, with evidence.
- [ ] No mock/seeded data in any build or test (grep the repo; review fixtures).
- [ ] Score-direction, saturation and confidence decisions (implementation_plan §0) signed off, and calibration recorded from real measurements.

## 10. Evidence to keep per run

Device + OS + Shizuku versions, build hash, command output (`dpm`, `dumpsys device_policy`, `appops get`), screenshots/screen recordings of greyed app and details page, backend audit-log rows, received emails (headers + body), and the probe app's own log.
