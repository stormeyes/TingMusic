package com.tingmusic.playback

import com.tingmusic.library.LyricLine

object LyricsIndex {
    /** 返回 time_ms <= pos 的最后一行下标;空列表返回 -1;pos 在首行前返回 0。 */
    fun activeIndex(lines: List<LyricLine>, posMs: Long): Int {
        if (lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.size - 1
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= posMs) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }
}
