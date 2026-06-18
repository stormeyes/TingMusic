package com.tingmusic.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverFetcherTest {
    @Test fun termUsesTitleAndArtist() {
        assertEquals("NIGHT DANCER imase", CoverFetcher.searchTerm("NIGHT DANCER", "imase"))
    }
    @Test fun termTitleOnlyWhenArtistBlankOrUnknown() {
        assertEquals("Song", CoverFetcher.searchTerm("Song", ""))
        assertEquals("Song", CoverFetcher.searchTerm("Song", "Unknown"))
        assertEquals("Song", CoverFetcher.searchTerm("  Song  ", "  "))
    }
    @Test fun upscaleReplaces100With600() {
        assertEquals(
            "https://x/600x600bb.jpg",
            CoverFetcher.upscale("https://x/100x100bb.jpg"),
        )
    }
    @Test fun parsesArtworkUrl() {
        val json = """{"resultCount":1,"results":[{"artworkUrl100":"https://x/100x100bb.jpg"}]}"""
        assertEquals("https://x/100x100bb.jpg", CoverFetcher.parseArtworkUrl(json))
    }
    @Test fun parseEmptyResultsReturnsNull() {
        assertNull(CoverFetcher.parseArtworkUrl("""{"resultCount":0,"results":[]}"""))
    }
    @Test fun cacheKeyStableAndFilesystemSafe() {
        val k1 = CoverFetcher.cacheKey("Anime/NIGHT DANCER-imase.mp3")
        val k2 = CoverFetcher.cacheKey("Anime/NIGHT DANCER-imase.mp3")
        assertEquals(k1, k2)
        assert(k1.matches(Regex("[a-f0-9]+"))) // 仅十六进制,无路径分隔符
    }
}
