# PrivacyGuard — Product Requirements Document

**Tagline:** Understand Before You Trust
**Module covered:** Runtime Privacy Audit + Hard App Blocking
**Status:** Draft for build

---

## 1. Problem

Android shows what an app *can* ask for, not what it *actually does* while you aren't looking. Apps can touch the camera, microphone, location, contacts or SMS while the phone is in a pocket. Users — and parents of minors — have no explainable, time-aware view of this, and no enforcement when an app is clearly abusive.

## 2. Product vision

PrivacyGuard audits installed apps three ways — static analysis (backend engine), declared/granted permissions, and **runtime access timed against foreground/background state** — turns them into an explainable risk score, and, when the score is above 90 with high confidence, **suspends the app** so it cannot be opened.

## 3. Goals / non-goals

**Goals**
1. Explain *why* an app is risky: every score ships with its contributing signals.
2. Distinguish foreground (expected) from background (suspicious) hardware access.
3. Enforce a hard block for the clearest cases, safely and transparently.
4. Keep parents in control for under-18 users; keep adults in control of themselves.
5. Be honest: no fake data, no pretend blocks, no hidden behavior.

**Non-goals**
- Not an antivirus/malware remover; no network traffic inspection.
- Not covert monitoring — the child and parent both see the system exists.
- Not Play-Store distributable in this form (Device Owner, Shizuku, `QUERY_ALL_PACKAGES`).
- No iOS.

## 4. Personas

| Persona | Needs |
|---------|-------|
| **Adult user (18+)** | Know what apps do; block bad ones; override or release at will. |
| **Minor user (<18)** | Clear explanation of why an app is blocked; a way to request permission. |
| **Parent** | Email when a block or high-risk app happens; one-tap Allow; control of release. |
| **Hackathon judge / analyst** | See real scores with explanations and a working end-to-end block on a real device. |

## 5. User stories and acceptance criteria

### Audit
- **US-1** As a user I grant Usage Access once and see foreground sessions per app.
  *AC:* without the grant the app shows a "Grant Usage Access" state and never shows sessions.
- **US-2** As a user I see which permissions an app has been **granted** vs only requested.
  *AC:* matches `PackageManager` flags for the real package.
- **US-3** As a user I see every camera/mic/location/SMS/contacts access with time, duration and whether it was foreground or background.
  *AC:* requires Shizuku; if absent, a clear "Start Shizuku" state replaces the data.
- **US-4** As a user I see a risk score with a per-permission breakdown and a trend over time.
  *AC:* breakdown sums to the score (before clamping, shown explicitly).

### Blocking
- **US-5** As an adult, when an app is scanned complete with `final_score > 90` and `confidence >= 70`, it is suspended and I'm notified.
  *AC:* app is greyed out, cannot launch; tapping it opens PrivacyGuard's "Why this app is blocked".
- **US-6** As a user I can see an exact reason page: score, top 3 mismatches, verdict, who blocked it and when.
- **US-7** As an adult I can **Allow** a blocked app (→ `allowed_by_user`) and re-block later.
- **US-8** As a parent I get an email with app, score, reasons and an **Allow this app** button that expires.
  *AC:* click → `allowed_by_parent`; the app unsuspends within one sync cycle.
- **US-9** As a user, if the phone is not Device Owner, I'm told hard blocking is off and I get warn-only mode (full-screen warning + uninstall button).
- **US-10** As a user, system-critical apps are never suspended; if the OS refuses a suspension, I see a warning state, not a crash.
- **US-11** As an adult I can **Release control**; as a minor this needs a parent's emailed confirmation code.

### Transparency
- **US-12** While hard blocking is on I always see a persistent notification and a "Hard blocking active" chip.
- **US-13** The Alerts feed lists every block/unblock with who and when.

## 6. Feature priorities

| Priority | Features |
|----------|----------|
| **P0** | Usage lifecycle tracker, baseline extractor, Shizuku AppOps reader, scoring/intersection, block rule + tests, Device Owner setup screen, suspend/unsuspend, block details page, warn-only fallback |
| **P1** | `GET /blocks` sync + WorkManager, backend block fields + endpoints + audit log, parent email + magic-link Allow, never-block list, reboot/update re-apply |
| **P2** | Release-control flow with parent code, history charts, Alerts feed polish |
| **P3** | Foreground-service continuous mode, tuning weights from real data, localization |

## 7. UX principles

1. **Say what is true.** Blocked / Not blockable / Warn-only / Hard blocking unavailable are four different states and look different.
2. **Explain before enforcing.** Block page leads with the reasons, not just "denied".
3. **No dark patterns.** The control-release path is always reachable.
4. **Empty is fine.** Zero scans, zero events → empty states, never filler.

## 8. Success metrics (measured on real devices only)

| Metric | How measured |
|--------|--------------|
| End-to-end block works | Verification checklist in `TESTING.md` §5 passes on an emulator/spare phone |
| False-block rate | Count of blocks later overridden with `allowed_by_*`, from the real block-event log |
| Time from scan-complete to suspended | Timestamps in `block_events` |
| Sync convergence | Parent Allow → device unsuspended within one sync interval |
| Explainability | Every block page shows ≥ 1 reason sourced from the engine |

*(No numeric targets are invented here; set them after the first real measurements.)*

## 9. Risks

| Risk | Mitigation |
|------|------------|
| Device Owner needs a no-accounts device | Setup screen explains; warn-only fallback; test on emulator/spare phone |
| Shizuku must be restarted after reboot | Detect and guide; degrade honestly |
| Runtime score saturates (×10) | Clamp + per-bucket rule + config (OD-2) |
| Blocking a legitimately needed app | Never-block list, confidence gate, Allow paths, release |
| Misuse as stalkerware | Visible chip + notification, child informed, parent link verified, source named on block page |
| API behavior differs by Android version | Verify-every-API rule, per-API test matrix |

## 10. Legal / ethics

- Informed disclosure to minors and parents on first run.
- Parent link is verified before any parent power applies.
- Data minimization: raw logs local; summaries synced.
- Prototype for a cybersecurity hackathon; production use would need legal review.

## 11. Dependencies

Android 8.0+ (API 26), Shizuku, FastAPI backend with the 3-component engine (RiskInDroid, heuristics, MobSF/Androguard), PostgreSQL, SMTP/Gmail sender, React dashboard (parent portal).

## 12. Open questions

See `required.md` §4 (OD-1 … OD-7).
