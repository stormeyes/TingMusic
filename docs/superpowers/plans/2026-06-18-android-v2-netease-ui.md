# Android v2 — 网易云风 UI + iTunes 封面 + 主题 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把安卓 UI 重做成网易云风(左抽屉 + 列表主屏 + 底部 mini 条 + 全屏播放页:旋转黑胶 + 唱臂,点碟⇄滚动歌词),并加 iTunes 联网封面与两套主题。

**Architecture:** 播放核心(Service/Controller/PlayMode/LyricsIndex/LrcParser)与 sync/library 层不动;只重做 UI 层并新增 `CoverFetcher`(iTunes)、`SettingsStore`(主题持久化)、`ui/theme`。导航用状态驱动(`ModalNavigationDrawer` + `AnimatedVisibility`),不引 Navigation-Compose。

**Tech Stack:** Kotlin 2.2.10、Compose(BOM 2026.02.01,Material3)、Coroutines、OkHttp、org.json、Media3(已在)。新依赖:无(全部复用已有)。

## Global Constraints

- 每个 `./gradlew` 命令必须先 `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`,在 `android/` 目录运行。
- AGP 9.2.0 **内置 Kotlin**:**不要** apply `org.jetbrains.kotlin.android`。
- compileSdk 36 / minSdk 26;`Modifier.blur` 仅 API 31+ 生效,API<31 需优雅退化(scrim)。
- 不引入新第三方依赖(OkHttp/org.json/coroutines/lifecycle-compose/media3 均已在);不引 Room/KSP/Navigation-Compose。
- 不运行任何 linter(ktlint/detekt 等);只用 `./gradlew :app:testDebugUnitTest` / `:app:assembleDebug` 验证。
- 真机:`ADB=~/Library/Android/sdk/platform-tools/adb`,命令前 `$ADB wait-for-device`;手机有安全锁屏,真机验证前需用户手动解锁。
- 关联 spec:`docs/superpowers/specs/2026-06-18-tingmusic-android-v2-design.md`。
- 现有可复用类型:`library.Track(id,file,title,artist,album,durationMs,lrcFile)`、`library.LrcParser.parse(String): Lyrics`、`library.Lyrics.Synced(lines)/Plain(text)`、`library.LyricLine(timeMs,text)`、`playback.PlaybackController{ state: StateFlow<PlaybackState>, connect/release/setLibrary/play(Track)/togglePlayPause/next/prev/seekTo(Long)/cycleMode/currentPositionMs() }`、`playback.PlaybackState(currentId,isPlaying,positionMs,durationMs,mode)`、`playback.PlayMode{SEQUENTIAL,RANDOM,REPEAT_ONE; next()}`、`playback.LyricsIndex.activeIndex(lines,posMs)`、`ui.SyncViewModel{ state: StateFlow<UiState>; startDiscovery/setManualHost/sync(baseUrl)/manualBaseUrl() }`、`ui.UiState(servers,manualHost,progress,tracks,syncing)`、`sync.DiscoveredServer(name,host,port,baseUrl)`、`sync.SyncProgress`。

---

### Task 1: 主题地基(AppTheme + ColorScheme + TingMusicTheme + SettingsStore）

**Files:**
- Create: `android/app/src/main/java/com/tingmusic/ui/theme/Theme.kt`
- Create: `android/app/src/main/java/com/tingmusic/data/SettingsStore.kt`
- Create: `android/app/src/test/java/com/tingmusic/ui/theme/AppThemeTest.kt`

**Interfaces:**
- Produces: `enum AppTheme { DEFAULT, WHITE_RED; companion fun fromStored(name: String?): AppTheme }`;`@Composable fun TingMusicTheme(theme: AppTheme, content: @Composable () -> Unit)`;`class SettingsStore(context: Context) { val theme: StateFlow<AppTheme>; fun setTheme(t: AppTheme) }`

- [ ] **Step 1: 写 AppTheme.fromStored 的失败测试**

Create `android/app/src/test/java/com/tingmusic/ui/theme/AppThemeTest.kt`:
```kotlin
package com.tingmusic.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeTest {
    @Test fun knownNames() {
        assertEquals(AppTheme.DEFAULT, AppTheme.fromStored("DEFAULT"))
        assertEquals(AppTheme.WHITE_RED, AppTheme.fromStored("WHITE_RED"))
    }
    @Test fun nullOrUnknownDefaults() {
        assertEquals(AppTheme.DEFAULT, AppTheme.fromStored(null))
        assertEquals(AppTheme.DEFAULT, AppTheme.fromStored("nope"))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "com.tingmusic.ui.theme.AppThemeTest"`
Expected: 编译失败(`AppTheme` 未定义)。

- [ ] **Step 3: 实现 Theme.kt**

