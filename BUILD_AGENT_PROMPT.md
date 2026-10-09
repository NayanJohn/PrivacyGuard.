# BUILD_AGENT_PROMPT — Read everything, then build PrivacyGuard

Put all the `.md` files below plus `roshan.md` in the project folder you open as the **Antigravity workspace** (repo root or `docs/`), then paste everything under the line into an Antigravity agent (Agent Manager or the editor's agent panel). Optional: if your Antigravity version supports workspace rules, save the STEP 0 logging contract there too so every agent session follows it (check Antigravity's settings for where rules live; I have not verified the exact path).

Files expected: `README.md`, `PRD.md`, `required.md`, `ARCHITECTURE.md`, `API_DOCUMENTATION.md`, `DATABASE_SCHEMA.md`, `DESIGN.md`, `implementation_plan.md`, `guide.md`, `workflow.md`, `TESTING.md`, `BUILD_PROMPT.md`, `stitch.md`, `roshan.md`.

---

You are the engineer building **PrivacyGuard**: an Android privacy auditor (Flutter UI + Kotlin native host) with a FastAPI/PostgreSQL backend, a React parent portal/dashboard, and optional **hard app blocking** via Device Owner. Work in the order below. Do not write application code until Step 2 is approved.

## STEP 0 — Logging contract (applies to every step)

`roshan.md` tracks **only the app-creation work you do in Antigravity** (code, config, builds, tests, device verification, build decisions, build issues). It is not a place for documentation writing or general notes. **After finishing each build task, and before starting the next, update `roshan.md`:**
1. Append a numbered entry (format is in the file): phase, type, Antigravity session/artifact reference, files touched, what you did, exact commands run, result, **Verified: YES (device + evidence) or NOT VERIFIED (reason)**, doc/API differences found, next step.
2. Update the **Status board**, **Next action**, **Open questions**, **Decisions log**, **API verification summary**, **Verification evidence index** and **Issues** sections whenever they change.
3. Never edit or delete old log entries. Never write a result you did not observe. If a command failed, log the failure.
4. Commit `roshan.md` together with the code of the task it describes.
5. Where Antigravity produces artifacts (task list, implementation plan, walkthrough, screenshots, recordings), reference them in the entry instead of pasting them. Do not claim an artifact proves a device test unless it shows the real device/emulator.
Reading the documents (STEP 1) is not logged in `roshan.md`; your understanding summary (STEP 2) goes in the chat only.

## STEP 1 — READ (no code yet)

Read every file fully, in this order:
1. `README.md`, `PRD.md`, `required.md` (what and why; requirement IDs FR-*, US-*)
2. `ARCHITECTURE.md`, `DATABASE_SCHEMA.md`, `API_DOCUMENTATION.md` (how)
3. `implementation_plan.md`, `guide.md`, `workflow.md` (plan, API cheat-sheet, flows)
4. `TESTING.md` (how it is proven), `BUILD_PROMPT.md` (binding build rules)
5. `DESIGN.md` and `stitch.md` (UI: "Spatial Sentinel" glass design, Sora font, Guardy mascot)

Then inspect the existing repository: is there already a backend with the 3-component scan engine (RiskInDroid, heuristics, MobSF/Androguard), a React dashboard, Gmail parent alerts at score ≥ 80? **Do not rebuild what exists.** Mention what you found in your STEP 2 summary. API_DOCUMENTATION marks some endpoints `[existing?]` — confirm against the real code.

## Source-of-truth precedence (when documents disagree)

1. `required.md` ground rules G1–G6 and the blocking rule
2. `DATABASE_SCHEMA.md`, `API_DOCUMENTATION.md`, `ARCHITECTURE.md`
3. `PRD.md`, `implementation_plan.md`, `workflow.md`, `guide.md`, `TESTING.md`
4. `DESIGN.md` (body section values are canonical for colours)

## Known conflicts between the documents (handle exactly like this; once building, record each resolution in the roshan.md Decisions log)

