package com.tingmusic.sync

import org.json.JSONObject
import java.io.File

/**
 * 把"上次同步的 manifest 状态"(path -> size,mtime)存成一个 JSON 文件。
 * 损坏或不存在时返回空(下次会被当成全新同步)。
 */
class SyncStateStore(private val file: File) {

    fun load(): Map<String, FileKey> {
        if (!file.isFile) return emptyMap()
        return try {
            val root = JSONObject(file.readText())
            val out = HashMap<String, FileKey>(root.length())
            for (key in root.keys()) {
                val o = root.getJSONObject(key)
                out[key] = FileKey(o.getLong("size"), o.getLong("mtime"))
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun save(state: Map<String, FileKey>) {
        val root = JSONObject()
        for ((path, key) in state) {
            root.put(path, JSONObject().put("size", key.size).put("mtime", key.mtime))
        }
        file.parentFile?.mkdirs()
        file.writeText(root.toString())
    }
}
