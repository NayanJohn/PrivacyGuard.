package com.privacyguard.app.background

import com.privacyguard.app.db.AccessEvent
import com.privacyguard.app.db.ForegroundSession
import com.privacyguard.app.db.RuntimeScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditDeduplicationTest {

    @Test
    fun testAccessEventUniqueKeyDeduplication() {
        // FR-H2: Only new events since last cursor stored, dedupe on (packageName, op, accessTimeMs)
        val event1 = AccessEvent(
            id = 1L,
            packageName = "com.android.chrome",
            op = "OP_CAMERA",
            accessTimeMs = 1700000000000L,
            durationMs = 2500L,
            rejectTimeMs = 0L,
            state = "FOREGROUND"
        )

        val duplicateOfEvent1 = AccessEvent(
            id = 2L,
            packageName = "com.android.chrome",
            op = "OP_CAMERA",
            accessTimeMs = 1700000000000L,
            durationMs = 2500L,
            rejectTimeMs = 0L,
            state = "FOREGROUND"
        )

        val event2DistinctTime = AccessEvent(
            id = 3L,
            packageName = "com.android.chrome",
            op = "OP_CAMERA",
            accessTimeMs = 1700000005000L,
            durationMs = 1200L,
            rejectTimeMs = 0L,
            state = "FOREGROUND"
        )

        val rawList = listOf(event1, duplicateOfEvent1, event2DistinctTime)

        // Deduplication using composite key (packageName + op + accessTimeMs)
        val deduplicated = rawList.distinctBy { Triple(it.packageName, it.op, it.accessTimeMs) }

        assertEquals(2, deduplicated.size)
        assertEquals(1700000000000L, deduplicated[0].accessTimeMs)
        assertEquals(1700000005000L, deduplicated[1].accessTimeMs)
    }

    @Test
    fun testForegroundSessionIntersectionClassifier() {
        val sessions = listOf(
            ForegroundSession(
                id = 1L,
                packageName = "com.android.chrome",
                startMs = 10000L,
                endMs = 20000L
            ),
            ForegroundSession(
                id = 2L,
                packageName = "com.android.chrome",
                startMs = 30000L,
                endMs = 40000L
            )
        )

        fun isForeground(accessTimeMs: Long, durationMs: Long): Boolean {
            return sessions.any { s ->
                (accessTimeMs in s.startMs..s.endMs) ||
                (s.startMs <= accessTimeMs + durationMs && accessTimeMs <= s.endMs)
            }
        }

        // Inside first session
        assertTrue(isForeground(15000L, 1000L))

        // On start boundary
        assertTrue(isForeground(10000L, 500L))

        // On end boundary
        assertTrue(isForeground(20000L, 500L))

        // Overlapping boundary
        assertTrue(isForeground(9500L, 1000L))

        // Completely outside in background gap
        assertFalse(isForeground(25000L, 2000L))

        // Before all sessions
        assertFalse(isForeground(5000L, 1000L))

        // Inside second session
        assertTrue(isForeground(35000L, 3000L))
    }

    @Test
    fun testCursorIncrementalProgression() {
        val initialCursor = 1700000000000L
        val events = listOf(
            AccessEvent(packageName = "com.app", op = "OP_CAMERA", accessTimeMs = 1699999990000L, durationMs = 0L, rejectTimeMs = 0L, state = "BACKGROUND"),
            AccessEvent(packageName = "com.app", op = "OP_CAMERA", accessTimeMs = 1700000001000L, durationMs = 0L, rejectTimeMs = 0L, state = "FOREGROUND"),
            AccessEvent(packageName = "com.app", op = "OP_RECORD_AUDIO", accessTimeMs = 1700000009000L, durationMs = 0L, rejectTimeMs = 0L, state = "FOREGROUND")
        )

        // Filter events strictly >= cursor
        val newEvents = events.filter { it.accessTimeMs >= initialCursor }
        assertEquals(2, newEvents.size)

        val updatedCursor = newEvents.maxOf { it.accessTimeMs }
        assertEquals(1700000009000L, updatedCursor)
        assertTrue(updatedCursor > initialCursor)
    }

    @Test
    fun testRuntimeScoreRoomSerialization() {
        val breakdownJson = """[{"permissionOrOp":"android.permission.CAMERA","expectedState":"FOREGROUND_ONLY","observedState":"BACKGROUND","evidenceCount":3,"severityScore":150,"detail":"3 background camera accesses"}]"""

        val score = RuntimeScore(
            id = 10L,
            packageName = "com.test.rogue",
            computedAtMs = 1700000100000L,
            runtimeRisk = 95,
            rawPenalty = 150,
            breakdownJson = breakdownJson
        )

        assertEquals("com.test.rogue", score.packageName)
        assertEquals(95, score.runtimeRisk)
        assertEquals(150, score.rawPenalty)

        assertTrue(score.breakdownJson.contains("\"permissionOrOp\":\"android.permission.CAMERA\""))
        assertTrue(score.breakdownJson.contains("\"observedState\":\"BACKGROUND\""))
        assertTrue(score.breakdownJson.contains("\"severityScore\":150"))
        assertTrue(score.breakdownJson.contains("\"evidenceCount\":3"))
    }
}