| # | Conflict | Resolution |
|---|----------|------------|
| 1 | Score direction: runtime formula `100 − penalties` vs block when `> 90` | `final_score` and `runtime_risk` are **risk, 0–100, higher = riskier**. "Safety/health = 100 − risk" is display only (ADR-3, OD-1). |
| 2 | Penalty saturation (×10 multiplier) | Clamp 0–100, apply **once per (permission, state) bucket per window**, keep the pre-clamp `rawPenalty` and full breakdown (ARCHITECTURE §3.4, OD-2). Weights/multipliers are config. Calibrate on real data before enabling blocking by default. |
| 3 | `confidence`, complete/preliminary | Backend scan status governs. Device-side confidence components (Shizuku connected, Usage access, polling coverage, event count, API sources) must be defined in code, documented, and shown in UI. Ask the owner to confirm the weights. |
| 4 | Local DB schema differs between `implementation_plan.md` §3.4 and `DATABASE_SCHEMA.md` | Use `DATABASE_SCHEMA.md` (Room entities). |
| 5 | Release endpoints: `/release/request` (plan) vs `/devices/{id}/release/request-code` (API doc) | Use `API_DOCUMENTATION.md`. |
| 6 | Verification section number: PRD says `TESTING.md` §5, hard-block checks are in §6 | Use `TESTING.md` §6. |
| 7 | `DESIGN.md` has two colour sets (YAML front-matter Material tokens vs body hex such as `#22D3EE`, `#8B5CF6`, canvas `#0B1020`) | Use the body hex values; ask the owner if the YAML tokens are wanted for the Flutter theme. |
| 8 | `GET /health` "field for nothing new" | No change to `/health` (OD-4). |
| 9 | Backend stack: older `BUILD_PROMPT.md` says "choose" | Decided by `ARCHITECTURE.md`: FastAPI + PostgreSQL + Alembic; React/Vite/TS/Tailwind portal. |
| 10 | Runtime risk vs backend static blend | `final_score` stays the backend's weighted blend; `runtime_risk` is uploaded and shown separately until the owner picks a blend weight (OD-3). |

## STEP 2 — CONFIRM UNDERSTANDING (stop and wait)

Reply with (max ~25 lines):
- what you will build, in phase order P0 → P8, mapped to FR-/US- IDs
- the blocking rule, exactly: `final_score > 90` (strict) ∧ scan `complete` ∧ `confidence >= 70` ∧ not on never-block list ∧ Device Owner ∧ (adult → user enabled | under-18 → verified parent link)
- what already exists in the repo vs what you will create
- any conflict beyond the ten above
- the Android/Shizuku APIs you must verify first
- only the questions that block P0
**Wait for my approval** before building.

## STEP 3 — BUILD (phase by phase)

After approval, execute the phases in `implementation_plan.md` §4, with the specifics below. At the end of each phase: run the phase's verification on a real device/emulator/backend, update `roshan.md`, post a short report (built / verified with output / API differences / open questions), and **wait for my go-ahead** unless I said "continue".

**P0 Foundations.** Environment check (`flutter doctor`, SDK/`compileSdk`, JDK 17, `adb`). Repo layout per `guide.md` §2 plus `backend/` and `portal/`. MethodChannel `pg/native` with the method list from `implementation_plan.md` §3.3; unimplemented methods return an explicit "not implemented" error, never placeholder data. Backend skeleton with `GET /health` and a config module (`BLOCK_THRESHOLD=90`, `BLOCK_MIN_CONFIDENCE=70`, weights, multipliers, TTLs, never-block list). Alembic set up. **Read the real signatures** of every API in `guide.md` §6 and Shizuku's API in the installed version; write `docs/api_verification.md`; record every difference from the docs in `roshan.md`.

