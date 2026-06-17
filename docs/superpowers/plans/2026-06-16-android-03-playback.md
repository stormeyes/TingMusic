# 安卓播放器 (Android Plan 3/3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让已同步的曲库在安卓上真正能播:Media3/ExoPlayer 后台播放 + 通知栏/锁屏控制 + 音频焦点;点列表项即播;三种播放模式;同步歌词滚动;内嵌封面 + 黑胶占位(播放时旋转)。

**Architecture:** 标准 Media3 模式——一个 `MediaSessionService`(`PlaybackService`)持有 `ExoPlayer` + `MediaSession`(系统据此给通知栏/锁屏);UI 通过 `MediaController` 连到会话,观察状态、发命令。曲库 `Track` → `MediaItem`(file URI + 元数据)。歌词复用 Plan 2 的 `LrcParser`,当前行用纯函数二分查找(TDD)。封面用 `MediaMetadataRetriever.embeddedPicture` 解码,无则 Compose 画黑胶。

**Tech Stack:** Media3 1.5.1(exoplayer + session)、Compose、Coroutines。复用 Plan 2 的 `LibraryIndexer`/`Track`/`LrcParser`/`SyncViewModel`。

**前置:** Plan 2 已交付(`android/`,曲库能同步并索引成 `List<Track>` 显示)。工具链:Gradle 9.4.1 / **AGP 9.2.0 内置 Kotlin(勿 apply kotlin.android)** / compileSdk 36 / minSdk 26 / JBR 21。

**构建/测试(从 `android/`):**
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```
真机:`ADB=~/Library/Android/sdk/platform-tools/adb`;命令前加 `$ADB wait-for-device`(USB 偶发掉线);手机有安全锁屏,真机验证前需用户手动解锁。

---

### Task 1: Media3 依赖 + 权限 + 服务声明

**Files:**
- Modify: `android/gradle/libs.versions.toml`
- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 版本目录**

`[versions]` 末尾加:
```toml
media3 = "1.5.1"
```
`[libraries]` 末尾加:
```toml
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }
```

- [ ] **Step 2: app 依赖**

`android/app/build.gradle.kts` 的 `dependencies { }` 里加(在 lifecycle-compose 那几行之后):
```kotlin
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
```

- [ ] **Step 3: Manifest 权限 + 服务**

在 `android/app/src/main/AndroidManifest.xml` 现有 `<uses-permission ... INTERNET>` 等之后加:
```xml
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```
在 `<application>` 内、`<activity>` 之后加服务声明:
```xml
        <service
            android:name=".playback.PlaybackService"
            android:exported="true"
            android:foregroundServiceType="mediaPlayback">
            <intent-filter>
                <action android:name="androidx.media3.session.MediaSessionService" />
            </intent-filter>
        </service>
```

- [ ] **Step 4: 编译确认**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL（会下载 media3）。注:此时还没有 `PlaybackService` 类,manifest 引用了 `.playback.PlaybackService` 但**编译期不校验 manifest 类是否存在**,assembleDebug 仍应成功;若打包阶段报缺类,先做 Task 3 再回来构建。

- [ ] **Step 5: Commit**
```bash
git add android/gradle/libs.versions.toml android/app/build.gradle.kts android/app/src/main/AndroidManifest.xml
git commit -m "build(android-playback): Media3 deps, FGS perms, MediaSessionService declaration"
```

---

### Task 2: 播放模式映射(纯逻辑,TDD)

**Files:**
- Create: `android/app/src/main/java/com/tingmusic/playback/PlayMode.kt`
- Create: `android/app/src/test/java/com/tingmusic/playback/PlayModeTest.kt`

- [ ] **Step 1: 写失败测试**

Create `android/app/src/test/java/com/tingmusic/playback/PlayModeTest.kt`:
```kotlin
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
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "com.tingmusic.playback.PlayModeTest"`
Expected: 编译失败(`PlayMode` 未定义)。

- [ ] **Step 3: 实现**

Create `android/app/src/main/java/com/tingmusic/playback/PlayMode.kt`:
```kotlin
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
```

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2。Expected: 4 个测试 PASS。

- [ ] **Step 5: Commit**
```bash
git add android/app/src/main/java/com/tingmusic/playback/PlayMode.kt android/app/src/test/java/com/tingmusic/playback/PlayModeTest.kt
git commit -m "feat(android-playback): PlayMode enum mapped to Media3 repeat/shuffle"
```

---

### Task 3: 活动歌词二分查找(纯逻辑,TDD)

**Files:**
- Create: `android/app/src/main/java/com/tingmusic/playback/LyricsIndex.kt`
- Create: `android/app/src/test/java/com/tingmusic/playback/LyricsIndexTest.kt`

- [ ] **Step 1: 写失败测试(对应 store.ts activeLyricIndex 语义)**

Create `android/app/src/test/java/com/tingmusic/playback/LyricsIndexTest.kt`:
```kotlin
package com.tingmusic.playback

