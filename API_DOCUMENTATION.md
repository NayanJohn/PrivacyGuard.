# PrivacyGuard — API Documentation

Base URL: `{BASE_URL}` (from environment; never hard-coded).
Format: JSON, UTF-8, ISO-8601 UTC timestamps.
Payload examples show **types**, not invented values.

> **Status legend:** **[spec]** = named in the blocking spec, required. **[proposed]** = needed by the design, align with your existing backend. **[existing?]** = probably already in your engine — confirm.

---

## 1. Authentication

| Caller | Mechanism |
|--------|-----------|
| Device | `Authorization: Bearer <device_token>` (issued at registration, stored hashed) |
| Adult / minor user | `Authorization: Bearer <user_session_token>` |
| Parent (email link) | Single-use token in the URL → exchanged at the portal; never an account password |

## 2. Conventions

- **Errors:** `{ "error": { "code": "<string>", "message": "<string>", "details": <object|null> } }`
- **Status codes:** 200 OK · 201 Created · 400 invalid input · 401 unauthenticated · 403 not allowed (age/parent rules) · 404 not found · 409 conflict · 410 token expired/used · 422 validation · 429 rate limited
- **Idempotency:** `POST` endpoints that change block state accept `Idempotency-Key` header.
- **Rate limits:** token and code endpoints are limited and counted (`attempts`).

---

## 3. Endpoints

### 3.1 `GET /health` — **[spec]**
Unchanged. (The spec line about adding a field "for nothing new" is read as: no change. See `required.md` OD-4.)

---

### 3.2 `GET /blocks` — **[spec]**
Returns the block list for the calling device so the device can apply suspend/unsuspend.

Query: `since` (optional ISO timestamp) — only changes after it.

Response `200`:
```json
{
  "device_id": "<uuid>",
  "generated_at": "<timestamp>",
  "blocks": [
    {
      "package_name": "<string>",
      "block_state": "blocked | allowed_by_parent | allowed_by_user | not_blockable | warned | none",
      "should_be_suspended": "<boolean>",
      "scan_id": "<uuid>",
      "final_score": "<number 0-100>",
      "confidence": "<number 0-100>",
      "verdict": "<string>",
      "block_reason": [ { "mismatch": "<string>", "detail": "<string>" } ],
      "blocked_at": "<timestamp|null>"
    }
  ]
}
```
Rules: `should_be_suspended` is `true` only for `blocked`. `block_reason` has at most 3 entries. Empty `blocks` array is a valid response.

---

### 3.3 `POST /apps/{package}/allow` — **[spec]**
Allows a blocked app.

Auth: **adult user** (session token) **or** **parent** (magic-link token).

Body:
```json
{
  "device_id": "<uuid>",
  "token": "<string|omitted for adult session>"
}
```
Behavior:
- Adult → `block_state = allowed_by_user`, event actor `adult_user`.
- Parent token (valid, unexpired, unused, matching device + package) → `allowed_by_parent`, token marked used, event actor `parent`.
- Under-18 user session without parent token → `403`.

Response `200`: `{ "package_name": "<string>", "block_state": "<state>", "updated_at": "<timestamp>" }`
Errors: `403` role not allowed · `404` no such device/app · `410` token expired or already used.
Effect: device unsuspends on the next sync.

---

### 3.4 `POST /apps/{package}/block` — **[spec]**
Manual re-block (e.g., after an allow).

Auth: adult user, or parent session.

Body: `{ "device_id": "<uuid>", "reason": "<string|null>" }`

Behavior: sets `block_state = blocked` **only if** the app is blockable (never-block list respected; device reports Device Owner active). Otherwise `409` with `code = "not_blockable"` or `"hard_blocking_unavailable"`.

Response `200`: `{ "package_name": "<string>", "block_state": "blocked", "blocked_at": "<timestamp>" }`

---

### 3.5 `POST /devices/register` — **[proposed]**
Body: `{ "label": "<string|null>", "sdk_int": <integer> }`
Response `201`: `{ "device_id": "<uuid>", "device_token": "<string, shown once>" }`

### 3.6 `POST /devices/{device_id}/status` — **[proposed]**
Device reports capability and enforcement truth.
```json
{
  "device_owner_active": "<boolean>",
  "shizuku_connected": "<boolean>",
  "usage_access_granted": "<boolean>",
  "applied": [
    { "package_name": "<string>", "suspended": "<boolean>", "failed_by_os": "<boolean>" }
  ]
}
```
Backend uses `applied[].failed_by_os` to set `not_blockable` and write a `block_events` row with the failed-packages array in `detail`.

