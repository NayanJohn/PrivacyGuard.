# PrivacyGuard — Database Schema

Two stores: **PostgreSQL** (backend, source of truth for scans, blocks, parent links, audit log) and **Room/SQLite** (device, raw runtime data + local block state).
No seed data. Tables start empty; migrations create structure only.

---

## 1. PostgreSQL (backend)

> The backend already has scan tables from the 3-component engine. The DDL below shows the **required fields**; where a table exists, apply the `ALTER` equivalents instead of re-creating it (confirm against your current schema).

```sql
-- Enums
CREATE TYPE age_category   AS ENUM ('under_18', 'adult');
CREATE TYPE scan_status    AS ENUM ('preliminary', 'complete', 'failed');
CREATE TYPE block_state    AS ENUM ('none', 'warned', 'blocked',
                                    'allowed_by_parent', 'allowed_by_user', 'not_blockable');
CREATE TYPE actor_type     AS ENUM ('system', 'adult_user', 'parent', 'device_sync');
CREATE TYPE block_action   AS ENUM ('block', 'unblock', 'allow', 'manual_block',
                                    'not_blockable', 'release_control');
CREATE TYPE token_kind     AS ENUM ('parent_allow', 'release_code');

-- Users and parent links
CREATE TABLE users (
  id            UUID PRIMARY KEY,
  email         TEXT UNIQUE NOT NULL,
  age_category  age_category NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE parent_links (
  id             UUID PRIMARY KEY,
  child_user_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  parent_email   TEXT NOT NULL,
  verified_at    TIMESTAMPTZ,                 -- NULL = not verified → no blocking for minors
  revoked_at     TIMESTAMPTZ,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (child_user_id, parent_email)
);

-- Devices
CREATE TABLE devices (
  id                    UUID PRIMARY KEY,
  user_id               UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  label                 TEXT,
  sdk_int               INTEGER NOT NULL,
  device_owner_active   BOOLEAN NOT NULL DEFAULT FALSE,   -- reported by the device
  token_hash            TEXT NOT NULL,
  last_seen_at          TIMESTAMPTZ,
  last_sync_at          TIMESTAMPTZ,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Apps (one row per package + version + apk hash)
CREATE TABLE apps (
  id            UUID PRIMARY KEY,
  package_name  TEXT NOT NULL,
  label         TEXT,
  version_code  BIGINT,
  version_name  TEXT,
  apk_sha256    TEXT,
  first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (package_name, version_code, apk_sha256)
);

-- Scans (existing table: add the block fields)
CREATE TABLE scans (
  id              UUID PRIMARY KEY,
  app_id          UUID NOT NULL REFERENCES apps(id),
  device_id       UUID REFERENCES devices(id),
  status          scan_status NOT NULL,
  ml_risk         NUMERIC(5,2),
  heuristic_risk  NUMERIC(5,2),
  static_risk     NUMERIC(5,2),
  runtime_risk    NUMERIC(5,2),               -- uploaded by device; NULL until reported
  final_score     NUMERIC(5,2),               -- 0–100, higher = riskier
  confidence      NUMERIC(5,2),               -- 0–100
  verdict         TEXT,
  block_state     block_state NOT NULL DEFAULT 'none',
  block_reason    JSONB,                      -- top 3 mismatches from the engine
  blocked_at      TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at    TIMESTAMPTZ,
  CHECK (final_score IS NULL OR final_score BETWEEN 0 AND 100),
  CHECK (confidence  IS NULL OR confidence  BETWEEN 0 AND 100)
);
CREATE INDEX scans_app_idx    ON scans (app_id, created_at DESC);
CREATE INDEX scans_device_idx ON scans (device_id, created_at DESC);

-- Per-signal explanations (existing; keep)
CREATE TABLE scan_signals (
  id            UUID PRIMARY KEY,
  scan_id       UUID NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
  component     TEXT NOT NULL,                -- ml | heuristic | static | runtime
  signal_key    TEXT NOT NULL,
  weight        NUMERIC,
  contribution  NUMERIC,
  explanation   TEXT NOT NULL
);

-- Current block state per device + package (what GET /blocks reads)
CREATE TABLE device_app_state (
  device_id     UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  package_name  TEXT NOT NULL,
  block_state   block_state NOT NULL,
  scan_id       UUID REFERENCES scans(id),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (device_id, package_name)
);

-- Append-only audit log (feeds the Alerts feed)
CREATE TABLE block_events (
  id            UUID PRIMARY KEY,
  device_id     UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  package_name  TEXT NOT NULL,
  scan_id       UUID REFERENCES scans(id),
  action        block_action NOT NULL,
  actor_type    actor_type   NOT NULL,
  actor_id      UUID,                          -- user id; NULL for system/parent-by-token
  actor_label   TEXT,                          -- e.g. parent email for token actions
  detail        JSONB,                         -- failed-packages array, reasons
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX block_events_device_idx ON block_events (device_id, created_at DESC);

-- Parent magic-link tokens and release codes (hash only)
CREATE TABLE action_tokens (
  id               UUID PRIMARY KEY,
  kind             token_kind NOT NULL,
  token_hash       TEXT NOT NULL UNIQUE,
  parent_link_id   UUID NOT NULL REFERENCES parent_links(id) ON DELETE CASCADE,
  device_id        UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  package_name     TEXT,                       -- NULL for release_code
  expires_at       TIMESTAMPTZ NOT NULL,
  used_at          TIMESTAMPTZ,
  attempts         INTEGER NOT NULL DEFAULT 0,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Runtime summaries uploaded by device (optional; raw logs stay on device)
CREATE TABLE runtime_summaries (
  id               UUID PRIMARY KEY,
  device_id        UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  package_name     TEXT NOT NULL,
  permission       TEXT NOT NULL,
  state            TEXT NOT NULL CHECK (state IN ('foreground','background')),
  event_count      INTEGER NOT NULL,
  total_duration_ms BIGINT,
  rejected_count   INTEGER NOT NULL DEFAULT 0,
  window_start     TIMESTAMPTZ NOT NULL,
  window_end       TIMESTAMPTZ NOT NULL,
  UNIQUE (device_id, package_name, permission, state, window_start, window_end)
);

-- Alerts feed
CREATE TABLE alerts (
  id            UUID PRIMARY KEY,
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_id     UUID REFERENCES devices(id) ON DELETE CASCADE,
  type          TEXT NOT NULL,                 -- block | unblock | not_blockable | parent_alert_80 | release
  block_event_id UUID REFERENCES block_events(id),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  read_at       TIMESTAMPTZ
);
```

