package com.privacyguard.app.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageTrackerTest {

    @Test
    fun testSimpleResumeStopPairApi29() {
        val events = listOf(
            RawUsageEvent(eventType = 1, timestamp = 1000L, packageName = "com.test.app"),
            RawUsageEvent(eventType = 23, timestamp = 5000L, packageName = "com.test.app")
        )

        val windows = UsageTracker.buildWindowsFromEvents(
            events = events,
            startMs = 0L,
            endMs = 10000L,
            apiLevel = 34
        )

        assertEquals(1, windows.size)
        assertEquals(1000L, windows[0].startMs)
        assertEquals(5000L, windows[0].endMs)
    }

    @Test
    fun testLegacyApi26To28Fallback() {
        // Legacy: 1 = MOVE_TO_FOREGROUND, 2 = MOVE_TO_BACKGROUND
        val events = listOf(
            RawUsageEvent(eventType = 1, timestamp = 2000L, packageName = "com.test.app"),
            RawUsageEvent(eventType = 2, timestamp = 6000L, packageName = "com.test.app")
        )

        val windows = UsageTracker.buildWindowsFromEvents(
            events = events,
            startMs = 0L,
            endMs = 10000L,
            apiLevel = 28
        )

        assertEquals(1, windows.size)
        assertEquals(2000L, windows[0].startMs)
        assertEquals(6000L, windows[0].endMs)
    }

    @Test
    fun testOpenSessionClosesAtEndMs() {
        val events = listOf(
            RawUsageEvent(eventType = 1, timestamp = 4000L, packageName = "com.test.app")
        )

        val windows = UsageTracker.buildWindowsFromEvents(
            events = events,
            startMs = 0L,
            endMs = 10000L,
            apiLevel = 34
        )

        assertEquals(1, windows.size)
        assertEquals(4000L, windows[0].startMs)
        assertEquals(10000L, windows[0].endMs)
    }

    @Test
    fun testStopWithoutPriorResumeAssumesWindowStart() {
        // App was opened before startMs, and closed at 3000ms
        val events = listOf(
            RawUsageEvent(eventType = 23, timestamp = 3000L, packageName = "com.test.app")
        )

        val windows = UsageTracker.buildWindowsFromEvents(
            events = events,
            startMs = 1000L,
            endMs = 10000L,
            apiLevel = 34
        )

        assertEquals(1, windows.size)
        assertEquals(1000L, windows[0].startMs)
        assertEquals(3000L, windows[0].endMs)
    }

    @Test
    fun testActivitySwitchingMergesIntoSingleWindow() {
        // Two consecutive resumes within same app before stop
        val events = listOf(
            RawUsageEvent(eventType = 1, timestamp = 1000L, packageName = "com.test.app", className = "MainActivity"),
            RawUsageEvent(eventType = 1, timestamp = 2000L, packageName = "com.test.app", className = "DetailActivity"),
            RawUsageEvent(eventType = 23, timestamp = 7000L, packageName = "com.test.app", className = "DetailActivity")
        )

        val windows = UsageTracker.buildWindowsFromEvents(
            events = events,
            startMs = 0L,
            endMs = 10000L,
            apiLevel = 34
        )

        assertEquals(1, windows.size)
        assertEquals(1000L, windows[0].startMs)
        assertEquals(7000L, windows[0].endMs)
    }

    @Test
    fun testOverlappingOrAdjacentWindowsMergeWithinTolerance() {
        val events = listOf(
            RawUsageEvent(eventType = 1, timestamp = 1000L, packageName = "com.test.app"),
            RawUsageEvent(eventType = 23, timestamp = 3000L, packageName = "com.test.app"),
            // Second session starts 500ms after first stops (within 1000ms tolerance)
            RawUsageEvent(eventType = 1, timestamp = 3500L, packageName = "com.test.app"),
            RawUsageEvent(eventType = 23, timestamp = 6000L, packageName = "com.test.app")
        )

        val windows = UsageTracker.buildWindowsFromEvents(
            events = events,
            startMs = 0L,
            endMs = 10000L,
            apiLevel = 34,
            toleranceMs = 1000L
        )

        assertEquals(1, windows.size)
        assertEquals(1000L, windows[0].startMs)
        assertEquals(6000L, windows[0].endMs)
    }

    @Test
    fun testDistinctSessionsDoNotMergeBeyondTolerance() {
        val events = listOf(
            RawUsageEvent(eventType = 1, timestamp = 1000L, packageName = "com.test.app"),
            RawUsageEvent(eventType = 23, timestamp = 3000L, packageName = "com.test.app"),
            // Second session starts 5000ms later (well beyond 1000ms tolerance)
            RawUsageEvent(eventType = 1, timestamp = 8000L, packageName = "com.test.app"),
            RawUsageEvent(eventType = 23, timestamp = 9000L, packageName = "com.test.app")
        )

        val windows = UsageTracker.buildWindowsFromEvents(
            events = events,
            startMs = 0L,
            endMs = 10000L,
            apiLevel = 34,
            toleranceMs = 1000L
        )

        assertEquals(2, windows.size)
        assertEquals(1000L, windows[0].startMs)
        assertEquals(3000L, windows[0].endMs)
        assertEquals(8000L, windows[1].startMs)
        assertEquals(9000L, windows[1].endMs)
    }

    @Test
    fun testInvalidTimeWindowReturnsEmpty() {
        val events = listOf(
            RawUsageEvent(eventType = 1, timestamp = 5000L, packageName = "com.test.app")
        )

        val windows = UsageTracker.buildWindowsFromEvents(
            events = events,
            startMs = 10000L,
            endMs = 5000L,
            apiLevel = 34
        )

        assertTrue(windows.isEmpty())
    }
}
