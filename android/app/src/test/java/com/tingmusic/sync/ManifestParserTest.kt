package com.tingmusic.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ManifestParserTest {
    @Test
    fun parsesVersionLibraryAndFiles() {
        val json = """
            {"version":1,"library_name":"TingMusic","files":[
              {"path":"a.mp3","size":100,"mtime":1778302258},
              {"path":"sub/b.lrc","size":20,"mtime":1778302259}
            ]}
        """.trimIndent()
        val m = ManifestParser.parse(json)
        assertEquals(1, m.version)
        assertEquals("TingMusic", m.libraryName)
        assertEquals(2, m.files.size)
        assertEquals("a.mp3", m.files[0].path)
        assertEquals(100L, m.files[0].size)
        assertEquals(1778302258L, m.files[0].mtime)
        assertEquals("sub/b.lrc", m.files[1].path)
    }

    @Test
    fun emptyFilesList() {
        val m = ManifestParser.parse("""{"version":1,"library_name":"X","files":[]}""")
        assertEquals(0, m.files.size)
        assertEquals("X", m.libraryName)
    }
}
