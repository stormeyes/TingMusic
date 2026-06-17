package com.tingmusic.playback

import androidx.media3.common.Player

/** 三种播放模式,映射到 Media3 的 repeat/shuffle。 */
enum class PlayMode(val repeatMode: Int, val shuffle: Boolean) {
    SEQUENTIAL(Player.REPEAT_MODE_ALL, false),
    RANDOM(Player.REPEAT_MODE_ALL, true),
    REPEAT_ONE(Player.REPEAT_MODE_ONE, false);

    fun next(): PlayMode = when (this) {
        SEQUENTIAL -> RANDOM
        RANDOM -> REPEAT_ONE
        REPEAT_ONE -> SEQUENTIAL
    }
}
