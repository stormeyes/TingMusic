package com.tingmusic.sync

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** 跟 Mac 端同步服务通信:拉 manifest、下载文件。 */
class SyncClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    /** GET {baseUrl}/manifest -> 解析后的 Manifest。 */
    fun fetchManifest(baseUrl: String): Manifest {
        val req = Request.Builder().url(trim(baseUrl) + "/manifest").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("manifest HTTP ${resp.code}")
            val body = resp.body?.string() ?: error("empty manifest body")
            return ManifestParser.parse(body)
        }
    }

    /** GET {baseUrl}/files/{encoded relPath} -> 流式写入 dest(先写 .part 再 rename)。 */
    fun downloadFile(baseUrl: String, relPath: String, dest: File) {
        val url = trim(baseUrl) + "/files/" + encodePath(relPath)
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("file HTTP ${resp.code} for $relPath")
            val body = resp.body ?: error("empty body for $relPath")
            dest.parentFile?.mkdirs()
            val part = File(dest.parentFile, dest.name + ".part")
            body.byteStream().use { input ->
                part.outputStream().use { out -> input.copyTo(out, bufferSize = 64 * 1024) }
            }
            if (!part.renameTo(dest)) {
                part.copyTo(dest, overwrite = true)
                part.delete()
            }
        }
    }

    private fun trim(base: String) = base.trimEnd('/')

    companion object {
        /**
         * 逐段 percent-encode 相对路径,保留 `/` 分隔符。空格编码为 %20(不是 +)。
         * 对应 Mac 端 axum `/files/{*path}` 的解码方式(spec §4.5)。
         */
        fun encodePath(rel: String): String =
            rel.split('/').joinToString("/") { seg ->
                URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
            }
    }
}
