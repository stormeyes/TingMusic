package com.tingmusic.sync

import org.json.JSONObject

/** Mac 端 /manifest 里的一个文件项(相对路径 + 大小 + 修改时间秒)。 */
data class ManifestEntry(val path: String, val size: Long, val mtime: Long)

/** /manifest 的完整内容。 */
data class Manifest(val version: Int, val libraryName: String, val files: List<ManifestEntry>)

object ManifestParser {
    fun parse(json: String): Manifest {
        val root = JSONObject(json)
        val arr = root.getJSONArray("files")
        val files = ArrayList<ManifestEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            files.add(ManifestEntry(e.getString("path"), e.getLong("size"), e.getLong("mtime")))
        }
        return Manifest(
            version = root.optInt("version", 1),
            libraryName = root.optString("library_name", ""),
            files = files,
        )
    }
}
