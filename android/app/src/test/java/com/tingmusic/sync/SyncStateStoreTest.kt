package com.tingmusic.sync

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SyncStateStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun loadMissingFileReturnsEmpty() {
        val store = SyncStateStore(tmp.newFolder().resolve("state.json"))
        assertEquals(emptyMap<String, FileKey>(), store.load())
    }

    @Test
    fun roundTrip() {
        val store = SyncStateStore(tmp.newFolder().resolve("state.json"))
        val data = mapOf("a.mp3" to FileKey(10, 100), "sub/b.lrc" to FileKey(20, 200))
        store.save(data)
        assertEquals(data, store.load())
    }

    @Test
    fun corruptFileReturnsEmpty() {
        val f = tmp.newFolder().resolve("state.json")
        f.writeText("{not json")
        assertEquals(emptyMap<String, FileKey>(), SyncStateStore(f).load())
    }
}
