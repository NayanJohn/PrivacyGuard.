package com.privacyguard.app.appops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpsParserTest {

    @Test
    fun testParseRelativeDuration() {
        assertEquals(500L, AppOpsParser.parseRelativeDuration("+500ms"))
        assertEquals(2000L, AppOpsParser.parseRelativeDuration("+2s"))
        assertEquals(2500L, AppOpsParser.parseRelativeDuration("+2s500ms"))
        assertEquals(60000L, AppOpsParser.parseRelativeDuration("+1m"))
        assertEquals(3600000L, AppOpsParser.parseRelativeDuration("+1h"))
        assertEquals(86400000L, AppOpsParser.parseRelativeDuration("+1d"))
        assertEquals(3527888L, AppOpsParser.parseRelativeDuration("+58m47s888ms ago"))
        assertEquals(0L, AppOpsParser.parseRelativeDuration(""))
        assertEquals(0L, AppOpsParser.parseRelativeDuration("invalid"))
    }

    @Test
    fun testNormalizeOpName() {
        assertEquals("OP_CAMERA", AppOpsParser.normalizeOpName("CAMERA"))
        assertEquals("OP_CAMERA", AppOpsParser.normalizeOpName("OP_CAMERA"))
        assertEquals("OP_RECORD_AUDIO", AppOpsParser.normalizeOpName("RECORD_AUDIO"))
        assertEquals("OP_FINE_LOCATION", AppOpsParser.normalizeOpName("FINE_LOCATION"))
    }

    @Test
    fun testParseHistoryTable() {
        val sampleOutput = """
UID|PACKAGE_NAME|DEVICE_ID|OP_NAME|ATTRIBUTION_TAG|ACCESS_TIME|ACCESS_COUNTS|REJECT_COUNTS|DURATION|UID_STATE|OP_FLAGS|ATTRIBUTION_CHAIN_ID|ATTRIBUTION_FLAGS
10226|com.google.android.gms|default:0|FINE_LOCATION_SOURCE|network_location_provider|2026-10-10 05:45:11.564|32|0|0|fg|s|-1|0
10152|com.google.android.apps.wellbeing|default:0|START_FOREGROUND|null|2026-10-10 05:52:03.616|0|0|232|fgsvc|s|-1|0
10230|com.privacyguard.app|default:0|GET_USAGE_STATS|null|2026-10-10 05:08:25.757|3|0|1500|top|s|-1|0
        """.trimIndent()

        // Filter for specific package
        val pgEvents = AppOpsParser.parseHistoryTable(sampleOutput, "com.privacyguard.app")
        assertEquals(1, pgEvents.size)
        val event = pgEvents[0]
        assertEquals("OP_GET_USAGE_STATS", event.op)
        assertEquals("com.privacyguard.app", event.packageName)
        assertTrue(event.accessTimeMs > 0L)
        assertEquals(1500L, event.durationMs)
        assertEquals(3, event.accessCount)
        assertEquals(0, event.rejectCount)
        assertEquals("top", event.uidState)
        assertEquals("history", event.source)

        // Parse all packages
        val allEvents = AppOpsParser.parseHistoryTable(sampleOutput, null)
        assertEquals(3, allEvents.size)
    }

    @Test
    fun testParsePackageSection() {
        val sampleDumpsys = """
AppOps Service state:
  Settings:
    top_state_settle_time=+5s0ms

AppOps Uid Op State
  Uid u0a230:
    Package com.privacyguard.app:
      GET_USAGE_STATS (allow): 
        null=[
          Access: [top-s] 2026-10-10 05:13:20.045 (-59m9s648ms) duration=+1s200ms
        ]
      CAMERA (allow):
        null=[
          Access: [fg-s] 2026-10-10 05:14:00.000 (-58m29s)
          Reject: [bg-s] 2026-10-10 05:14:05.000 (-58m24s)
        ]
    Package com.other.app:
      RECORD_AUDIO (allow):
        null=[
          Access: [fg-s] 2026-10-10 05:20:00.000 (-52m29s)
        ]
History:  mode=HISTORICAL_MODE_ENABLED_ACTIVE
        """.trimIndent()

        val events = AppOpsParser.parsePackageSection(sampleDumpsys, "com.privacyguard.app")
        assertEquals(3, events.size)

        val usageEvent = events.find { it.op == "OP_GET_USAGE_STATS" }
        assertNotNull(usageEvent)
        assertEquals(1200L, usageEvent!!.durationMs)
        assertEquals("top", usageEvent.uidState)
        assertTrue(usageEvent.accessTimeMs > 0L)

        val cameraAccess = events.find { it.op == "OP_CAMERA" && it.accessTimeMs > 0L }
        assertNotNull(cameraAccess)
        assertEquals("fg", cameraAccess!!.uidState)

        val cameraReject = events.find { it.op == "OP_CAMERA" && it.rejectTimeMs > 0L }
        assertNotNull(cameraReject)
        assertEquals("bg", cameraReject!!.uidState)
    }

    @Test
    fun testParseCmdAppopsGet() {
        val sampleCmd = """
Uid mode: COARSE_LOCATION: ignore
FINE_LOCATION: allow; time=+10m ago; duration=+5s; rejectTime=+1m ago
CAMERA: allow; time=+2m30s ago
RECORD_AUDIO: ignore
        """.trimIndent()

        val nowMs = 1700000000000L
        val events = AppOpsParser.parseCmdAppopsGet(sampleCmd, "com.test.app", nowMs)
        assertEquals(2, events.size)

        val locationEvent = events.find { it.op == "OP_FINE_LOCATION" }
        assertNotNull(locationEvent)
        assertEquals(nowMs - (10 * 60 * 1000L), locationEvent!!.accessTimeMs)
        assertEquals(5000L, locationEvent.durationMs)
        assertEquals(nowMs - (60 * 1000L), locationEvent.rejectTimeMs)
    }

    @Test
    fun testParseAllWithDeduplication() {
        val sampleCombined = """
UID|PACKAGE_NAME|DEVICE_ID|OP_NAME|ATTRIBUTION_TAG|ACCESS_TIME|ACCESS_COUNTS|REJECT_COUNTS|DURATION|UID_STATE|OP_FLAGS|ATTRIBUTION_CHAIN_ID|ATTRIBUTION_FLAGS
10230|com.privacyguard.app|default:0|CAMERA|null|2026-10-10 05:13:20.045|1|0|2000|fg|s|-1|0

    Package com.privacyguard.app:
      CAMERA (allow):
        null=[
          Access: [fg-s] 2026-10-10 05:13:20.045 (-59m9s) duration=+2s0ms
        ]
        """.trimIndent()

        val events = AppOpsParser.parseAll(sampleCombined, "com.privacyguard.app")
        // Both refer to same op CAMERA at 2026-10-10 05:13:20.045; must deduplicate to 1
        assertEquals(1, events.size)
        assertEquals("OP_CAMERA", events[0].op)
        assertEquals(2000L, events[0].durationMs)
    }

    @Test
    fun testEmptyOrInvalidOutputProducesHonestEmptyList() {
        val events = AppOpsParser.parseAll("", "com.privacyguard.app")
        assertTrue(events.isEmpty())

        val invalidEvents = AppOpsParser.parseAll("Some random error text", "com.privacyguard.app")
        assertTrue(invalidEvents.isEmpty())
    }
}