Create `android/app/src/main/java/com/tingmusic/ui/theme/Theme.kt`:
```kotlin
package com.tingmusic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppTheme {
    DEFAULT,    // 中性灰,红仅作锚点
    WHITE_RED;  // 白底 + 红强调(网易云外链风)

    companion object {
        fun fromStored(name: String?): AppTheme =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

private val Red = Color(0xFFD33A31)

// 默认:暗灰中性,主色用红作锚点
private val DefaultColors = darkColorScheme(
    primary = Red,
    onPrimary = Color.White,
    background = Color(0xFF242424),
    surface = Color(0xFF2C2C2C),
    onBackground = Color(0xFFEDEDED),
    onSurface = Color(0xFFEDEDED),
    onSurfaceVariant = Color(0xFFAFAFAF),
)

// 白红:白底,红强调
private val WhiteRedColors = lightColorScheme(
    primary = Red,
    onPrimary = Color.White,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF8A8A8A),
)

@Composable
fun TingMusicTheme(theme: AppTheme, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (theme == AppTheme.WHITE_RED) WhiteRedColors else DefaultColors,
        content = content,
    )
}
```

- [ ] **Step 4: 实现 SettingsStore.kt**

Create `android/app/src/main/java/com/tingmusic/data/SettingsStore.kt`:
```kotlin
package com.tingmusic.data

import android.content.Context
import com.tingmusic.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 用 SharedPreferences 持久化主题等设置。零依赖。 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("tingmusic_settings", Context.MODE_PRIVATE)
    private val _theme = MutableStateFlow(AppTheme.fromStored(prefs.getString(KEY_THEME, null)))
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    fun setTheme(t: AppTheme) {
        prefs.edit().putString(KEY_THEME, t.name).apply()
        _theme.value = t
    }

    private companion object { const val KEY_THEME = "theme" }
}
```

- [ ] **Step 5: 在 SyncViewModel 接入 SettingsStore(暴露 theme + setTheme)**

修改 `android/app/src/main/java/com/tingmusic/ui/SyncViewModel.kt`:在类体内(`indexer` 字段附近)加:
```kotlin
    private val settings = com.tingmusic.data.SettingsStore(app)
    val theme: kotlinx.coroutines.flow.StateFlow<com.tingmusic.ui.theme.AppTheme> = settings.theme
    fun setTheme(t: com.tingmusic.ui.theme.AppTheme) = settings.setTheme(t)
```

- [ ] **Step 6: 跑测试 + 编译**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "com.tingmusic.ui.theme.AppThemeTest" && ./gradlew :app:assembleDebug`
Expected: AppThemeTest 2 个 PASS;BUILD SUCCESSFUL。

- [ ] **Step 7: Commit**
```bash
git add android/app/src/main/java/com/tingmusic/ui/theme/Theme.kt android/app/src/main/java/com/tingmusic/data/SettingsStore.kt android/app/src/test/java/com/tingmusic/ui/theme/AppThemeTest.kt android/app/src/main/java/com/tingmusic/ui/SyncViewModel.kt
git commit -m "feat(android-v2): theme color schemes + SettingsStore (SharedPreferences)"
```

---

### Task 2: iTunes 封面 CoverFetcher + CoverImage 组件

**Files:**
- Create: `android/app/src/main/java/com/tingmusic/sync/CoverFetcher.kt`
- Create: `android/app/src/main/java/com/tingmusic/ui/CoverImage.kt`
- Create: `android/app/src/test/java/com/tingmusic/sync/CoverFetcherTest.kt`
- Delete (在 Step 6):`android/app/src/main/java/com/tingmusic/ui/CoverArt.kt`(逻辑并入 CoverImage)

**Interfaces:**
- Consumes: `library.Track`
- Produces: `CoverFetcher` 伴生纯函数 `searchTerm(title,artist): String`、`upscale(url): String`、`parseArtworkUrl(json): String?`、`cacheKey(trackId): String`;`class CoverFetcher(context, client) { suspend fun fetch(title,artist,trackId): ImageBitmap? }`;`@Composable fun CoverImage(track: Track?, isPlaying: Boolean, modifier, sizeDp: Int, vinylFrame: Boolean)`

- [ ] **Step 1: 写 CoverFetcher 纯函数的失败测试**

Create `android/app/src/test/java/com/tingmusic/sync/CoverFetcherTest.kt`:
```kotlin
package com.tingmusic.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverFetcherTest {
    @Test fun termUsesTitleAndArtist() {
        assertEquals("NIGHT DANCER imase", CoverFetcher.searchTerm("NIGHT DANCER", "imase"))
    }
    @Test fun termTitleOnlyWhenArtistBlankOrUnknown() {
        assertEquals("Song", CoverFetcher.searchTerm("Song", ""))
        assertEquals("Song", CoverFetcher.searchTerm("Song", "Unknown"))
        assertEquals("Song", CoverFetcher.searchTerm("  Song  ", "  "))
    }
    @Test fun upscaleReplaces100With600() {
        assertEquals(
            "https://x/600x600bb.jpg",
            CoverFetcher.upscale("https://x/100x100bb.jpg"),
        )
    }
    @Test fun parsesArtworkUrl() {
        val json = """{"resultCount":1,"results":[{"artworkUrl100":"https://x/100x100bb.jpg"}]}"""
        assertEquals("https://x/100x100bb.jpg", CoverFetcher.parseArtworkUrl(json))
    }
    @Test fun parseEmptyResultsReturnsNull() {
        assertNull(CoverFetcher.parseArtworkUrl("""{"resultCount":0,"results":[]}"""))
    }
    @Test fun cacheKeyStableAndFilesystemSafe() {
        val k1 = CoverFetcher.cacheKey("Anime/NIGHT DANCER-imase.mp3")
        val k2 = CoverFetcher.cacheKey("Anime/NIGHT DANCER-imase.mp3")
        assertEquals(k1, k2)
        assert(k1.matches(Regex("[a-f0-9]+"))) // 仅十六进制,无路径分隔符
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "com.tingmusic.sync.CoverFetcherTest"`
Expected: 编译失败(`CoverFetcher` 未定义)。

- [ ] **Step 3: 实现 CoverFetcher.kt**

Create `android/app/src/main/java/com/tingmusic/sync/CoverFetcher.kt`:
```kotlin
package com.tingmusic.sync

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .build(),
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
```

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2。Expected: 6 个测试 PASS。

- [ ] **Step 5: 实现 CoverImage.kt(内嵌→iTunes→黑胶,旋转仅大碟)**

Create `android/app/src/main/java/com/tingmusic/ui/CoverImage.kt`:
```kotlin
package com.tingmusic.ui

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tingmusic.library.Track
import com.tingmusic.sync.CoverFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 取封面:内嵌 → iTunes(带缓存)→ null。全程 IO 线程。 */
@Composable
private fun rememberCover(track: Track?): ImageBitmap? {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = null, key1 = track?.id) {
        value = null
        val t = track ?: return@produceState
        // 1) 内嵌
        val embedded = withContext(Dispatchers.IO) {
            runCatching {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(t.file.absolutePath)
                    mmr.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                } finally { runCatching { mmr.release() } }
            }.getOrNull()
        }
        if (embedded != null) { value = embedded; return@produceState }
        // 2) iTunes(带本地缓存)
        value = CoverFetcher(context).fetch(t.title, t.artist, t.id)
    }
    return bmp
}

