package com.tingmusic.library

import java.io.File

/** 一首本地(已同步)曲目。lrcFile 为 null 表示没有歌词。 */
data class Track(
    val id: String,            // 相对路径,稳定唯一
    val file: File,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val lrcFile: File?,
)
