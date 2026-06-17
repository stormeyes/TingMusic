package com.tingmusic.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayModeTest {
    @Test
    fun sequentialRepeatsAllNoShuffle() {
        assertEquals(Player.REPEAT_MODE_ALL, PlayMode.SEQUENTIAL.repeatMode)
        assertFalse(PlayMode.SEQUENTIAL.shuffle)
    }

    @Test
    fun randomShufflesRepeatAll() {
        assertEquals(Player.REPEAT_MODE_ALL, PlayMode.RANDOM.repeatMode)
        assertTrue(PlayMode.RANDOM.shuffle)
    }

    @Test
    fun repeatOneRepeatsOne() {
        assertEquals(Player.REPEAT_MODE_ONE, PlayMode.REPEAT_ONE.repeatMode)
        assertFalse(PlayMode.REPEAT_ONE.shuffle)
    }

    @Test
    fun cyclesInOrder() {
        assertEquals(PlayMode.RANDOM, PlayMode.SEQUENTIAL.next())
        assertEquals(PlayMode.REPEAT_ONE, PlayMode.RANDOM.next())
        assertEquals(PlayMode.SEQUENTIAL, PlayMode.REPEAT_ONE.next())
    }
}
