# BUILD_PROMPT — PrivacyGuard

Paste everything below the line into your coding agent (e.g. Claude Code) at the repo root. Keep the other `.md` files in the repo; the agent should read them.

---

You are building **PrivacyGuard**, an Android privacy auditor with optional hard app blocking. Read `README.md`, `implementation_plan.md`, `guide.md`, `workflow.md` and `TESTING.md` first; they are the source of truth. Work phase by phase (P0 → P8 in `implementation_plan.md`) and stop at each phase's exit criteria to verify.

## Stack
- Flutter (UI, Dart) + Kotlin native host over `MethodChannel` (`pg/native`).
- Native: `UsageStatsManager`, Shizuku + `AppOpsManager`, `PackageManager`, `DevicePolicyManager` (Device Owner), `WorkManager`, Room.
- Backend: REST API + relational DB + mailer + parent portal (choose a stack, justify in one paragraph, and confirm with me before building if my answer to open decision #4 is missing).
- minSdk 26. Handle API differences (e.g. API 29 event constants, API 28 `SHOW_SUSPENDED_APP_DETAILS`).

## Non-negotiable rules
1. **No fake or mock data.** No seeded rows, hard-coded sample apps/scores, fake charts, or mocked system services. Empty/unavailable states must be shown honestly. Pure-function boundary vectors in unit tests are the only exception.
2. **Verify every Android API against the installed SDK** (read the real signatures in `android.jar`/sources, including hidden/`@SystemApi` status, min API, deprecation, and required permissions). If anything differs from my docs, **tell me explicitly** in the phase report and adapt the design.
3. **Never pretend a block happened when it did not.** Persist `blocked` only after `setPackagesSuspended` succeeded for that package; handle its returned failed-packages array → `not_blockable` + logged warning state (not an error).
4. **Transparency:** persistent notification and a visible "Hard blocking active" chip while enabled; block pages always name PrivacyGuard; a working **Release control** button; every block/unblock logged with who/when and shown in the Alerts feed.
5. Report discrepancies and ambiguities instead of silently choosing. Specifically resolve with me before P4/P7: score direction (use `risk_score`, higher = riskier, `privacy_health = 100 − risk`), penalty saturation/stacking, definition of `confidence` and complete/preliminary, `GET /health` instruction.

## Blocking rule (implement exactly, as a pure, tested function)
Block only when `final_score > 90` (90 is **not** blocked) **and** scan is **complete** (never preliminary) **and** `confidence >= 70`; otherwise warn only. `BLOCK_THRESHOLD=90` and the confidence minimum live in backend config. Also require: not on never-block list (default dialer, default SMS app, Settings, launcher(s), emergency apps, system packages, PrivacyGuard itself — resolved at runtime), PrivacyGuard is Device Owner, and (age ≥ 18 → user enabled; age < 18 → verified parent link).

## Android deliverables (hard blocking)
1. `PgDeviceAdminReceiver : DeviceAdminReceiver` + `res/xml/device_admin.xml` + manifest entry.
2. `DeviceControl`: `isDeviceOwner()` (`dpm.isDeviceOwnerApp`), `suspend`/`unsuspend` (`dpm.setPackagesSuspended(admin, arrayOf(pkg), true/false)`), `isSuspended` (`dpm.isPackageSuspended`); return the failed-packages array to callers.
3. Setup screen "Enable hard blocking": detects Device Owner; if not, shows `adb shell dpm set-device-owner <applicationId>/<receiver class>` (filled with the real values), the "no accounts / fresh or factory-reset / emulator without Google account" explanation, and a **Re-check** button. If not Device Owner → hard blocking disabled, warn-only fallback (full-screen warning + `ACTION_DELETE` button).
4. After each completed scan: if the rule matches → `suspend(pkg)`, save `block_state` with reasons, notify.
5. Activity handling `android.intent.action.SHOW_SUSPENDED_APP_DETAILS` (API 28+): "Why this app is blocked" (score, top 3 mismatches, verdict, allow/request path by age).
6. Block sync: WorkManager + on app open → `GET /blocks`; apply suspend/unsuspend; re-apply after reboot and target-app updates (verify whether needed).
7. Release control: unsuspend all, then `clearDeviceOwnerApp` (verify availability/deprecation); 18+ confirm dialog, <18 emailed parent code.
8. Audit features 1–5 per `implementation_plan.md` (lifecycle, AppOps via Shizuku, baseline, scoring/intersection engine, background logging).

## Backend deliverables
Fields `block_state` (`none | warned | blocked | allowed_by_parent | allowed_by_user | not_blockable`), `block_reason` (top 3 mismatches), `blocked_at`. Endpoints: `GET /blocks`, `POST /apps/{package}/allow` (adult or parent magic-link token), `POST /apps/{package}/block`, plus `POST /scans`, `GET /alerts`, release-code endpoints, `GET /health`. Under-18 block → email parent (app, score, reasons, "Allow this app" button, expiring token); allowing sets `allowed_by_parent` and the device unsuspends on next sync. Append-only audit log. Tests for the rule: score 90 not blocked; 91 blocked only if final and confidence ≥ 70; preliminary never blocks; never-block list respected.

## Verification (real devices/emulator — see `TESTING.md` §6)
Provision a no-account emulator/spare phone via ADB and confirm `isDeviceOwner()` is true; build and use the **probe app** to generate real, known access patterns; scan, confirm suspension (greyed, cannot launch, tap opens details page); confirm never-block apps stay usable, a refused suspension is reported honestly, state survives reboot and app update; parent Allow unsuspends within one sync cycle; Release control removes Device Owner cleanly; without Device Owner the app falls back to warn-only and says so.

## How to work and report
- Plan the phase, implement, run the verification for that phase on a real device/emulator, then post a short report: what was built, what was verified (with command output), **API differences found**, open questions.
- Small commits per feature; no large unreviewed dumps.
- Do not start the next phase until the current phase's exit criteria pass or I approve the exception.
- If you cannot verify something (no device available), say so — never claim it passed.