import com.tingmusic.library.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsIndexTest {
    private val lines = listOf(
        LyricLine(0, "a"),
        LyricLine(1000, "b"),
        LyricLine(2000, "c"),
        LyricLine(5000, "d"),
    )

    @Test
    fun beforeFirstReturnsZero() {
        // 所有行 time<=pos 取最后一个;pos 在第一行之前时仍返回 0(对齐 Web 端)
        assertEquals(0, LyricsIndex.activeIndex(lines, -10))
        assertEquals(0, LyricsIndex.activeIndex(lines, 0))
    }

    @Test
    fun picksLastLineNotAfterPosition() {
        assertEquals(0, LyricsIndex.activeIndex(lines, 999))
        assertEquals(1, LyricsIndex.activeIndex(lines, 1000))
        assertEquals(1, LyricsIndex.activeIndex(lines, 1500))
        assertEquals(2, LyricsIndex.activeIndex(lines, 2000))
        assertEquals(3, LyricsIndex.activeIndex(lines, 9999))
    }

    @Test
    fun emptyReturnsMinusOne() {
        assertEquals(-1, LyricsIndex.activeIndex(emptyList(), 1000))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "com.tingmusic.playback.LyricsIndexTest"`
Expected: 编译失败(`LyricsIndex` 未定义)。

- [ ] **Step 3: 实现(移植 store.ts activeLyricIndex 的二分)**

Create `android/app/src/main/java/com/tingmusic/playback/LyricsIndex.kt`:
```kotlin
package com.tingmusic.playback

import com.tingmusic.library.LyricLine

object LyricsIndex {
    /** 返回 time_ms <= pos 的最后一行下标;空列表返回 -1;pos 在首行前返回 0。 */
    fun activeIndex(lines: List<LyricLine>, posMs: Long): Int {
        if (lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.size - 1
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= posMs) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2。Expected: 3 个测试 PASS。

- [ ] **Step 5: Commit**
```bash
git add android/app/src/main/java/com/tingmusic/playback/LyricsIndex.kt android/app/src/test/java/com/tingmusic/playback/LyricsIndexTest.kt
git commit -m "feat(android-playback): binary-search active lyric line"
```

---

### Task 4: PlaybackService（MediaSessionService + ExoPlayer）

**Files:**
- Create: `android/app/src/main/java/com/tingmusic/playback/PlaybackService.kt`

- [ ] **Step 1: 实现服务**

Create `android/app/src/main/java/com/tingmusic/playback/PlaybackService.kt`:
```kotlin
package com.tingmusic.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * 后台播放服务。持有一个 ExoPlayer + MediaSession;系统据此提供通知栏 / 锁屏 /
 * 蓝牙耳机控制。UI 侧用 MediaController 连到这个会话。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // 拔耳机暂停
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
```

- [ ] **Step 2: 编译确认**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL(manifest 引用的 `.playback.PlaybackService` 现在存在,打包通过)。

- [ ] **Step 3: Commit**
```bash
git add android/app/src/main/java/com/tingmusic/playback/PlaybackService.kt
git commit -m "feat(android-playback): MediaSessionService with ExoPlayer + audio focus"
```

---

### Task 5: PlaybackController（连接会话 + 状态/命令）

**Files:**
- Create: `android/app/src/main/java/com/tingmusic/playback/PlaybackController.kt`

- [ ] **Step 1: 实现**

Create `android/app/src/main/java/com/tingmusic/playback/PlaybackController.kt`:
```kotlin
package com.tingmusic.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.tingmusic.library.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI 观察的播放状态。currentId 为当前曲目 id(Track.id = 相对路径)。 */
data class PlaybackState(
    val currentId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val mode: PlayMode = PlayMode.SEQUENTIAL,
)

/**
 * 连接 PlaybackService 的 MediaController 封装。connect() 在 Activity onStart 调用,
 * release() 在 onStop。命令直接转发给 controller。播放时设置整个曲库为队列。
 */
class PlaybackController(private val context: Context) {

    private var controller: MediaController? = null
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** 当前曲库快照(用于 play(index) 与 id 映射)。 */
    private var tracks: List<Track> = emptyList()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            pushState(player)
        }
    }

    fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(listener)
            pushState(c)
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    fun setLibrary(list: List<Track>) { tracks = list }

    /** 用整个曲库做队列,从 track 处开始播。 */
    fun play(track: Track) {
        val c = controller ?: return
        val idx = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        val items = tracks.map { t ->
            MediaItem.Builder()
                .setMediaId(t.id)
                .setUri(Uri.fromFile(t.file))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.title)
                        .setArtist(t.artist)
                        .setAlbumTitle(t.album)
                        .build(),
                )
                .build()
        }
        c.setMediaItems(items, idx, 0)
        applyMode(c, _state.value.mode)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun prev() { controller?.seekToPreviousMediaItem() }
    fun seekTo(posMs: Long) { controller?.seekTo(posMs) }

    fun cycleMode() {
        val c = controller ?: return
        val nextMode = _state.value.mode.next()
        applyMode(c, nextMode)
        _state.value = _state.value.copy(mode = nextMode)
    }

    /** 供 UI 轮询当前进度(MediaController 的 position 只能主线程读)。 */
    fun currentPositionMs(): Long = controller?.currentPosition ?: 0L

    private fun applyMode(c: MediaController, mode: PlayMode) {
        c.repeatMode = mode.repeatMode
        c.shuffleModeEnabled = mode.shuffle
    }

    private fun pushState(player: Player) {
        _state.value = _state.value.copy(
            currentId = player.currentMediaItem?.mediaId,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
        )
    }
}
```
> 注:`MoreExecutors` 来自 guava,Media3 已传递依赖该库,直接 import 可用;若 import 不到,改用 `androidx.media3.common.util.Util` 的主线程 executor 或 `context.mainExecutor`(API 28+)。

- [ ] **Step 2: 编译确认**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**
```bash
git add android/app/src/main/java/com/tingmusic/playback/PlaybackController.kt
git commit -m "feat(android-playback): MediaController wrapper exposing state + commands"
```

---

### Task 6: 封面（内嵌 / 黑胶占位）+ UI 接线（点歌即播 + 播放控制）

**Files:**
- Create: `android/app/src/main/java/com/tingmusic/ui/CoverArt.kt`
- Create: `android/app/src/main/java/com/tingmusic/ui/NowPlaying.kt`
- Modify: `android/app/src/main/java/com/tingmusic/ui/SyncScreen.kt`(列表项可点 → 播放)
- Modify: `android/app/src/main/java/com/tingmusic/MainActivity.kt`(创建 PlaybackController,连接生命周期,组装界面)

- [ ] **Step 1: CoverArt —— 内嵌封面或旋转黑胶**

Create `android/app/src/main/java/com/tingmusic/ui/CoverArt.kt`:
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.io.File

/** 异步取内嵌封面(IO);无则返回 null。 */
@Composable
fun rememberEmbeddedCover(file: File?): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(initialValue = null, key1 = file?.absolutePath) {
        value = null
        val f = file ?: return@produceState
        value = runCatching {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(f.absolutePath)
                mmr.embeddedPicture?.let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            } finally {
                runCatching { mmr.release() }
            }
        }.getOrNull()
    }
    return bmp
}

@Composable
fun CoverArt(file: File?, isPlaying: Boolean, modifier: Modifier = Modifier, sizeDp: Int = 64) {
    val cover = rememberEmbeddedCover(file)
    // 播放时持续旋转(20s 一圈);暂停时停。
    val transition = rememberInfiniteTransition(label = "vinyl")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    val spin = if (isPlaying) Modifier.rotate(angle) else Modifier
    Box(modifier.size(sizeDp.dp).clip(CircleShape)) {
        if (cover != null) {
            Image(cover, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(sizeDp.dp).clip(CircleShape).then(spin))
        } else {
            Canvas(Modifier.size(sizeDp.dp).then(spin)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(Color(0xFF0A0A0A), radius = r, center = c)
                drawCircle(Color(0x14FFFFFF), radius = r * 0.8f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.02f))
                drawCircle(Color(0x14FFFFFF), radius = r * 0.6f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.02f))
                drawCircle(Color(0xFFD33A31), radius = r * 0.3f, center = c) // 红色中心标签
                drawCircle(Color(0xFF0A0A0A), radius = r * 0.05f, center = c) // 轴孔
            }
        }
    }
}
```

- [ ] **Step 2: NowPlaying 面板 + 歌词视图**

Create `android/app/src/main/java/com/tingmusic/ui/NowPlaying.kt`:
```kotlin
package com.tingmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import com.tingmusic.library.LrcParser
import com.tingmusic.library.Lyrics
import com.tingmusic.library.Track
import com.tingmusic.playback.LyricsIndex
import com.tingmusic.playback.PlayMode
import com.tingmusic.playback.PlaybackState

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