@Composable
fun CoverImage(
    track: Track?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    sizeDp: Int = 48,
    vinylFrame: Boolean = false,
) {
    val cover = rememberCover(track)
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    val spin = if (vinylFrame && isPlaying) Modifier.rotate(angle) else Modifier
    Box(modifier.size(sizeDp.dp).clip(CircleShape)) {
        if (cover != null) {
            if (vinylFrame) {
                // 封面嵌进黑胶圈:外圈深色 + 中间封面圆
                Canvas(Modifier.size(sizeDp.dp).then(spin)) {
                    val r = size.minDimension / 2f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(Color(0xFF0A0A0A), radius = r, center = c)
                }
                Image(
                    cover, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size((sizeDp * 0.66f).dp).clip(CircleShape).then(spin),
                )
            } else {
                Image(cover, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(sizeDp.dp).clip(CircleShape))
            }
        } else {
            Canvas(Modifier.size(sizeDp.dp).then(spin)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(Color(0xFF0A0A0A), radius = r, center = c)
                drawCircle(Color(0x14FFFFFF), radius = r * 0.8f, center = c, style = Stroke(width = r * 0.02f))
                drawCircle(Color(0x14FFFFFF), radius = r * 0.6f, center = c, style = Stroke(width = r * 0.02f))
                drawCircle(Color(0xFFD33A31), radius = r * 0.3f, center = c)
                drawCircle(Color(0xFF0A0A0A), radius = r * 0.05f, center = c)
            }
        }
    }
}
```

- [ ] **Step 6: 删除旧 CoverArt.kt(其调用者在 Task 3 改用 CoverImage)**

注意:`ui/CoverArt.kt` 当前被 `ui/NowPlaying.kt` 引用。本步骤删除 CoverArt.kt 后**会编译失败**,直到 Task 3 删除/重写 NowPlaying.kt。因此:**本步骤先不删**,把删除挪到 Task 3 Step 1(连同 SyncScreen.kt、NowPlaying.kt 一起退役)。此处仅确认新文件编译:

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL(CoverImage 与旧 CoverArt 暂时并存,均能编译)。

- [ ] **Step 7: Commit**
```bash
git add android/app/src/main/java/com/tingmusic/sync/CoverFetcher.kt android/app/src/main/java/com/tingmusic/ui/CoverImage.kt android/app/src/test/java/com/tingmusic/sync/CoverFetcherTest.kt
git commit -m "feat(android-v2): iTunes CoverFetcher (+tests) and CoverImage fallback chain"
```

---

### Task 3: 导航外壳(抽屉 + 列表主屏 + mini 条 + 基础播放页覆盖层)

**Files:**
- Create: `android/app/src/main/java/com/tingmusic/ui/PlaylistScreen.kt`
- Create: `android/app/src/main/java/com/tingmusic/ui/MiniPlayer.kt`
- Create: `android/app/src/main/java/com/tingmusic/ui/DrawerContent.kt`
- Create: `android/app/src/main/java/com/tingmusic/ui/NowPlayingScreen.kt`(本任务为**基础版**:封面+标题+进度+控制+⌄+点碟切歌词;Task 4 再加模糊背景/旋转碟/唱臂)
- Modify: `android/app/src/main/java/com/tingmusic/MainActivity.kt`(全重写:主题 + 抽屉 + Scaffold + mini 条 + 播放页覆盖层 + 位置轮询 + BackHandler)
- Delete: `android/app/src/main/java/com/tingmusic/ui/SyncScreen.kt`、`android/app/src/main/java/com/tingmusic/ui/NowPlaying.kt`、`android/app/src/main/java/com/tingmusic/ui/CoverArt.kt`

**Interfaces:**
- Consumes: Task1 `TingMusicTheme`/`AppTheme`/`SyncViewModel.theme/setTheme`;Task2 `CoverImage`;既有 `PlaybackController`/`PlaybackState`/`SyncViewModel`/`Track`。
- Produces: `PlaylistScreen(tracks, currentId, onPlay)`;`MiniPlayer(track, isPlaying, onToggle, onOpen)`;`DrawerContent(vm, theme, onSetTheme)`;`NowPlayingScreen(track, state, livePositionMs, onClose, onToggle, onNext, onPrev, onSeek, onCycleMode)`。

- [ ] **Step 1: 删除退役文件**

```bash
git rm android/app/src/main/java/com/tingmusic/ui/SyncScreen.kt android/app/src/main/java/com/tingmusic/ui/NowPlaying.kt android/app/src/main/java/com/tingmusic/ui/CoverArt.kt
```
(此时项目暂时编译不过——MainActivity 仍引用它们;后续 Step 把 MainActivity 重写好即可。)

- [ ] **Step 2: PlaylistScreen.kt**

Create `android/app/src/main/java/com/tingmusic/ui/PlaylistScreen.kt`:
```kotlin
package com.tingmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tingmusic.library.Track

