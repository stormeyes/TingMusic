package com.tingmusic.library

/**
 * 给定一个音频文件名,在同目录的 .lrc 文件名列表里找匹配。
 * 优先精确同名(去扩展名、忽略大小写);否则去掉尾部 `#hash` 后再比(网易云/QQ 缓存命名)。
 * 移植自 Rust lyrics.rs 的 load_sidecar_lrc。
 */
object LrcMatch {
    fun findFor(audioFileName: String, lrcFileNames: List<String>): String? {
        val audioStem = stem(audioFileName).lowercase()
        val audioNorm = stripHash(audioStem)
        var fallback: String? = null
        for (lrc in lrcFileNames) {
            val lrcStem = stem(lrc).lowercase()
            if (lrcStem == audioStem) return lrc
            if (fallback == null && stripHash(lrcStem) == audioNorm) fallback = lrc
        }
        return fallback
    }

    private fun stem(name: String): String {
        val slash = name.lastIndexOf('/')
        val base = if (slash >= 0) name.substring(slash + 1) else name
        val dot = base.lastIndexOf('.')
        return if (dot > 0) base.substring(0, dot) else base
    }

    private fun stripHash(s: String): String {
        val h = s.lastIndexOf('#')
        return if (h >= 0) s.substring(0, h).trimEnd() else s
    }
}
