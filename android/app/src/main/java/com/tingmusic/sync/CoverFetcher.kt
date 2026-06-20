package com.tingmusic.sync

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.tingmusic.library.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 无内嵌封面的曲目走 iTunes Search 拉 600x600 封面并本地缓存。镜像 Mac cover_fetch.rs。
 */
class CoverFetcher(
    private val context: Context,
    private val client: OkHttpClient = sharedClient,
) {
    /** 命中缓存或联网取;失败返回 null(调用方回落黑胶)。 */
    suspend fun fetch(title: String, artist: String, trackId: String): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "covers").apply { mkdirs() }
            val cacheFile = File(dir, cacheKey(trackId) + ".jpg")
            if (cacheFile.isFile && cacheFile.length() > 0) {
                return@withContext decode(cacheFile.readBytes())
            }
            if (title.isBlank()) return@withContext null
            try {
                val term = searchTerm(title, artist)
                val searchUrl = "https://itunes.apple.com/search?term=" +
                    URLEncoder.encode(term, "UTF-8") + "&media=music&limit=1"
                val body = httpGetString(searchUrl) ?: return@withContext null
                val art = parseArtworkUrl(body) ?: return@withContext null
                val bytes = httpGetBytes(upscale(art)) ?: return@withContext null
                if (bytes.isEmpty()) return@withContext null
                cacheFile.writeBytes(bytes)
                decode(bytes)
            } catch (_: Exception) {
                null
            }
        }

    /**
     * 锁屏 / 通知用的封面字节(JPEG/PNG)。来源优先级:内嵌图 → iTunes 本地缓存 →
     * iTunes 联网(顺带写缓存)。全程 IO 线程;取不到返回 null。
     */
    suspend fun coverBytes(track: Track): ByteArray? = withContext(Dispatchers.IO) {
        // 1) 内嵌封面
        val embedded = runCatching {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(track.file.absolutePath)
                mmr.embeddedPicture
            } finally {
                runCatching { mmr.release() }
            }
        }.getOrNull()
        if (embedded != null && embedded.isNotEmpty()) return@withContext embedded
        // 2) iTunes 本地缓存文件
        val cacheFile = File(File(context.cacheDir, "covers"), cacheKey(track.id) + ".jpg")
        if (cacheFile.isFile && cacheFile.length() > 0) return@withContext cacheFile.readBytes()
        // 3) iTunes 联网(fetch 命中后会写入同一缓存文件),成功则读回字节
        fetch(track.title, track.artist, track.id)
        if (cacheFile.isFile && cacheFile.length() > 0) cacheFile.readBytes() else null
    }

    private fun httpGetString(url: String): String? {
        client.newCall(Request.Builder().url(url).header("User-Agent", "TingMusic/0.1").build())
            .execute().use { r -> return if (r.isSuccessful) r.body?.string() else null }
    }

    private fun httpGetBytes(url: String): ByteArray? {
        client.newCall(Request.Builder().url(url).header("User-Agent", "TingMusic/0.1").build())
            .execute().use { r -> return if (r.isSuccessful) r.body?.bytes() else null }
    }

    private fun decode(bytes: ByteArray): ImageBitmap? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

    companion object {
        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()
        }

        fun searchTerm(title: String, artist: String): String {
            val t = title.trim()
            val a = artist.trim()
            return if (a.isEmpty() || a == "Unknown") t else "$t $a"
        }

        /** iTunes 返回 100x100bb;换成 600x600bb 拿可用尺寸。 */
        fun upscale(url: String): String = url.replace("100x100bb", "600x600bb")

        fun parseArtworkUrl(json: String): String? {
            val results = JSONObject(json).optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            return results.getJSONObject(0).optString("artworkUrl100").ifBlank { null }
        }

        /** trackId(相对路径,含 /)→ 文件系统安全的稳定 key(sha256 十六进制)。 */
        fun cacheKey(trackId: String): String {
            val d = MessageDigest.getInstance("SHA-256").digest(trackId.toByteArray())
            return d.joinToString("") { "%02x".format(it) }
        }
    }
}