**P1–P5 (device audit).** Exactly as `required.md` §1.1–1.5 and `ARCHITECTURE.md` §3: `UsageTracker`, `PermissionBaseline`, `AppOpsReader` (UserService / `ShizukuBinderWrapper`, history ops first then shell fallback, relative-time conversion), `RuntimeScorer` (pure), `AuditWorker`, Room per `DATABASE_SCHEMA.md` §2 with unique indexes and cursor. Build the **probe app** (`TESTING.md` §4) in P1/P3 to get real ground truth. When Shizuku or Usage access is missing, show honest "unavailable" states and lower confidence.

**P6 Backend.** Alembic migrations per `DATABASE_SCHEMA.md` §1 (apply `ALTER` equivalents where tables exist; no seed data). Endpoints per `API_DOCUMENTATION.md`: `/blocks`, `/apps/{package}/allow`, `/apps/{package}/block`, `/devices/register`, `/devices/{id}/status`, `/scans`, `/scans/{id}`, `/devices/{id}/runtime-summaries`, release request/confirm, parent portal, `/alerts`. Server block-decision logic as in API doc §4. Append-only `block_events`, hashed single-use tokens, rate limits, idempotency keys. Real email sending (SMTP/Gmail) and the parent portal "Allow this app" page. Keep the ≥ 80 parent alert working and separate. Tests for the rule (90 not blocked; 91 blocked only when final and confidence ≥ 70; preliminary never blocks; never-block respected) against a real PostgreSQL.

**P7 Hard blocking.** `PgDeviceAdminReceiver`, `device_admin.xml`, manifest; `DeviceControl` (isDeviceOwner, suspend/unsuspend returning the failed-packages array, isSuspended, release); `NeverBlockResolver` (dialer, default SMS, launcher, `FLAG_SYSTEM`, Settings, emergency apps, default IME, Shizuku Manager, own package, backend allowlist); setup screen with the real `adb shell dpm set-device-owner <applicationId>/<receiver class>` and **Re-check**; post-scan suspend; `SuspendedAppDetailsActivity` for `SHOW_SUSPENDED_APP_DETAILS`; `BlockSyncWorker` + on-open sync + reboot/update handling (verify whether the OS keeps suspension); Release control (build and test this **first** inside P7); persistent notification + "Hard blocking active" chip; warn-only fallback (full-screen warning + `ACTION_DELETE`). `blocked` is persisted/reported only after `setPackagesSuspended` did not list the package as failed **and** `isPackageSuspended` confirms it (G3).

**P8 Hardening + UI.** Build Flutter screens and the portal to `DESIGN.md` (glass surfaces, Sora, severity chips, Guardy mascot states) using the screen list in `stitch.md`. Every screen needs loading / empty / error / permission-missing states, no invented values. Accessibility (48dp targets, TalkBack, large font). Run the `TESTING.md` §9 acceptance checklist.

## Non-negotiable rules

1. **No fake or mock data**: no seed rows, sample apps/scores, placeholder charts, mocked system services or stubbed backends. Pure-function boundary vectors in unit tests are the only exception. Empty states are honest.
2. **Verify every Android API against the installed SDK**; report differences explicitly (log them).
3. **Never claim a block that did not happen**; failed suspension → `not_blockable` warning state, logged, not a crash.
4. **No hidden behaviour**: persistent notification, visible chip, block page names PrivacyGuard, Release control always reachable, every block/unblock logged with who/when and shown in Alerts.
5. Never claim something works unless you ran it and can show output. If no device is available, say **NOT VERIFIED**.
6. Never hard-code `BASE_URL`, secrets or tokens; use environment/config. Tokens and codes are stored hashed.
7. Small commits, one feature each; no large unreviewed dumps. Do not start the next phase before the current phase's exit criteria pass or I approve an exception.
8. If you are unsure, ask — do not silently choose. Put the question in `roshan.md` → Open questions.

## Report format at the end of each phase

```
PHASE Pn — <name>
Built: <list>
Verified (real device/emulator/backend): <commands + output, or NOT VERIFIED + reason>
API/doc differences: <list>
roshan.md updated: entries NNNN–NNNN
Open questions: <list>
Next: <phase / task>
```

Begin with STEP 0 and STEP 1 now.
