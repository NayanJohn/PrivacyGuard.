package com.privacyguard.app.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Foreground session window extracted from UsageStatsManager.
 * Matches DATABASE_SCHEMA.md §2.
 */
@Entity(
    tableName = "foreground_sessions",
    indices = [Index(value = ["packageName", "startMs"], unique = true)]
)
data class ForegroundSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startMs: Long,
    val endMs: Long
)

/**
 * Historical AppOps hardware/privacy access event.
 * Unique index on [packageName, op, accessTimeMs] deduplicates repeated poll cycles (FR-H2).
 * Matches DATABASE_SCHEMA.md §2.
 */
@Entity(
    tableName = "access_events",
    indices = [Index(value = ["packageName", "op", "accessTimeMs"], unique = true)]
)
data class AccessEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val op: String,                 // e.g. OP_CAMERA, OP_RECORD_AUDIO
    val accessTimeMs: Long,
    val durationMs: Long?,
    val rejectTimeMs: Long?,
    val state: String               // FOREGROUND | BACKGROUND (computed at insert)
)

/**
 * Snapshot of declared and granted manifest permissions.
 * Matches DATABASE_SCHEMA.md §2.
 */
@Entity(
    tableName = "permission_baseline",
    primaryKeys = ["packageName", "permission"]
)
data class PermissionBaselineRow(
    val packageName: String,
    val permission: String,
    val granted: Boolean,
    val capturedAtMs: Long
)

/**
 * Historical computed risk score and breakdown.
 * Matches DATABASE_SCHEMA.md §2.
 */
@Entity(tableName = "runtime_scores")
data class RuntimeScore(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val computedAtMs: Long,
    val runtimeRisk: Int,           // clamped 0–100
    val rawPenalty: Int,            // pre-clamp, kept for honesty
    val breakdownJson: String       // permission, state, count, weight, multiplier, penalty
)

/**
 * Local device-side block state cache.
 * Matches DATABASE_SCHEMA.md §2.
 */
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

/**
 * Cursor tracking incremental audit watermarks.
 * Matches DATABASE_SCHEMA.md §2.
 */
@Entity(tableName = "audit_cursor")
data class AuditCursor(
    @PrimaryKey val key: String,    // e.g. "appops_last_checked", "usage_last_checked"
    val valueMs: Long
)