@Composable
fun PlaylistScreen(
    tracks: List<Track>,
    currentId: String?,
    onPlay: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) {
        Column(modifier.fillMaxSize().padding(24.dp)) {
            Text("曲库是空的", style = MaterialTheme.typography.titleMedium)
            Text("打开左上角抽屉,扫描局域网内的 Mac 同步曲库。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }
        return
    }
    LazyColumn(modifier.fillMaxSize()) {
        items(tracks, key = { it.id }) { t ->
            Row(
                Modifier.fillMaxWidth().clickable { onPlay(t) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverImage(track = t, isPlaying = false, sizeDp = 48)
                Column(Modifier.padding(start = 12.dp).fillMaxWidth()) {
                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (t.id == currentId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text("${t.artist}${if (t.lrcFile != null) "  · 有歌词" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
```

- [ ] **Step 3: MiniPlayer.kt**

Create `android/app/src/main/java/com/tingmusic/ui/MiniPlayer.kt`:
```kotlin
package com.tingmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tingmusic.library.Track

@Composable
fun MiniPlayer(track: Track, isPlaying: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().clickable { onOpen() }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(track = track, isPlaying = false, sizeDp = 40)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onToggle) {
                if (isPlaying) Icon(Icons.Filled.Pause, "暂停") else Icon(Icons.Filled.PlayArrow, "播放")
            }
        }
    }
}
```

- [ ] **Step 4: DrawerContent.kt(主题 + 局域网同步 + 关于)**

Create `android/app/src/main/java/com/tingmusic/ui/DrawerContent.kt`:
```kotlin
package com.tingmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingmusic.sync.SyncProgress
import com.tingmusic.ui.theme.AppTheme

@Composable
fun DrawerContent(vm: SyncViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    ModalDrawerSheet {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("TingMusic", style = MaterialTheme.typography.titleLarge)

            Text("主题", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                listOf(AppTheme.DEFAULT to "默认灰", AppTheme.WHITE_RED to "白红").forEach { (t, label) ->
                    if (t == theme) Button(onClick = { vm.setTheme(t) }) { Text(label) }
                    else OutlinedButton(onClick = { vm.setTheme(t) }) { Text(label) }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("局域网同步", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                OutlinedButton(onClick = { vm.startDiscovery() }) { Text("扫描 Mac") }
                Button(onClick = { vm.sync(vm.manualBaseUrl()) }, enabled = !s.syncing && s.manualHost.isNotBlank()) { Text("手填同步") }
            }
            OutlinedTextField(
                value = s.manualHost, onValueChange = { vm.setManualHost(it) },
                label = { Text("手动 IP(如 192.168.5.139)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            s.servers.forEach { server ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${server.name} — ${server.host}:${server.port}", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { vm.sync(server.baseUrl) }, enabled = !s.syncing) { Text("同步") }
                }
            }
            s.progress?.let { Text(progressText(it), modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall) }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("关于", style = MaterialTheme.typography.titleSmall)
            Text("TingMusic v0.1.0", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun progressText(p: SyncProgress): String = when (p) {
    is SyncProgress.FetchingManifest -> "正在获取清单…"
    is SyncProgress.Downloading -> "下载中 ${p.done}/${p.total}:${p.current}"
    is SyncProgress.Done -> "完成:下载 ${p.downloaded},删除 ${p.deleted},失败 ${p.failed}"
    is SyncProgress.Failed -> "失败:${p.message}"
}
```

- [ ] **Step 5: NowPlayingScreen.kt(基础版)**

Create `android/app/src/main/java/com/tingmusic/ui/NowPlayingScreen.kt`:
```kotlin
package com.tingmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tingmusic.library.LrcParser
import com.tingmusic.library.Lyrics
import com.tingmusic.library.Track
import com.tingmusic.playback.LyricsIndex
import com.tingmusic.playback.PlayMode
import com.tingmusic.playback.PlaybackState

private fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }
private fun modeLabel(m: PlayMode) = when (m) { PlayMode.SEQUENTIAL -> "顺序"; PlayMode.RANDOM -> "随机"; PlayMode.REPEAT_ONE -> "单曲" }

@Composable
fun NowPlayingScreen(
    track: Track,
    state: PlaybackState,
    livePositionMs: Long,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit,
) {
    var lyricsMode by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "收起") }
            // 中央:点击在碟 / 歌词间切换(Task 4 把碟做成旋转黑胶+唱臂+模糊背景)
            Box(Modifier.weight(1f).fillMaxWidth().clickable { lyricsMode = !lyricsMode },
                contentAlignment = Alignment.Center) {
                if (!lyricsMode) {
                    CoverImage(track = track, isPlaying = state.isPlaying, sizeDp = 260, vinylFrame = true)
                } else {
                    LyricsView(track = track, positionMs = livePositionMs)
                }
            }
            Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val dur = if (state.durationMs > 0) state.durationMs else track.durationMs
            Slider(value = if (dur > 0) (livePositionMs.toFloat() / dur) else 0f, onValueChange = { onSeek((it * dur).toLong()) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${fmt(livePositionMs)} / ${fmt(dur)}", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrev) { Icon(Icons.Filled.SkipPrevious, "上一首") }
                    IconButton(onClick = onToggle) { if (state.isPlaying) Icon(Icons.Filled.Pause, "暂停") else Icon(Icons.Filled.PlayArrow, "播放") }
                    IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, "下一首") }
                }
                TextButton(onClick = onCycleMode) { Text(modeLabel(state.mode)) }
            }
        }
    }
}

/** 同步歌词:解析当前曲 .lrc,按 position 高亮+居中。解析按 track 记忆(避免每帧重读)。 */
@Composable
fun LyricsView(track: Track?, positionMs: Long, modifier: Modifier = Modifier) {
    val lyrics = remember(track) {
        val lrc = track?.lrcFile
        if (lrc != null && lrc.isFile) runCatching { LrcParser.parse(lrc.readText()) }.getOrNull() else null
    }
    when (lyrics) {
        is Lyrics.Synced -> {
            val lines = lyrics.lines
            val active = LyricsIndex.activeIndex(lines, positionMs)
            val listState = rememberLazyListState()
            LaunchedEffect(active) { if (active >= 0) listState.animateScrollToItem(active.coerceAtLeast(0)) }
            LazyColumn(state = listState, modifier = modifier.fillMaxWidth()) {
                items(lines.size) { i ->
                    Text(lines[i].text.ifBlank { "♪" },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 16.dp),
                        color = if (i == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (i == active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        is Lyrics.Plain -> Text(lyrics.text, modifier.padding(16.dp))
        null -> Text("无歌词", modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```
> `items(lines.size)` 用 `androidx.compose.foundation.lazy.items`(按 count 的重载)。如未解析到,确保 import 正确。

- [ ] **Step 6: 重写 MainActivity.kt(主题 + 抽屉 + Scaffold + mini 条 + 播放页覆盖层)**

Replace `android/app/src/main/java/com/tingmusic/MainActivity.kt` 全文:
```kotlin
package com.tingmusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.collectAsState
import com.tingmusic.playback.PlaybackController
import com.tingmusic.ui.DrawerContent
import com.tingmusic.ui.MiniPlayer
import com.tingmusic.ui.NowPlayingScreen
import com.tingmusic.ui.PlaylistScreen
import com.tingmusic.ui.SyncViewModel
import com.tingmusic.ui.theme.TingMusicTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: SyncViewModel by viewModels()
    private lateinit var playback: PlaybackController

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playback = PlaybackController(applicationContext)
        enableEdgeToEdge()
        setContent {
            val theme by vm.theme.collectAsStateWithLifecycle()
            TingMusicTheme(theme) {
                val ui by vm.state.collectAsStateWithLifecycle()
                val pstate by playback.state.collectAsState()
                var showNowPlaying by remember { mutableStateOf(false) }
                var livePos by remember { mutableLongStateOf(0L) }
                LaunchedEffect(pstate.isPlaying, pstate.currentId) {
                    while (true) { livePos = playback.currentPositionMs(); delay(300) }
                }
                LaunchedEffect(ui.tracks) { playback.setLibrary(ui.tracks) }

                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val current = ui.tracks.find { it.id == pstate.currentId }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = { DrawerContent(vm) },
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            TopAppBar(
                                title = { Text("TingMusic") },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Filled.Menu, "菜单")
                                    }
                                },
                            )
                        },
                        bottomBar = {
                            if (current != null) {
                                MiniPlayer(
                                    track = current, isPlaying = pstate.isPlaying,
                                    onToggle = { playback.togglePlayPause() },
                                    onOpen = { showNowPlaying = true },
                                )
                            }
                        },
                    ) { inner ->
                        PlaylistScreen(
                            tracks = ui.tracks, currentId = pstate.currentId,
                            onPlay = { playback.play(it); showNowPlaying = true },
                            modifier = Modifier.padding(inner),
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showNowPlaying && current != null,
                    enter = slideInVertically { it }, exit = slideOutVertically { it },
                ) {
                    current?.let { c ->
                        NowPlayingScreen(
                            track = c, state = pstate, livePositionMs = livePos,
                            onClose = { showNowPlaying = false },
                            onToggle = { playback.togglePlayPause() },
                            onNext = { playback.next() }, onPrev = { playback.prev() },
                            onSeek = { playback.seekTo(it) }, onCycleMode = { playback.cycleMode() },
                        )
                    }
                }
                BackHandler(enabled = showNowPlaying) { showNowPlaying = false }
            }
        }
    }

    override fun onStart() { super.onStart(); playback.connect() }
    override fun onStop() { super.onStop(); playback.release() }
}
```

- [ ] **Step 7: 编译 + 全部单测**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: 单测全过(32 + AppTheme 2 + CoverFetcher 6 = 40),BUILD SUCCESSFUL。修正 Compose import/API 不匹配直到通过(如 `TopAppBar` 需 `@OptIn(ExperimentalMaterial3Api::class)`,已加)。

- [ ] **Step 8: Commit**
```bash
git add -A android/app/src/main/java/com/tingmusic/ui android/app/src/main/java/com/tingmusic/MainActivity.kt
git commit -m "feat(android-v2): drawer + playlist + mini-player shell, slide-up now-playing"
```

---

### Task 4: 播放页升级 —— 模糊封面背景 + 旋转黑胶 + 唱臂

**Files:**
- Modify: `android/app/src/main/java/com/tingmusic/ui/NowPlayingScreen.kt`
- Create: `android/app/src/main/java/com/tingmusic/ui/Vinyl.kt`(唱片+唱臂组件)

**Interfaces:**
- Consumes: Task2 `CoverImage`/`rememberCover`(注:`rememberCover` 现为 private;本任务在 `CoverImage.kt` 把它改 `internal` 以供 Vinyl 取同一 ImageBitmap,或 Vinyl 自行内嵌+iTunes 取图)。
- Produces: `@Composable fun VinylDisc(track: Track, isPlaying: Boolean, sizeDp: Int)`(旋转黑胶 + 封面 + 唱臂)。

- [ ] **Step 1: 把 rememberCover 暴露为 internal**

修改 `android/app/src/main/java/com/tingmusic/ui/CoverImage.kt`:把 `private fun rememberCover` 改为 `internal fun rememberCover`(供 Vinyl 复用同一取图逻辑,避免重复 IO)。

- [ ] **Step 2: Vinyl.kt —— 旋转黑胶 + 唱臂**

Create `android/app/src/main/java/com/tingmusic/ui/Vinyl.kt`:
```kotlin
package com.tingmusic.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.tingmusic.library.Track

/** 旋转黑胶:深色胶圈 + 封面(或黑胶占位)+ 右上唱臂(播放落下/暂停抬起)。 */
@Composable
fun VinylDisc(track: Track, isPlaying: Boolean, sizeDp: Int = 260) {
    val cover = rememberCover(track)
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Restart), label = "a")
    val spin = if (isPlaying) Modifier.rotate(angle) else Modifier
    // 唱臂角度:播放 -2°(落到碟面),暂停 -22°(抬起)
    val armAngle by animateFloatAsState(if (isPlaying) -2f else -22f, label = "arm")

    Box(Modifier.size((sizeDp * 1.15f).dp), contentAlignment = Alignment.Center) {
        // 黑胶圈
        Canvas(Modifier.size(sizeDp.dp).then(spin)) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color(0xFF0A0A0A), radius = r, center = c)
            drawCircle(Color(0x14FFFFFF), radius = r * 0.92f, center = c, style = Stroke(r * 0.012f))
            drawCircle(Color(0x14FFFFFF), radius = r * 0.80f, center = c, style = Stroke(r * 0.012f))
        }
        // 封面(或占位红心)嵌在中间
        if (cover != null) {
            Image(cover, null, contentScale = ContentScale.Crop,
                modifier = Modifier.size((sizeDp * 0.6f).dp).clip(CircleShape).then(spin))
        } else {
            Canvas(Modifier.size((sizeDp * 0.6f).dp).then(spin)) {
                val r = size.minDimension / 2f; val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(Color(0xFFD33A31), radius = r * 0.5f, center = c)
                drawCircle(Color(0xFF0A0A0A), radius = r * 0.08f, center = c)
            }
        }
        // 唱臂:从右上角支点伸向碟心,绕支点旋转
        Canvas(Modifier.size((sizeDp * 1.15f).dp)) {
            val pivot = Offset(size.width * 0.82f, size.height * 0.12f)
            val len = size.minDimension * 0.42f
            rotate(degrees = armAngle, pivot = pivot) {
                drawCircle(Color(0xFF888888), radius = size.minDimension * 0.03f, center = pivot)
                drawLine(Color(0xFFB0B0B0), start = pivot,
                    end = Offset(pivot.x - len * 0.5f, pivot.y + len), strokeWidth = size.minDimension * 0.018f)
            }
        }
    }
}
```

- [ ] **Step 3: NowPlayingScreen 用 VinylDisc + 模糊封面背景**

修改 `android/app/src/main/java/com/tingmusic/ui/NowPlayingScreen.kt`:
1. 顶部 import 增加:
```kotlin
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
```
2. 把最外层 `Surface { Column { ... } }` 改成 `Box`,先铺一层模糊封面背景 + 暗色 scrim,再叠原来的 `Column` 内容;中央碟区把 `CoverImage(... vinylFrame=true)` 换成 `VinylDisc(track, state.isPlaying, sizeDp = 260)`。具体把 `NowPlayingScreen` 的 body 改为:
```kotlin
    var lyricsMode by remember { mutableStateOf(false) }
    val bg = rememberCover(track)  // internal,来自 CoverImage.kt
    Box(Modifier.fillMaxSize()) {
        if (bg != null) {
            Image(bg, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(28.dp))  // API<31 无模糊,靠下方 scrim
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "收起", tint = Color.White) }
            Box(Modifier.weight(1f).fillMaxWidth().clickable { lyricsMode = !lyricsMode }, contentAlignment = Alignment.Center) {
                if (!lyricsMode) VinylDisc(track = track, isPlaying = state.isPlaying, sizeDp = 260)
                else LyricsView(track = track, positionMs = livePositionMs, modifier = Modifier.fillMaxHeight())
            }
            Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
            val dur = if (state.durationMs > 0) state.durationMs else track.durationMs
            Slider(value = if (dur > 0) (livePositionMs.toFloat() / dur) else 0f, onValueChange = { onSeek((it * dur).toLong()) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${fmt(livePositionMs)} / ${fmt(dur)}", style = MaterialTheme.typography.bodySmall, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrev) { Icon(Icons.Filled.SkipPrevious, "上一首", tint = Color.White) }
                    IconButton(onClick = onToggle) { if (state.isPlaying) Icon(Icons.Filled.Pause, "暂停", tint = Color.White) else Icon(Icons.Filled.PlayArrow, "播放", tint = Color.White) }
                    IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, "下一首", tint = Color.White) }
                }
                TextButton(onClick = onCycleMode) { Text(modeLabel(state.mode), color = Color.White) }
            }
        }
    }
