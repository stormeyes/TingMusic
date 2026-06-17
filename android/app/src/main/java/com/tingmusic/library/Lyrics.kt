package com.tingmusic.library

/** 一行同步歌词。 */
data class LyricLine(val timeMs: Long, val text: String)

/** 歌词:带时间戳的同步歌词,或纯文本回落。 */
sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>) : Lyrics
    data class Plain(val text: String) : Lyrics
}

object LrcParser {
    private const val PARSE_FAIL_RATIO = 0.5
    private val metadataTags = setOf("ti", "ar", "al", "by", "offset", "re", "ve", "au", "length")

    /** 解析 LRC 文本。坏行超过一半、或没有任何带时间戳的行 → 退化为 Plain。 */
    fun parse(text: String): Lyrics {
        val trimmed = text.removePrefix("﻿")
        val lines = ArrayList<LyricLine>()
        var total = 0
        var bad = 0
        for (rawLine in trimmed.lineSequence()) {
            val raw = rawLine.trim()
            if (raw.isEmpty()) continue
            total++
            val parsed = parseLine(raw)
            if (parsed == null) bad++ else lines.addAll(parsed)
        }
        if (total == 0) return Lyrics.Plain(text)
        if (bad.toDouble() / total.toDouble() > PARSE_FAIL_RATIO) return Lyrics.Plain(text)
        if (lines.isEmpty()) return Lyrics.Plain(text)
        lines.sortBy { it.timeMs }
        return Lyrics.Synced(lines)
    }

    /** 单行:可能有多个 [mm:ss.xx] 时间戳;元数据标签([ti:..] 等)被吃掉。 */
    private fun parseLine(raw: String): List<LyricLine>? {
        if (!raw.startsWith("[")) return null
        var rest = raw
        val times = ArrayList<Long>()
        while (rest.startsWith("[")) {
            val end = rest.indexOf(']')
            if (end < 0) return null
            val tag = rest.substring(1, end)
            rest = rest.substring(end + 1)
            val ms = parseTimestamp(tag)
            if (ms != null) {
                times.add(ms)
            } else if (isMetadataTag(tag)) {
                continue
            } else {
                return null
            }
        }
        if (times.isEmpty()) return emptyList()
        val lineText = rest.trim()
        return times.map { LyricLine(it, lineText) }
    }

    /** 接受 mm:ss.xx / mm:ss.xxx / mm:ss。 */
    private fun parseTimestamp(tag: String): Long? {
        val colon = tag.indexOf(':')
        if (colon < 0) return null
        val minutes = tag.substring(0, colon).toLongOrNull() ?: return null
        val rest = tag.substring(colon + 1)
        val dot = rest.indexOf('.')
        val secStr = if (dot < 0) rest else rest.substring(0, dot)
        val fracStr = if (dot < 0) "0" else rest.substring(dot + 1)
        val seconds = secStr.toLongOrNull() ?: return null
        if (seconds >= 60) return null
        val fracDigits = minOf(fracStr.length, 3)
        val frac = (if (fracDigits == 0) "0" else fracStr.substring(0, fracDigits)).toLongOrNull() ?: return null
        val fracMs = when (fracDigits) {
            0 -> 0L
            1 -> frac * 100
            2 -> frac * 10
            else -> frac
        }
        return minutes * 60_000 + seconds * 1000 + fracMs
    }

    private fun isMetadataTag(tag: String): Boolean =
        metadataTags.contains(tag.substringBefore(':'))
}
