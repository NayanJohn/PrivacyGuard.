package com.privacyguard.app.appops

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

data class AccessEvent(
    val op: String,
    val packageName: String,
    val accessTimeMs: Long,
    val durationMs: Long = 0L,
    val rejectTimeMs: Long = 0L,
    val accessCount: Int = 1,
    val rejectCount: Int = 0,
    val uidState: String = "unknown",
    val attributionTag: String? = null,
    val source: String = "history"
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "op" to op,
        "packageName" to packageName,
        "accessTimeMs" to accessTimeMs,
        "durationMs" to durationMs,
        "rejectTimeMs" to rejectTimeMs,
        "accessCount" to accessCount,
        "rejectCount" to rejectCount,
        "uidState" to uidState,
        "attributionTag" to attributionTag,
        "source" to source
    )
}

object AppOpsParser {

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    private val RELATIVE_TIME_PATTERN = Pattern.compile(
        """(?:(\d+)d)?(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?(?:(\d+)ms)?"""
    )

    fun normalizeOpName(rawOp: String): String {
        val trimmed = rawOp.trim()
        return if (trimmed.startsWith("OP_")) trimmed else "OP_$trimmed"
    }

    fun parseRelativeDuration(raw: String): Long {
        val cleaned = raw.trim().removePrefix("+").removeSuffix("ago").trim()
        if (cleaned.isEmpty()) return 0L

        val matcher = RELATIVE_TIME_PATTERN.matcher(cleaned)
        if (matcher.matches()) {
            val days = matcher.group(1)?.toLongOrNull() ?: 0L
            val hours = matcher.group(2)?.toLongOrNull() ?: 0L
            val minutes = matcher.group(3)?.toLongOrNull() ?: 0L
            val seconds = matcher.group(4)?.toLongOrNull() ?: 0L
            val millis = matcher.group(5)?.toLongOrNull() ?: 0L
            return (days * 86400000L) + (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L) + millis
        }
        return 0L
    }

    fun parseHistoryTable(output: String, targetPackage: String? = null): List<AccessEvent> {
        val events = mutableListOf<AccessEvent>()
        val lines = output.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("UID|") || trimmed.startsWith("----")) continue