```
3. 增加 import `androidx.compose.foundation.background`。删除原 `Surface` 相关 import 若不再用。注意歌词态在模糊背景上,`LyricsView` 的非高亮行颜色 `onSurfaceVariant` 在暗背景上可能偏暗——本任务可接受(后续可调);高亮行用 primary(红)仍清晰。

- [ ] **Step 4: 编译 + 全部单测**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: 单测全过(40),BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**
```bash
git add android/app/src/main/java/com/tingmusic/ui/Vinyl.kt android/app/src/main/java/com/tingmusic/ui/NowPlayingScreen.kt android/app/src/main/java/com/tingmusic/ui/CoverImage.kt
git commit -m "feat(android-v2): now-playing blurred background + rotating vinyl + tonearm"
```

---

### Task 5: 真机端到端验证(控制者执行)

**Files:** 无(验证)。前置:镜像目录已有同步曲库(Plan2 D2 同步过则现成);手机已解锁。

- [ ] **Step 1: 装 + 启动**
```bash
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB wait-for-device
$ADB shell input keyevent KEYCODE_WAKEUP
$ADB install -r /Users/kongkongyzt/Sites/TingMusic/android/app/build/outputs/apk/debug/app-debug.apk
$ADB shell am start -n com.tingmusic/.MainActivity
$ADB exec-out screencap -p > /tmp/tm_v2_0.png
```
Expected: 列表主屏 + 顶部 ☰;有曲库则列出曲目(带封面);底部无 mini 条(还没播)。

- [ ] **Step 2: 抽屉 + 主题切换**
用 `adb shell input tap` 点左上 ☰(或从左边缘右滑 `input swipe 0 800 600 800`)→ 截图确认抽屉(主题/局域网同步/关于)。点"白红"→ 截图确认整体变白底红强调;点"默认灰"→ 变回。

- [ ] **Step 3: 点歌 → 播放页(唱片/唱臂/模糊背景)**
`uiautomator dump` 取某曲目行坐标 → tap → 截图。Expected:从底部滑上来的播放页,模糊封面背景 + 旋转黑胶(播放中)+ 唱臂落下;`dumpsys media_session | grep state=` 显示 state=3。暂停 → 截图确认唱臂抬起、碟停。

- [ ] **Step 4: 点碟 → 歌词**
点击碟区中央 → 截图确认切到滚动歌词(有 .lrc 的曲目当前行高亮);隔几秒再截,高亮行随播放推进;再点回唱片。

- [ ] **Step 5: mini 条 + 返回**
点 ⌄ 收起播放页 → 截图确认回到列表且底部出现 mini 条(当前曲);点 mini 条 → 再次进播放页;按系统返回键 → 收起播放页。

- [ ] **Step 6: iTunes 封面(需公网)**
对一首**无内嵌封面**的曲目(如某 .flac)进入播放页,等几秒 → 截图确认封面从黑胶占位变成 iTunes 拉到的真实封面(若 iTunes 无此冷门曲则维持黑胶,属正常)。复核缓存:`$ADB shell ls /sdcard/Android/data/com.tingmusic/files/../cache/covers 2>/dev/null || $ADB shell run-as com.tingmusic ls cache/covers`。

- [ ] **Step 7: 收尾**:无需 commit。失败查 `$ADB logcat -d -t 300 | grep -iE "tingmusic|AndroidRuntime|media3"`。

---

## Self-Review

**Spec 覆盖(§4–§13):**
- §4 外壳(抽屉+Scaffold+mini条+播放页覆盖层+BackHandler)→ T3 ✅
- §5 PlaylistScreen(列表/点行播放/高亮/空库引导)→ T3 ✅
- §6 MiniPlayer(条件显示/小封面/播放暂停/点开)→ T3 ✅
- §7 NowPlayingScreen(模糊背景+旋转碟+唱臂+控制+点碟⇄歌词)→ T3 基础 + T4 升级 ✅
- §8 CoverFetcher(iTunes/缓存/回退链)+ CoverImage → T2 ✅
- §9 DrawerContent(主题/同步/关于)→ T3 ✅
- §10 主题 + SettingsStore 持久化 → T1 ✅
- §11 数据流(controller.state / 轮询 position / tracks / themeFlow)→ T3 MainActivity ✅
- §12 错误处理(iTunes→黑胶、blur API<31→scrim、无歌词、空库)→ T2/T4/T3 ✅
- §13 测试(CoverFetcher 纯函数 JVM 单测 + AppTheme 单测 + 真机)→ T1/T2/T5 ✅
- §15 不做:圆方切换/队列管理/⋮菜单/重扫/Navigation-Compose —— 计划未涉及 ✅

**占位扫描:** 无 TBD;纯逻辑(T1 AppTheme、T2 CoverFetcher)给完整代码+测试;UI(T2 CoverImage、T3 各屏、T4 Vinyl/NowPlaying)给完整 Compose 代码。T3 Step5 NowPlayingScreen 给全量代码,T4 给针对性修改片段。T5 真机验证。

**类型一致性:** `AppTheme.fromStored`、`SettingsStore{theme,setTheme}`、`SyncViewModel.theme/setTheme`、`CoverFetcher{searchTerm,upscale,parseArtworkUrl,cacheKey,fetch}`、`CoverImage(track,isPlaying,modifier,sizeDp,vinylFrame)`、`rememberCover`(T2 private→T4 改 internal)、`PlaylistScreen(tracks,currentId,onPlay)`、`MiniPlayer(track,isPlaying,onToggle,onOpen)`、`DrawerContent(vm)`、`NowPlayingScreen(track,state,livePositionMs,onClose,onToggle,onNext,onPrev,onSeek,onCycleMode)`、`VinylDisc(track,isPlaying,sizeDp)`、`LyricsView(track,positionMs,modifier)` 在各任务间一致;复用既有 `PlaybackController`/`PlaybackState`/`SyncViewModel`/`Track`/`LrcParser`/`LyricsIndex` 签名与 Global Constraints 列出的一致。

**执行注记:**
- Material Icons(`Menu`/`KeyboardArrowDown`/`Pause`/`PlayArrow`/`SkipNext`/`SkipPrevious`)来自 `material-icons-extended`(v1 已加)。
- `TopAppBar`/`rememberDrawerState` 需 `@OptIn(ExperimentalMaterial3Api::class)`(已在 MainActivity 标注)。
- `rememberCover` 跨文件复用:T2 设 private,T4 Step1 改 internal——实现 T3 时若 NowPlayingScreen 基础版需要背景图可暂不用;背景图在 T4 引入。
- 真机 UI 驱动用 `uiautomator dump` 取坐标 + `adb shell input tap`;USB 掉线用 `wait-for-device` 兜。