private fun modeLabel(m: PlayMode) = when (m) {
    PlayMode.SEQUENTIAL -> "顺序"
    PlayMode.RANDOM -> "随机"
    PlayMode.REPEAT_ONE -> "单曲"
}

@Composable
fun NowPlayingPanel(
    track: Track?,
    s: PlaybackState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (track == null) return
    Column(modifier.fillMaxWidth().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverArt(file = track.file, isPlaying = s.isPlaying, sizeDp = 56)
            Column(Modifier.padding(start = 12.dp).fillMaxWidth()) {
                Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val dur = if (s.durationMs > 0) s.durationMs else (track.durationMs)
        Slider(
            value = if (dur > 0) (s.positionMs.toFloat() / dur) else 0f,
            onValueChange = { onSeek((it * dur).toLong()) },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${fmt(s.positionMs)} / ${fmt(dur)}", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) { Icon(Icons.Filled.SkipPrevious, "上一首") }
                IconButton(onClick = onToggle) {
                    if (s.isPlaying) Icon(Icons.Filled.Pause, "暂停") else Icon(Icons.Filled.PlayArrow, "播放")
                }
                IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, "下一首") }
            }
            TextButton(onClick = onCycleMode) { Text(modeLabel(s.mode)) }
        }
    }
}

/** 同步歌词:解析当前曲目的 .lrc,按 position 高亮+居中。 */
@Composable
fun LyricsView(track: Track?, positionMs: Long, modifier: Modifier = Modifier) {
    val lrc = track?.lrcFile
    val lyrics = if (lrc != null && lrc.isFile) runCatching { LrcParser.parse(lrc.readText()) }.getOrNull() else null
    when (lyrics) {
        is Lyrics.Synced -> {
            val lines = lyrics.lines
            val active = LyricsIndex.activeIndex(lines, positionMs)
            val listState = rememberLazyListState()
            LaunchedEffect(active) {
                if (active >= 0) listState.animateScrollToItem(active.coerceAtLeast(0))
            }
            LazyColumn(state = listState, modifier = modifier.fillMaxWidth()) {
                items(lines.size) { i ->
                    Text(
                        lines[i].text.ifBlank { "♪" },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 16.dp),
                        color = if (i == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (i == active) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        is Lyrics.Plain -> Text(lyrics.text, modifier.padding(16.dp))
        null -> Text("无歌词", modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

- [ ] **Step 3: SyncScreen 列表项可点 → 播放;加 列表/歌词 切换**

修改 `android/app/src/main/java/com/tingmusic/ui/SyncScreen.kt`:把 `SyncScreen` 的签名加上播放相关回调与状态,并让列表项可点、加一个"列表/歌词"切换。具体:把函数签名改为
```kotlin
@Composable
fun SyncScreen(
    vm: SyncViewModel,
    playback: PlaybackState,
    onPlayTrack: (com.tingmusic.library.Track) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit,
    livePositionMs: Long,
    modifier: Modifier = Modifier,
)
```
在 `HorizontalDivider()` 之后、`曲库(...)` Text 与 `LazyColumn` 之间,插入 NowPlaying 面板 + 列表/歌词切换。当前曲目 = `s.tracks.find { it.id == playback.currentId }`。用一个本地 `var showLyrics by remember { mutableStateOf(false) }`(需 import `androidx.compose.runtime.*`),两个 `FilterChip`/`TextButton` 切换。`showLyrics` 为真时下方显示 `LyricsView(currentTrack, livePositionMs)`,否则显示曲库 `LazyColumn`,其中每个 item 包成可点:
```kotlin
            items(s.tracks, key = { it.id }) { t ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayTrack(t) }   // import androidx.compose.foundation.clickable
                        .padding(vertical = 6.dp),
                ) {
                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (t.id == playback.currentId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text("${t.artist}${if (t.lrcFile != null) "  · 有歌词" else ""}",
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
```
并在 `曲库(...)` 区上方插入:
```kotlin
        val currentTrack = s.tracks.find { it.id == playback.currentId }
        NowPlayingPanel(
            track = currentTrack, s = playback.copy(positionMs = livePositionMs),
            onToggle = onToggle, onNext = onNext, onPrev = onPrev, onSeek = onSeek, onCycleMode = onCycleMode,
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { showLyrics = false }) { Text(if (!showLyrics) "● 列表" else "列表") }
            TextButton(onClick = { showLyrics = true }) { Text(if (showLyrics) "● 歌词" else "歌词") }
        }
```
当 `showLyrics` 为真时,用 `LyricsView(currentTrack, livePositionMs, Modifier.fillMaxSize())` 取代曲库 LazyColumn。
保留原有的扫描/手填 IP/同步进度 UI 不变。

> 实现者:按上述意图把这些片段整合进现有 `SyncScreen` 的 Column 里,补齐 import(`clickable`、`remember`/`mutableStateOf`/`getValue`/`setValue`、`NowPlayingPanel`/`LyricsView`、`PlaybackState`)。保持现有同步功能可用。

- [ ] **Step 4: MainActivity 组装 —— 创建 PlaybackController、连接生命周期、轮询进度**

Replace `android/app/src/main/java/com/tingmusic/MainActivity.kt` 全文:
```kotlin
package com.tingmusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingmusic.playback.PlaybackController
import com.tingmusic.ui.SyncScreen
import com.tingmusic.ui.SyncViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: SyncViewModel by viewModels()
    private lateinit var playback: PlaybackController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playback = PlaybackController(applicationContext)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val ui by vm.state.collectAsStateWithLifecycle()
                val pstate by playback.state.collectAsState()
                // 每 300ms 拉一次真实进度(MediaController.position 只能主线程读)
                var livePos by remember { mutableLongStateOf(0L) }
                LaunchedEffect(pstate.isPlaying, pstate.currentId) {
                    while (true) { livePos = playback.currentPositionMs(); delay(300) }
                }
                // 曲库变化时同步给 controller(用于 play 队列)
                LaunchedEffect(ui.tracks) { playback.setLibrary(ui.tracks) }
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    SyncScreen(
                        vm = vm,
                        playback = pstate,
                        onPlayTrack = { playback.play(it) },
                        onToggle = { playback.togglePlayPause() },
                        onNext = { playback.next() },
                        onPrev = { playback.prev() },
                        onSeek = { playback.seekTo(it) },
                        onCycleMode = { playback.cycleMode() },
                        livePositionMs = livePos,
                        modifier = Modifier.padding(inner),
                    )
                }
            }
        }
    }

    override fun onStart() { super.onStart(); playback.connect() }
    override fun onStop() { super.onStop(); playback.release() }
}
```

- [ ] **Step 5: 编译 + 全部单测**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: 单测全过(此前 25 + PlayMode 4 + LyricsIndex 3 = 32),BUILD SUCCESSFUL。修正 Compose import/API 不匹配直到编译通过。

- [ ] **Step 6: Commit**
```bash
git add android/app/src/main/java/com/tingmusic/ui/CoverArt.kt android/app/src/main/java/com/tingmusic/ui/NowPlaying.kt android/app/src/main/java/com/tingmusic/ui/SyncScreen.kt android/app/src/main/java/com/tingmusic/MainActivity.kt
git commit -m "feat(android-playback): now-playing panel, lyrics view, cover, tap-to-play"
```

---

### Task 7: 真机端到端验证（控制者执行）

**Files:** 无(验证任务)。需要镜像目录里已有同步好的曲库(Plan 2 已同步过则现成);否则先同步一次。

- [ ] **Step 1: 装 + 启动**
```bash
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB wait-for-device
$ADB shell input keyevent KEYCODE_WAKEUP   # 需用户已手动解锁安全锁屏
$ADB install -r /Users/kongkongyzt/Sites/TingMusic/android/app/build/outputs/apk/debug/app-debug.apk
$ADB shell am start -n com.tingmusic/.MainActivity
```

- [ ] **Step 2: 点一首歌播放,验证状态推进**
用 `adb shell input tap` 点曲库里某一项(先 screencap 取坐标)。然后:
```bash
$ADB shell dumpsys media_session 2>/dev/null | grep -iE "TingMusic|state=PLAYING|package=com.tingmusic" | head
$ADB shell screencap -p /sdcard/p1.png; $ADB pull /sdcard/p1.png /tmp/tm_play1.png
```
Expected: now-playing 面板出现(封面/标题/进度),播放图标变暂停;`dumpsys media_session` 显示 com.tingmusic 的活动会话且 state=PLAYING。隔 1-2 秒再截一张,进度 `m:ss / m:ss` 在走动。

- [ ] **Step 3: 通知栏 / 锁屏控制**
```bash
$ADB shell cmd statusbar expand-notifications
$ADB exec-out screencap -p > /tmp/tm_notif.png
$ADB shell cmd statusbar collapse
```
Expected: 通知栏出现媒体通知(标题/艺术家 + 播放控制)。

- [ ] **Step 4: 歌词 / 模式 / 上下首**
- 点"歌词"切换,确认有 `.lrc` 的曲目歌词随播放高亮滚动(隔几秒两张截图对比高亮行变化)。
- 点模式按钮(顺序→随机→单曲循环)文字轮换。
- 点下一首/上一首,now-playing 标题切换。

- [ ] **Step 5: 后台播放**
```bash
$ADB shell input keyevent KEYCODE_HOME      # 回桌面
$ADB shell dumpsys media_session 2>/dev/null | grep -iE "state=PLAYING|com.tingmusic" | head
```
Expected: 回到桌面后仍 state=PLAYING(后台播放 + 前台服务存活)。

- [ ] **Step 6: 收尾**:无需 commit(纯验证)。失败查 `$ADB logcat -d -t 300 | grep -iE "tingmusic|media3|ExoPlayer|AndroidRuntime"`。

---

## Self-Review

**Spec 覆盖(§5.2 播放 / §5.3 歌词 / §5.4 封面 / §11):**
- §5.2 Media3 MediaSessionService + ExoPlayer + 音频焦点 + 拔耳机暂停 → T4 ✅;通知栏/锁屏(MediaSession 自动)→ T1 权限+服务声明 / T4 会话 ✅;三模式映射 → T2 ✅;file:// 播放 + 队列 → T5 ✅
- §5.3 歌词:.lrc sidecar(复用 Plan2 LrcParser)、轮询 position、二分查找、居中滚动 → T3 + T6 LyricsView ✅
- §5.4 封面:内嵌(MediaMetadataRetriever.embeddedPicture)、黑胶占位(红心 #d33a31)、播放时旋转 → T6 CoverArt ✅
- 点列表项即播(安卓交互,丢弃 Mac 的"点封面才播"语义)→ T5/T6 ✅
- §11 v1 不做:iTunes 拉封面 / 主题切换 / 圆方切换 —— 本计划未做 ✅

**占位扫描:** 无 TBD;纯逻辑(T2/T3)给完整代码+测试;服务/控制器/封面(T4/T5/T6)给完整代码;T6 Step3 对 SyncScreen 的改造以"意图+关键片段"描述并标注实现者需补齐 import——因为它是对现有大文件的插入式修改,给完整重写反而易错位。T7 为真机验证。

**类型一致性:** `PlayMode{repeatMode,shuffle,next()}`、`LyricsIndex.activeIndex(List<LyricLine>,Long)`、`PlaybackState{currentId,isPlaying,positionMs,durationMs,mode}`、`PlaybackController{connect/release/setLibrary/play/togglePlayPause/next/prev/seekTo/cycleMode/currentPositionMs/state}`、`Track{id,file,title,artist,album,durationMs,lrcFile}`(Plan2)、`LyricLine{timeMs,text}`/`Lyrics.Synced/Plain`(Plan2)、`LrcParser.parse`(Plan2)在各处一致。`SyncScreen` 新签名与 `MainActivity` 调用处一致。

**执行注记:**
- 所有 `./gradlew` 命令需 `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`。
- AGP 9 内置 Kotlin:勿 apply `kotlin.android`。
- Material Icons:`Icons.Filled.PlayArrow/Pause/SkipNext/SkipPrevious` 来自 `androidx.compose.material:material-icons-core`(随 compose BOM,通常已在 material3 依赖图内;若 import 不到,在 app deps 加 `implementation("androidx.compose.material:material-icons-extended")` 或改用文字按钮)。
- media3 1.5.1 若与 compileSdk 36 / AGP 9.2 解析冲突,升到能解析的最近 1.x;`MoreExecutors`(guava)若取不到改用 `context.mainExecutor`。
- 真机验证需用户先手动解锁(安全锁屏);USB 命令前 `$ADB wait-for-device`。