            val parts = trimmed.split("|")
            if (parts.size >= 10) {
                val pkg = parts[1].trim()
                if (targetPackage != null && pkg != targetPackage) continue

                val rawOp = parts[3].trim()
                val tag = parts[4].trim().takeIf { it != "null" && it.isNotBlank() }
                val timeStr = parts[5].trim()
                val accessCount = parts[6].trim().toIntOrNull() ?: 0
                val rejectCount = parts[7].trim().toIntOrNull() ?: 0
                val durationMs = parts[8].trim().toLongOrNull() ?: 0L
                val uidState = parts[9].trim()

                val timeMs = try {
                    synchronized(DATE_FORMAT) {
                        DATE_FORMAT.parse(timeStr)?.time ?: 0L
                    }
                } catch (e: Exception) {
                    0L
                }

                if (timeMs > 0L || accessCount > 0 || rejectCount > 0) {
                    val accessTimeMs = if (accessCount > 0) timeMs else 0L
                    val rejectTimeMs = if (rejectCount > 0) timeMs else 0L

                    events.add(
                        AccessEvent(
                            op = normalizeOpName(rawOp),
                            packageName = pkg,
                            accessTimeMs = accessTimeMs,
                            durationMs = durationMs,
                            rejectTimeMs = rejectTimeMs,
                            accessCount = accessCount,
                            rejectCount = rejectCount,
                            uidState = uidState,
                            attributionTag = tag,
                            source = "history"
                        )
                    )
                }
            }
        }
        return events
    }

    fun parsePackageSection(
        output: String,
        targetPackage: String,
        nowMs: Long = System.currentTimeMillis()
    ): List<AccessEvent> {
        val events = mutableListOf<AccessEvent>()
        val lines = output.lines()

        var inPackageBlock = false
        var currentOp: String? = null

        val accessRegex = Pattern.compile("""Access:\s*\[([^\]]+)\]\s*([0-9\-]+\s+[0-9:.]+)(?:\s*\(([^)]+)\))?(?:\s*duration=([^\s]+))?""")
        val rejectRegex = Pattern.compile("""Reject:\s*\[([^\]]+)\]\s*([0-9\-]+\s+[0-9:.]+)(?:\s*\(([^)]+)\))?""")
        val opHeaderRegex = Pattern.compile("""^\s{4,8}([A-Z0-9_]+)(?:\s*\(([^)]+)\))?:\s*$""")

        for (line in lines) {
            val trimmed = line.trim()
            if (line.startsWith("    Package ") || line.startsWith("  Package ")) {
                val pkgName = line.substringAfter("Package ").substringBefore(":").trim()
                inPackageBlock = (pkgName == targetPackage)
                currentOp = null
                continue
            }

            if (!inPackageBlock) continue

            // Stop package block if next top-level section starts
            if (line.startsWith("AppOps ") || line.startsWith("History:") || line.startsWith("----")) {
                inPackageBlock = false
                break
            }

            val opMatcher = opHeaderRegex.matcher(line)
            if (opMatcher.find()) {
                currentOp = opMatcher.group(1)
                continue
            }

            if (currentOp != null) {
                val accMatcher = accessRegex.matcher(trimmed)
                if (accMatcher.find()) {
                    val uidState = accMatcher.group(1)?.split("-")?.firstOrNull() ?: "unknown"
                    val absTimeStr = accMatcher.group(2)
                    val relTimeStr = accMatcher.group(3)
                    val durationStr = accMatcher.group(4)

                    val timeMs = parseDateTimeOrRelative(absTimeStr, relTimeStr, nowMs)
                    val durationMs = durationStr?.let { parseRelativeDuration(it) } ?: 0L

                    if (timeMs > 0L) {
                        events.add(
                            AccessEvent(
                                op = normalizeOpName(currentOp),
                                packageName = targetPackage,
                                accessTimeMs = timeMs,
                                durationMs = durationMs,
                                rejectTimeMs = 0L,
                                accessCount = 1,
                                rejectCount = 0,
                                uidState = uidState,
                                source = "dumpsys"
                            )
                        )
                    }
                }

                val rejMatcher = rejectRegex.matcher(trimmed)
                if (rejMatcher.find()) {
                    val uidState = rejMatcher.group(1)?.split("-")?.firstOrNull() ?: "unknown"
                    val absTimeStr = rejMatcher.group(2)
                    val relTimeStr = rejMatcher.group(3)

                    val timeMs = parseDateTimeOrRelative(absTimeStr, relTimeStr, nowMs)
                    if (timeMs > 0L) {
                        events.add(
                            AccessEvent(
                                op = normalizeOpName(currentOp),
                                packageName = targetPackage,
                                accessTimeMs = 0L,
                                durationMs = 0L,
                                rejectTimeMs = timeMs,
                                accessCount = 0,
                                rejectCount = 1,
                                uidState = uidState,
                                source = "dumpsys"
                            )
                        )
                    }
                }
            }
        }

        return events
    }

    fun parseCmdAppopsGet(
        output: String,
        targetPackage: String,
        nowMs: Long = System.currentTimeMillis()
    ): List<AccessEvent> {
        val events = mutableListOf<AccessEvent>()
        val lines = output.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || !trimmed.contains(":")) continue
            val colonIdx = trimmed.indexOf(":")
            val rawOp = trimmed.substring(0, colonIdx).trim()
            if (rawOp.contains(" ")) continue // Skip headers like "Uid mode"

            val rest = trimmed.substring(colonIdx + 1)
            val parts = rest.split(";").map { it.trim() }

            var rawTime: String? = null
            var rawDuration: String? = null
            var rawReject: String? = null

            for (part in parts) {
                when {
                    part.startsWith("time=") -> rawTime = part.removePrefix("time=").trim()
                    part.startsWith("duration=") -> rawDuration = part.removePrefix("duration=").trim()
                    part.startsWith("rejectTime=") -> rawReject = part.removePrefix("rejectTime=").trim()
                }
            }

            val accessTimeMs = if (!rawTime.isNullOrBlank()) {
                val offset = parseRelativeDuration(rawTime)
                if (offset > 0L) nowMs - offset else 0L
            } else 0L

            val durationMs = if (!rawDuration.isNullOrBlank()) {
                parseRelativeDuration(rawDuration)
            } else 0L

            val rejectTimeMs = if (!rawReject.isNullOrBlank()) {
                val offset = parseRelativeDuration(rawReject)
                if (offset > 0L) nowMs - offset else 0L
            } else 0L

            if (accessTimeMs > 0L || rejectTimeMs > 0L) {
                events.add(
                    AccessEvent(
                        op = normalizeOpName(rawOp),
                        packageName = targetPackage,
                        accessTimeMs = accessTimeMs,
                        durationMs = durationMs,
                        rejectTimeMs = rejectTimeMs,
                        accessCount = if (accessTimeMs > 0L) 1 else 0,
                        rejectCount = if (rejectTimeMs > 0L) 1 else 0,
                        uidState = "unknown",
                        source = "cmd"
                    )
                )
            }
        }

        return events
    }

    fun parseAll(
        output: String,
        targetPackage: String,
        nowMs: Long = System.currentTimeMillis()
    ): List<AccessEvent> {
        val historyEvents = parseHistoryTable(output, targetPackage)
        val packageEvents = parsePackageSection(output, targetPackage, nowMs)
        val cmdEvents = if (historyEvents.isEmpty() && packageEvents.isEmpty()) {
            parseCmdAppopsGet(output, targetPackage, nowMs)
        } else emptyList()

        val combined = (historyEvents + packageEvents + cmdEvents)
        // Deduplicate on (op, accessTimeMs, rejectTimeMs)
        val uniqueMap = mutableMapOf<String, AccessEvent>()
        for (event in combined) {
            val key = "${event.op}|${event.accessTimeMs}|${event.rejectTimeMs}"
            val existing = uniqueMap[key]
            if (existing == null) {
                uniqueMap[key] = event
            } else {
                // Prefer history over dumpsys over cmd, or take higher duration
                if (event.durationMs > existing.durationMs || existing.source == "cmd") {
                    uniqueMap[key] = event
                }
            }
        }

        return uniqueMap.values.sortedByDescending { maxOf(it.accessTimeMs, it.rejectTimeMs) }
    }

    private fun parseDateTimeOrRelative(absStr: String?, relStr: String?, nowMs: Long): Long {
        if (!absStr.isNullOrBlank()) {
            try {
                synchronized(DATE_FORMAT) {
                    val parsed = DATE_FORMAT.parse(absStr.trim())
                    if (parsed != null) return parsed.time
                }
            } catch (_: Exception) {}
        }

        if (!relStr.isNullOrBlank()) {
            val offset = parseRelativeDuration(relStr.removePrefix("-").trim())
            if (offset > 0L) return nowMs - offset
        }

        return 0L
    }
}