### 3.7 `POST /scans` — **[existing?]**
Starts a scan for a package (APK upload or package identity). Returns `scan_id` with `status = preliminary` until the engine completes.

### 3.8 `GET /scans/{scan_id}` — **[existing?]**
```json
{
  "id": "<uuid>",
  "package_name": "<string>",
  "status": "preliminary | complete | failed",
  "ml_risk": "<number|null>",
  "heuristic_risk": "<number|null>",
  "static_risk": "<number|null>",
  "runtime_risk": "<number|null>",
  "final_score": "<number|null>",
  "confidence": "<number|null>",
  "verdict": "<string|null>",
  "signals": [ { "component": "<string>", "signal_key": "<string>", "contribution": "<number>", "explanation": "<string>" } ],
  "block_state": "<state>",
  "block_reason": [ ... ],
  "blocked_at": "<timestamp|null>"
}
```
The block rule runs when `status` becomes `complete`. A `preliminary` scan never sets `blocked`.

### 3.9 `POST /devices/{device_id}/runtime-summaries` — **[proposed]**
Uploads aggregated runtime data (not raw logs).
```json
{
  "summaries": [
    { "package_name": "<string>", "permission": "<string>", "state": "foreground | background",
      "event_count": <integer>, "total_duration_ms": <integer|null>, "rejected_count": <integer>,
      "window_start": "<timestamp>", "window_end": "<timestamp>" }
  ]
}
```
Idempotent via the unique key in `DATABASE_SCHEMA.md`.

### 3.10 Release control — **[proposed]**
`POST /devices/{device_id}/release/request-code`
Under-18: generates a code, stores its hash, emails the parent. Response `202`.

`POST /devices/{device_id}/release/confirm`
Body: `{ "code": "<string>" }` → `200 { "authorized": true, "expires_at": "<timestamp>" }` or `403` / `429`.
Adults skip both; the device logs `release_control` via `POST /devices/{id}/status` or a dedicated event call.

### 3.11 Parent portal — **[proposed]**
`GET  /parent/allow?token=<token>` → page data: app, score, reasons, device label, expiry (or `410`).
`POST /parent/allow` → body `{ "token": "<string>" }` → performs the allow as in §3.3.

### 3.12 `GET /alerts` — **[proposed]**
Query: `device_id`, `limit`, `cursor`.
```json
{ "items": [
    { "id": "<uuid>", "type": "block | unblock | not_blockable | parent_alert_80 | release",
      "package_name": "<string>", "actor_type": "<string>", "actor_label": "<string|null>",
      "created_at": "<timestamp>", "read_at": "<timestamp|null>" } ],
  "next_cursor": "<string|null>" }
```

---

## 4. Block decision (server logic)

```
eligible = final_score > BLOCK_THRESHOLD            # strictly greater (90 is NOT blocked)
        AND scan.status == 'complete'
        AND confidence >= BLOCK_MIN_CONFIDENCE      # 70 inclusive
        AND package not in never_block (server allowlist)
        AND device.device_owner_active
        AND (user.age_category == 'adult' OR parent_link.verified_at IS NOT NULL)

block_state = 'blocked'  if eligible
              'warned'   if final_score > BLOCK_THRESHOLD but not eligible
              'none'     otherwise
```
Under-18 + `blocked` → send the parent email (§3.11 link). Existing `final_score >= 80` parent alert remains separate.

## 5. Configuration (backend env)

| Variable | Meaning | Value |
|----------|---------|-------|
| `BLOCK_THRESHOLD` | strict lower bound for block | `90` |
| `BLOCK_MIN_CONFIDENCE` | inclusive minimum confidence | `70` |
| `PARENT_TOKEN_TTL_MINUTES` | magic-link lifetime | set by owner |
| `RELEASE_CODE_TTL_MINUTES` | release code lifetime | set by owner |
| `NEVER_BLOCK_PACKAGES` | extra allowlist (comma-separated) | set by owner |
| `SMTP_*` / Gmail sender | parent email delivery | set by owner |

## 6. Audit logging
Every state change writes one `block_events` row (`action`, `actor_type`, `actor_id/label`, `detail`, `created_at`) and one `alerts` row. Rows are never updated.
