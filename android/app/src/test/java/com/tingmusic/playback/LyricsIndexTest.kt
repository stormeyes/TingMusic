package com.tingmusic.playback

import com.tingmusic.library.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsIndexTest {
    private val lines = listOf(
        LyricLine(0, "a"),
        LyricLine(1000, "b"),
        LyricLine(2000, "c"),
        LyricLine(5000, "d"),
    )

    @Test
    fun beforeFirstReturnsZero() {
        // 所有行 time<=pos 取最后一个;pos 在第一行之前时仍返回 0(对齐 Web 端)
        assertEquals(0, LyricsIndex.activeIndex(lines, -10))
        assertEquals(0, LyricsIndex.activeIndex(lines, 0))
    }

    @Test
    fun picksLastLineNotAfterPosition() {
        assertEquals(0, LyricsIndex.activeIndex(lines, 999))
        assertEquals(1, LyricsIndex.activeIndex(lines, 1000))
        assertEquals(1, LyricsIndex.activeIndex(lines, 1500))
        assertEquals(2, LyricsIndex.activeIndex(lines, 2000))
        assertEquals(3, LyricsIndex.activeIndex(lines, 9999))
    }

    @Test
    fun emptyReturnsMinusOne() {
        assertEquals(-1, LyricsIndex.activeIndex(emptyList(), 1000))
    }
}
