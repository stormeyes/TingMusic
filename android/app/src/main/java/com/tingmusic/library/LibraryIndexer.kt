package com.tingmusic.library

import android.media.MediaMetadataRetriever
import java.io.File

/**
 * 扫描镜像目录,对每个音频文件用 MediaMetadataRetriever 取元数据,
 * 在同目录里按 LrcMatch 找 .lrc,产出按相对路径排序的曲目列表。
 */
class LibraryIndexer(private val mirrorRoot: File) {

    private val audioExts = setOf("mp3", "flac", "wav", "ogg", "m4a", "aac")

    fun index(): List<Track> {
        if (!mirrorRoot.isDirectory) return emptyList()
        val allFiles = mirrorRoot.walkTopDown().filter { it.isFile }.toList()
        val tracks = ArrayList<Track>()
        for (f in allFiles) {
            val ext = f.extension.lowercase()
            if (ext !in audioExts) continue
            val siblingLrcs = (f.parentFile?.listFiles { c -> c.extension.lowercase() == "lrc" }
                ?.map { it.name } ?: emptyList())
            val lrcName = LrcMatch.findFor(f.name, siblingLrcs)
            val lrcFile = lrcName?.let { File(f.parentFile, it) }
            tracks.add(readTrack(f, lrcFile))
        }
        return tracks.sortedBy { it.id }
    }

    private fun readTrack(file: File, lrcFile: File?): Track {
        val rel = file.relativeTo(mirrorRoot).path
        val fileNameStem = file.nameWithoutExtension
        val mmr = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationMs = 0L
        try {
            mmr.setDataSource(file.absolutePath)
            title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
            album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }
            durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            // 损坏/不支持的文件:用文件名兜底
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
        // 文件名兜底:Title-Artist(取最后一个连字符;去尾部 #hash)
        val (fnTitle, fnArtist) = parseFilenameStem(fileNameStem)
        return Track(
            id = rel,
            file = file,
            title = title ?: fnTitle,
            artist = artist ?: (fnArtist ?: "Unknown"),
            album = album ?: "Unknown",
            durationMs = durationMs,
            lrcFile = lrcFile,
        )
    }

    private fun parseFilenameStem(stem: String): Pair<String, String?> {
        val idx = stem.lastIndexOf('-')
        if (idx > 0) {
            val title = stem.substring(0, idx).trim()
            var artist = stem.substring(idx + 1).trim()
            val h = artist.lastIndexOf('#')
            if (h >= 0) artist = artist.substring(0, h).trimEnd()
            if (title.isNotEmpty() && artist.isNotEmpty()) return title to artist
        }
        return stem to null
    }
}