### Notes
- `block_events` is **append-only** (no UPDATE/DELETE grants for the app role).
- `device_app_state` is a cache of the latest decision; `block_events` is the history.
- Raw timestamps are stored `TIMESTAMPTZ` (UTC).
- Configuration values (`BLOCK_THRESHOLD`, `BLOCK_MIN_CONFIDENCE`) live in env/config, not tables, so the rule tests can pin them.

---

## 2. Room / SQLite (device)

```kotlin
@Entity(tableName = "foreground_sessions",
        indices = [Index(value = ["packageName", "startMs"], unique = true)])
data class ForegroundSession(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val packageName: String,
  val startMs: Long,
  val endMs: Long
)

@Entity(tableName = "access_events",
        indices = [Index(value = ["packageName", "op", "accessTimeMs"], unique = true)])
data class AccessEvent(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val packageName: String,
  val op: String,                 // e.g. OPSTR_CAMERA
  val accessTimeMs: Long,
  val durationMs: Long?,
  val rejectTimeMs: Long?,
  val state: String               // FOREGROUND | BACKGROUND (computed at insert)
)

@Entity(tableName = "permission_baseline",
        primaryKeys = ["packageName", "permission"])
data class PermissionBaselineRow(
  val packageName: String,
  val permission: String,
  val granted: Boolean,
  val capturedAtMs: Long
)

@Entity(tableName = "runtime_scores")
data class RuntimeScore(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val packageName: String,
  val computedAtMs: Long,
  val runtimeRisk: Int,           // clamped 0–100
  val rawPenalty: Int,            // pre-clamp, kept for honesty
  val breakdownJson: String       // permission, state, count, weight, multiplier, penalty
)

@Entity(tableName = "block_state_local")
data class LocalBlockState(
  @PrimaryKey val packageName: String,
  val state: String,              // none|warned|blocked|allowed_by_parent|allowed_by_user|not_blockable
  val scanId: String?,
  val finalScore: Double?,
  val confidence: Double?,
  val verdict: String?,
  val reasonsJson: String?,       // top 3 mismatches
  val failedSuspend: Boolean = false,
  val blockedAtMs: Long?,
  val updatedAtMs: Long
)

@Entity(tableName = "audit_cursor")
data class AuditCursor(
  @PrimaryKey val key: String,    // e.g. "appops_last_checked"
  val valueMs: Long
)
```

### Retention
Raw `access_events` / `foreground_sessions` pruned by a configurable window (set after observing real storage use); `runtime_scores` and `block_state_local` kept.

## 3. Migrations
- Postgres: Alembic (matches FastAPI stack). One migration per change; no data inserts.
- Room: versioned `Migration` objects; schema export enabled and committed.
