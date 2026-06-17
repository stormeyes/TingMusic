package com.tingmusic.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcMatchTest {
    @Test
    fun exactStemMatch() {
        val lrcs = listOf("Song.lrc", "Other.lrc")
        assertEquals("Song.lrc", LrcMatch.findFor("Song.mp3", lrcs))
    }

    @Test
    fun hashSuffixTolerated() {
        // 音频带 #hash 缓存后缀,lrc 不带 -> 仍匹配
        val lrcs = listOf("NIGHT DANCER-imase.lrc")
        assertEquals("NIGHT DANCER-imase.lrc", LrcMatch.findFor("NIGHT DANCER-imase#2ryCf3.mp3", lrcs))
    }

    @Test
    fun exactWinsOverHashNormalized() {
        val lrcs = listOf("Song.lrc", "Song#abc.lrc")
        assertEquals("Song#abc.lrc", LrcMatch.findFor("Song#abc.mp3", lrcs))
    }

    @Test
    fun noMatchReturnsNull() {
        assertEquals(null, LrcMatch.findFor("A.mp3", listOf("B.lrc")))
    }
}
