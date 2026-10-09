# PrivacyGuard — Workflows

Diagrams use [Mermaid](https://mermaid.js.org/) (rendered by GitHub, VS Code, etc.). All values come from real devices and services — no mock data.

---

## 1. End-to-end overview

```mermaid
flowchart TD
    A[First run] --> B[Setup: Usage access, Shizuku, optional Device Owner]
    B --> C[Capture: lifecycle windows + AppOps events + permission baseline]
    C --> D[Scoring engine: intersect timestamps, weight, confidence]
    D --> E{Scan complete?}
    E -- no --> P[Mark preliminary and keep collecting]
    P --> C
    E -- yes --> F{Block rule matches?}
    F -- no --> G[Warn only / none]
    F -- yes --> H{Device Owner?}
    H -- no --> I[Warn-only fallback: full-screen warning + Uninstall button]
    H -- yes --> J[suspend package]
    J --> K{Package in failed array?}
    K -- yes --> L[not_blockable + warning state + log]
    K -- no --> M[block_state = blocked + notify + alert + email parent if under 18]
```

## 2. Setup flow

```mermaid
flowchart TD
    S[Open app] --> U{Usage access granted?}
    U -- no --> U1[Open ACTION_USAGE_ACCESS_SETTINGS] --> U
    U -- yes --> Z{Shizuku binder alive?}
    Z -- no --> Z1[Prompt user to start Shizuku] --> Z
    Z -- yes --> ZP{Shizuku permission granted?}
    ZP -- no --> ZR[requestPermission] --> ZP
    ZP -- yes --> R[Audit ready]
    R --> HB[Setup: Enable hard blocking]
    HB --> DO{isDeviceOwnerApp?}
    DO -- yes --> ON[Hard blocking active chip + persistent notification]
    DO -- no --> ADB[Show ADB command + 'no accounts' note + Re-check]
    ADB --> DO
    ADB --> WO[Skip: warn-only mode, clearly labelled]
```

## 3. Scan pipeline

```mermaid
sequenceDiagram
    participant UI as Flutter UI
    participant N as Kotlin host
    participant US as UsageStatsManager
    participant SH as Shizuku/AppOps
    participant PM as PackageManager
    participant DB as Room
    participant BE as Backend
    UI->>N: runScan(pkg)
    N->>US: queryEvents(window)
    US-->>N: resume/stop events
    N->>SH: read ops (camera, mic, location, contacts, SMS, storage)
    SH-->>N: access time, duration, reject time
    N->>PM: getPackageInfo(GET_PERMISSIONS)
    PM-->>N: requested permissions + flags
    N->>N: build windows, classify fg/bg, weight, confidence, top-3 mismatches
    N->>DB: persist events + scan
    N->>BE: POST /scans (derived summary)
    BE-->>N: policy decision (block_state)
    N-->>UI: scan result
```

## 4. Block decision

```mermaid
flowchart TD
    A[Completed scan] --> B{status = complete?}
    B -- no --> W[Warn only]
    B -- yes --> C{final_score > 90?}
    C -- no --> W
    C -- yes --> D{confidence >= 70?}
    D -- no --> W
    D -- yes --> E{On never-block list?}
    E -- yes --> NB[not_blockable]
    E -- no --> F{Age >= 18?}
    F -- yes --> G{User enabled hard blocking?}
    F -- no --> H{Verified parent link?}
    G -- no --> W
    H -- no --> W
    G -- yes --> X[suspend]
    H -- yes --> X
```

Boundaries: score **90 → not blocked**, **91 → blocked** (only if complete and confidence ≥ 70).

## 5. Block state machine

```mermaid
stateDiagram-v2
    [*] --> none
    none --> warned: score high but gate not met
    none --> blocked: rule matches and suspend ok
    warned --> blocked: later scan matches
    none --> not_blockable: OS refused / never-block
    warned --> not_blockable
    blocked --> allowed_by_user: adult allows
    blocked --> allowed_by_parent: parent allows
    allowed_by_user --> blocked: manual re-block
    allowed_by_parent --> blocked: manual re-block
    blocked --> none: release control / uninstall
```

## 6. Under-18: parent allow

```mermaid
sequenceDiagram
    participant D as Device
    participant BE as Backend
    participant P as Parent (email + portal)
    D->>BE: scan result (blocked)
    BE->>P: email: app, score, reasons, 'Allow this app' (expiring token)
    P->>BE: open portal link, press Allow
    BE->>BE: block_state = allowed_by_parent, audit log (who, when)
    D->>BE: GET /blocks (sync job or app open)
    BE-->>D: app no longer in blocked list
    D->>D: unsuspend(pkg) and update UI
```

## 7. Adult: allow / re-block

```mermaid
flowchart LR
    A[Blocked app page] --> B[Allow]
    B --> C[POST /apps/pkg/allow]
    C --> D[allowed_by_user]
    D --> E[Next sync: unsuspend]
    E --> F[Manual re-block: POST /apps/pkg/block]
    F --> G[Next sync: suspend]
```

## 8. Block sync loop

```mermaid
flowchart TD
    T[Triggers: app open, WorkManager, reboot, target app updated] --> G[GET /blocks]
    G --> D[Diff vs isSuspended per package]
    D --> S{Needs suspend?}
    D --> U{Needs unsuspend?}
    S --> SS[setPackagesSuspended true]
    U --> UU[setPackagesSuspended false]
    SS --> R[Collect failed packages]
    UU --> R
    R --> L[Log failures honestly, set not_blockable / warning state]
```

## 9. Suspended-app tap

```mermaid
flowchart LR
    A[User taps greyed app] --> B[System dialog: app unavailable]
    B --> C[More details]
    C --> D[SHOW_SUSPENDED_APP_DETAILS]
    D --> E[PrivacyGuard: Why this app is blocked]
    E --> F[Score, top 3 mismatches, verdict, source = PrivacyGuard]
    F --> G{Age category}
    G -- 18+ --> H[Allow button]
    G -- under 18 --> I[Request parent permission]
```

## 10. Release control

```mermaid
flowchart TD
    A[Settings: Release control] --> B{Age >= 18?}
    B -- yes --> C[Confirm dialog]
    B -- no --> D[Request code, emailed by backend to parent] --> E[Enter code]
    C --> F[Unsuspend every suspended package]
    E --> F
    F --> G[clearDeviceOwnerApp]
    G --> H{Success?}
    H -- yes --> I[Hard blocking off, warn-only mode, chip removed]
    H -- no --> J[Show real error; packages already unsuspended]
```

## 11. Background audit

```mermaid
flowchart TD
    A[WorkManager / foreground service tick] --> B[Check Shizuku + usage access]
    B -- missing --> C[Record gap, lower confidence, notify]
    B -- ok --> D[Query AppOps and UsageEvents since last checked time]
    D --> E[Insert only new events - unique index]
    E --> F[Update windows, rescore if needed]
    F --> G[Advance sync cursor]
    G --> H[Flutter reads DB on open and renders timeline/trends]
```

## 12. Warn-only fallback (no Device Owner)

```mermaid
flowchart LR
    A[Score high] --> B{Device Owner?}
    B -- no --> C[Full-screen warning]
    C --> D[Uninstall button: ACTION_DELETE]
    C --> E[State text: Not blocked - hard blocking is off]
    B -- yes --> F[Hard block path]
```
