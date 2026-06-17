package com.tingmusic.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesBasicSynced() {
        val l = LrcParser.parse("[00:01.00]Hello\n[00:02.50]World")
        l as Lyrics.Synced
        assertEquals(2, l.lines.size)
        assertEquals(1000L, l.lines[0].timeMs)
        assertEquals("Hello", l.lines[0].text)
        assertEquals(2500L, l.lines[1].timeMs)
    }

    @Test
    fun handlesMetadataHeader() {
        val l = LrcParser.parse("[ti:Song]\n[ar:Artist]\n[00:01.00]Line")
        l as Lyrics.Synced
        assertEquals(1, l.lines.size)
    }

    @Test
    fun multiTimestampLineYieldsMultipleEntries() {
        val l = LrcParser.parse("[00:01.00][00:05.00]Repeat")
        l as Lyrics.Synced
        assertEquals(2, l.lines.size)
        assertEquals(1000L, l.lines[0].timeMs)
        assertEquals(5000L, l.lines[1].timeMs)
    }

    @Test
    fun sortsUnorderedInput() {
        val l = LrcParser.parse("[00:05.00]Late\n[00:01.00]Early") as Lyrics.Synced
        assertEquals("Early", l.lines[0].text)
        assertEquals("Late", l.lines[1].text)
    }

    @Test
    fun handlesBom() {
        val l = LrcParser.parse("﻿[00:01.00]Hi") as Lyrics.Synced
        assertEquals("Hi", l.lines[0].text)
    }

    @Test
    fun degradesToPlainWhenMajorityCorrupt() {
        val l = LrcParser.parse("garbage1\ngarbage2\ngarbage3\n[00:01.00]Good")
        assertTrue(l is Lyrics.Plain)
    }

    @Test
    fun emptyInputIsPlain() {
        val l = LrcParser.parse("")
        assertTrue(l is Lyrics.Plain)
    }

    @Test
    fun parsesMillisThreeDigits() {
        val l = LrcParser.parse("[00:01.234]X") as Lyrics.Synced
        assertEquals(1234L, l.lines[0].timeMs)
    }
}
