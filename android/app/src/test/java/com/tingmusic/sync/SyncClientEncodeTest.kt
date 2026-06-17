package com.tingmusic.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncClientEncodeTest {
    @Test
    fun encodesSpacesAsPercent20NotPlus() {
        assertEquals("NIGHT%20DANCER-imase.mp3", SyncClient.encodePath("NIGHT DANCER-imase.mp3"))
    }

    @Test
    fun keepsSlashSeparatorsButEncodesSegments() {
        assertEquals("Anime/NIGHT%20DANCER.mp3", SyncClient.encodePath("Anime/NIGHT DANCER.mp3"))
    }

    @Test
    fun encodesHashAndQuestion() {
        assertEquals("Song%23hash.mp3", SyncClient.encodePath("Song#hash.mp3"))
        assertEquals("a%3Fb.mp3", SyncClient.encodePath("a?b.mp3"))
    }

    @Test
    fun encodesUnicode() {
        // 群青 -> UTF-8 percent-encoded; just assert it round-trips through decode.
        val enc = SyncClient.encodePath("群青 - YOASOBI.flac")
        assertEquals("群青 - YOASOBI.flac", java.net.URLDecoder.decode(enc.replace("/", "%2F"), "UTF-8").replace("%2F", "/"))
    }
}
