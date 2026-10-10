package com.privacyguard.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ForegroundSessionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSessions(sessions: List<ForegroundSession>): List<Long>

    @Query("SELECT * FROM foreground_sessions WHERE packageName = :packageName AND endMs >= :sinceMs ORDER BY startMs ASC")
    fun getSessionsForPackage(packageName: String, sinceMs: Long = 0L): List<ForegroundSession>

    @Query("SELECT * FROM foreground_sessions WHERE endMs >= :sinceMs ORDER BY startMs ASC")
    fun getAllSessionsSince(sinceMs: Long): List<ForegroundSession>

    @Query("SELECT COUNT(*) FROM foreground_sessions")
    fun getSessionCount(): Long

    @Query("DELETE FROM foreground_sessions WHERE endMs < :beforeMs")
    fun pruneBefore(beforeMs: Long): Int
}

@Dao
interface AccessEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertEvents(events: List<AccessEvent>): List<Long>

    @Query("SELECT * FROM access_events WHERE packageName = :packageName AND accessTimeMs >= :sinceMs ORDER BY accessTimeMs DESC LIMIT :limit")
    fun getEventsForPackage(packageName: String, sinceMs: Long = 0L, limit: Int = 100): List<AccessEvent>

    @Query("SELECT * FROM access_events ORDER BY accessTimeMs DESC LIMIT :limit")
    fun getRecentEvents(limit: Int = 100): List<AccessEvent>

    @Query("SELECT COUNT(*) FROM access_events")
    fun getEventCount(): Long

    @Query("SELECT COUNT(*) FROM access_events WHERE packageName = :packageName")
    fun getEventCountForPackage(packageName: String): Long

    @Query("SELECT MAX(accessTimeMs) FROM access_events WHERE packageName = :packageName")
    fun getLatestAccessTime(packageName: String): Long?

    @Query("DELETE FROM access_events WHERE accessTimeMs < :beforeMs")
    fun pruneBefore(beforeMs: Long): Int
}

@Dao
interface PermissionBaselineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBaseline(rows: List<PermissionBaselineRow>)

    @Query("SELECT * FROM permission_baseline WHERE packageName = :packageName")
    fun getBaseline(packageName: String): List<PermissionBaselineRow>

    @Query("DELETE FROM permission_baseline WHERE packageName = :packageName")
    fun deleteForPackage(packageName: String): Int
}

@Dao
interface RuntimeScoreDao {
    @Insert
    fun insertScore(score: RuntimeScore): Long

    @Query("SELECT * FROM runtime_scores WHERE packageName = :packageName ORDER BY computedAtMs DESC LIMIT 1")
    fun getLatestScore(packageName: String): RuntimeScore?

    @Query("SELECT * FROM runtime_scores WHERE packageName = :packageName ORDER BY computedAtMs DESC LIMIT :limit")
    fun getScoreHistory(packageName: String, limit: Int = 50): List<RuntimeScore>

    @Query("SELECT * FROM runtime_scores ORDER BY computedAtMs DESC LIMIT :limit")
    fun getAllRecentScores(limit: Int = 50): List<RuntimeScore>

    @Query("SELECT COUNT(*) FROM runtime_scores")
    fun getScoreCount(): Long
}

@Dao
interface LocalBlockStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBlockState(state: LocalBlockState)

    @Query("SELECT * FROM block_state_local WHERE packageName = :packageName")
    fun getBlockState(packageName: String): LocalBlockState?

    @Query("SELECT * FROM block_state_local")
    fun getAllBlockStates(): List<LocalBlockState>
}

@Dao
interface AuditCursorDao {
    @Query("SELECT valueMs FROM audit_cursor WHERE `key` = :key")
    fun getCursor(key: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun setCursor(cursor: AuditCursor)
}
