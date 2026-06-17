package com.tingmusic.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncDiffTest {
    private fun entry(path: String, size: Long, mtime: Long) = ManifestEntry(path, size, mtime)

    @Test
    fun newFileIsDownloaded() {
        val plan = SyncDiff.compute(emptyMap(), listOf(entry("a.mp3", 10, 100)))
        assertEquals(listOf("a.mp3"), plan.toDownload.map { it.path })
        assertEquals(emptyList<String>(), plan.toDelete)
    }

    @Test
    fun unchangedFileIsSkipped() {
        val local = mapOf("a.mp3" to FileKey(10, 100))
        val plan = SyncDiff.compute(local, listOf(entry("a.mp3", 10, 100)))
        assertEquals(emptyList<String>(), plan.toDownload.map { it.path })
        assertEquals(emptyList<String>(), plan.toDelete)
    }

    @Test
    fun changedSizeOrMtimeReDownloads() {
        val local = mapOf("a.mp3" to FileKey(10, 100), "b.mp3" to FileKey(20, 200))
        val plan = SyncDiff.compute(local, listOf(entry("a.mp3", 11, 100), entry("b.mp3", 20, 201)))
        assertEquals(setOf("a.mp3", "b.mp3"), plan.toDownload.map { it.path }.toSet())
    }

    @Test
    fun missingFromRemoteIsDeleted() {
        val local = mapOf("gone.mp3" to FileKey(5, 50), "keep.mp3" to FileKey(6, 60))
        val plan = SyncDiff.compute(local, listOf(entry("keep.mp3", 6, 60)))
        assertEquals(listOf("gone.mp3"), plan.toDelete)
        assertEquals(emptyList<String>(), plan.toDownload.map { it.path })
    }
}
