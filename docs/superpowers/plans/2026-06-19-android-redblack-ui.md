# Android 红黑 UI 重做 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把安卓 UI 换成用户设计的红黑音乐播放器:单一红黑主题、三屏滑动导航(列表→播放器→歌词)+ 左抽屉、凹槽黑胶 + 精致唱臂、大红播放键、抽屉里真实的局域网同步。功能层(播放/同步/曲库/歌词)不变。

**Architecture:** 复用 `playback/*`、`sync/*`、`library/*`;只重做 `ui/*` 与 `MainActivity`。单一红黑 `ColorScheme`;三屏用 `AnimatedVisibility` slide-up 叠层 + 左 `ModalNavigationDrawer`。自定义绘制(黑胶凹槽/唱臂/均衡器/conic 进度环)用 Compose `Canvas`/`drawscope`。

**Tech Stack:** Kotlin 2.2.10、Compose(BOM 2026.02.01,Material3)、Media3(已在)。无新依赖。

## Global Constraints

- 每个 `./gradlew` 命令先 `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`,在 `android/` 运行。
- AGP 9.2.0 **内置 Kotlin**:**不要** apply `org.jetbrains.kotlin.android`。
- 不引入新第三方依赖;不运行任何 linter;只用 `./gradlew :app:testDebugUnitTest` / `:app:assembleDebug` 验证。
- compileSdk 36 / minSdk 26;`Modifier.blur` 仅 API31+ 生效,API<31 靠暗罩退化。
- **设计 token / 尺寸 / 几何以 spec 为准**:`docs/superpowers/specs/2026-06-19-android-redblack-design.md`。关键值:底 `#0a0a0a`、mini `#0d0d0d`、抽屉 `#0f0f0f`、搜索框 `#161616`;主文字 `#F5F5F5`、次 `白@0.40`、弱 `白@0.28`;强调红 `#E60026`;分隔 `白@0.06`。
- 真机:`ADB=~/Library/Android/sdk/platform-tools/adb`,命令前 `$ADB wait-for-device`;手机有安全锁屏,验证前需用户手动解锁;左边缘滑会被 HyperOS 当返回,开抽屉点 ☰ 或点列表项触发。
- 复用类型(签名不变):`library.Track(id,file,title,artist,album,durationMs,lrcFile)`、`library.LrcParser.parse(String):Lyrics`、`library.Lyrics.Synced(lines)/Plain(text)`、`library.LyricLine(timeMs,text)`、`playback.PlaybackController{ state:StateFlow<PlaybackState>, connect/release/setLibrary/play(Track)/togglePlayPause/next/prev/seekTo(Long)/cycleMode/currentPositionMs() }`、`playback.PlaybackState(currentId,isPlaying,positionMs,durationMs,mode)`、`playback.PlayMode{SEQUENTIAL,RANDOM,REPEAT_ONE; next()}`、`playback.LyricsIndex.activeIndex(lines,posMs)`、`ui.SyncViewModel{ state:StateFlow<UiState>, startDiscovery(), setManualHost(String), sync(baseUrl), manualBaseUrl() }`、`ui.UiState(servers,manualHost,progress,tracks,syncing)`、`sync.DiscoveredServer(name,host,port,baseUrl)`、`sync.SyncProgress`、`ui.CoverImage(track,isPlaying,modifier,sizeDp,vinylFrame)` 与 `internal fun ui.rememberCover(track): ImageBitmap?`。

---

### Task 1: 红黑主题地基 + 移除双主题机制 + 共享小组件

**Files:**
- Rewrite: `android/app/src/main/java/com/tingmusic/ui/theme/Theme.kt`
- Delete: `android/app/src/main/java/com/tingmusic/data/SettingsStore.kt`、`android/app/src/test/java/com/tingmusic/ui/theme/AppThemeTest.kt`
- Modify: `android/app/src/main/java/com/tingmusic/ui/SyncViewModel.kt`(删 `settings`/`theme`/`setTheme`)
- Create: `android/app/src/main/java/com/tingmusic/ui/RedBlackPieces.kt`

**Interfaces:**
- Produces: `object RB { val Bg, MiniBg, DrawerBg, SearchBg, Text, TextDim, TextWeak, Red, Divider: Color }`;`@Composable fun TingMusicTheme(content: @Composable () -> Unit)`;`@Composable fun EqualizerBars(modifier)`;`@Composable fun ConicPlayButton(progress: Float, isPlaying: Boolean, sizeDp: Int, onClick: ()->Unit)`;`fun DrawScope.drawPlayTriangle(color)`(或一个 `@Composable fun RedCirclePlay(sizeDp)`)。

- [ ] **Step 1: 重写 Theme.kt(单一红黑 + token)**

Replace `android/app/src/main/java/com/tingmusic/ui/theme/Theme.kt` 全文:
```kotlin
package com.tingmusic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 红黑设计 token(集中,供所有 UI 引用,不走 MaterialTheme 也能直接用)。 */
object RB {
    val Bg = Color(0xFF0A0A0A)
    val MiniBg = Color(0xFF0D0D0D)
    val DrawerBg = Color(0xFF0F0F0F)
    val SearchBg = Color(0xFF161616)
    val Text = Color(0xFFF5F5F5)
    val TextDim = Color(0x66FFFFFF)   // ~40%
    val TextWeak = Color(0x47FFFFFF)  // ~28%
    val Red = Color(0xFFE60026)
    val Divider = Color(0x0FFFFFFF)   // ~6%
}

private val RedBlackScheme = darkColorScheme(
    primary = RB.Red,
    onPrimary = Color.White,
    background = RB.Bg,
    surface = RB.MiniBg,
    onBackground = RB.Text,
    onSurface = RB.Text,
    onSurfaceVariant = RB.TextDim,
)

@Composable
fun TingMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RedBlackScheme, content = content)
}
```

- [ ] **Step 2: 删除主题机制文件**
```bash
git rm android/app/src/main/java/com/tingmusic/data/SettingsStore.kt android/app/src/test/java/com/tingmusic/ui/theme/AppThemeTest.kt
```

- [ ] **Step 3: 从 SyncViewModel 删主题相关三行**

在 `android/app/src/main/java/com/tingmusic/ui/SyncViewModel.kt` 删除这三行(Task v2-T1 加的):
```kotlin
    private val settings = com.tingmusic.data.SettingsStore(app)
    val theme: kotlinx.coroutines.flow.StateFlow<com.tingmusic.ui.theme.AppTheme> = settings.theme
    fun setTheme(t: com.tingmusic.ui.theme.AppTheme) = settings.setTheme(t)
```
(其余 SyncViewModel 不动。)

- [ ] **Step 4: 创建 RedBlackPieces.kt(均衡器 / conic 进度按钮 / 红圈播放)**

Create `android/app/src/main/java/com/tingmusic/ui/RedBlackPieces.kt`:
```kotlin
package com.tingmusic.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tingmusic.ui.theme.RB

/** 红色脉动均衡器(3 根),标当前播放曲。 */
@Composable
fun EqualizerBars(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val phases = listOf(0, 300, 600)
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        phases.forEachIndexed { i, delayMs ->
            val h by t.animateFloat(
                4f, 14f,
                infiniteRepeatable(tween(900, delayMillis = delayMs), RepeatMode.Reverse),
                label = "b$i",
            )
            if (i > 0) Spacer(Modifier.width(2.dp))
            Box(Modifier.width(2.dp).height(h.dp).clip(CircleShape).let { it }) {
                Canvas(Modifier.size(2.dp, h.dp)) { drawRect(RB.Red) }
            }
        }
    }
}

/** mini 条播放/暂停:外圈 conic 进度环 + 内圆 + 图标。 */
@Composable
fun ConicPlayButton(progress: Float, isPlaying: Boolean, onClick: () -> Unit, sizeDp: Int = 38) {
    val sweep = 360f * progress.coerceIn(0f, 1f)
    Box(
        Modifier.size(sizeDp.dp).clip(CircleShape).clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(sizeDp.dp)) {
            drawArc(Color(0x21FFFFFF), 0f, 360f, useCenter = false,
                style = Stroke(width = size.minDimension * 0.11f))
            drawArc(RB.Red, -90f, sweep, useCenter = false,
                style = Stroke(width = size.minDimension * 0.11f))
        }
        Box(Modifier.size((sizeDp * 0.79f).dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size((sizeDp * 0.79f).dp)) { drawCircle(RB.MiniBg) }
            if (isPlaying) Icon(Icons.Filled.Pause, "暂停", tint = RB.Text, modifier = Modifier.size(16.dp))
            else Icon(Icons.Filled.PlayArrow, "播放", tint = RB.Text, modifier = Modifier.size(16.dp))
        }
    }
}

/** 列表"播放全部"用的红色描边圈 + 实心三角。 */
@Composable
fun RedCirclePlay(sizeDp: Int = 24) {
    Canvas(Modifier.size(sizeDp.dp)) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(RB.Red, radius = r - 1f, center = c, style = Stroke(width = 1.5f * density))
        // 实心三角(指向右)
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x - r * 0.18f, c.y - r * 0.34f)
            lineTo(c.x + r * 0.42f, c.y)
            lineTo(c.x - r * 0.18f, c.y + r * 0.34f)
            close()
        }
        drawPath(p, RB.Red)
    }
}
```
并在文件内加一个无涟漪点击辅助:
```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this // 占位:在 composable 内用下方版本
```
> 实现者:`clickableNoRipple` 用一个 `@Composable Modifier` 扩展更稳;若简单起见,直接在 `ConicPlayButton` 的 Box 上用普通 `Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }` 替换 `clickableNoRipple(onClick)`,并删掉占位扩展。保证编译通过即可。

- [ ] **Step 5: 编译 + 跑剩余单测**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug`
Expected: **此步会编译失败**——`MainActivity`/`DrawerContent` 仍引用 `vm.theme`/`TingMusicTheme(theme)`/`AppTheme`。这是预期的;它们在 Task 2/4 改。**先只确认本任务新增/改的文件无语法错**:`./gradlew :app:compileDebugKotlin` 也会因下游引用失败。因此本任务的验证放宽为:`Theme.kt`/`RedBlackPieces.kt`/`SyncViewModel.kt` 改动本身正确,整体编译留到 Task 4 接线完成后绿。**提交本任务,不要求此刻 assembleDebug 通过。**

> 实现者:如果希望每步可编译,可把 Task 2/4 对 `MainActivity`/`DrawerContent` 的最小适配(去掉 `theme` 参数与主题开关)也并入本任务以保持绿;但按计划拆分时,接受 Task1–3 期间整体不编译,Task 4 收口。

- [ ] **Step 6: Commit**
```bash
git add -A android/app/src/main/java/com/tingmusic/ui/theme/Theme.kt android/app/src/main/java/com/tingmusic/ui/RedBlackPieces.kt android/app/src/main/java/com/tingmusic/ui/SyncViewModel.kt
git commit -m "feat(redblack): single red-black theme + shared pieces; drop theme switcher machinery"
```

---

### Task 2: 歌曲列表页 + 迷你播放条

**Files:**
- Rewrite: `android/app/src/main/java/com/tingmusic/ui/PlaylistScreen.kt`
- Rewrite: `android/app/src/main/java/com/tingmusic/ui/MiniPlayer.kt`

**Interfaces:**
- Consumes: Task1 `RB`/`EqualizerBars`/`ConicPlayButton`/`RedCirclePlay`;`CoverImage`、`Track`、`PlaybackState`。
- Produces: `PlaylistScreen(tracks, currentId, isPlaying, onPlayTrack, onPlayAll, onOpenDrawer, modifier)`;`MiniPlayer(track, isPlaying, progress: Float, onToggle, onOpen)`。

- [ ] **Step 1: 重写 PlaylistScreen.kt**

按 spec §5 实现。`Column(Modifier.fillMaxSize().background(RB.Bg))`:
1. 顶栏 `Row`(padding 6/8,h~46):☰ `IconButton`(`Icons.Filled.Menu`,tint `RB.Text`,`onClick=onOpenDrawer`)+ 搜索框 `Row(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(18.dp)).background(RB.SearchBg).padding(horizontal=13.dp))`(放大镜 `Icons.Filled.Search` 15dp tint `RB.TextDim` + Text "搜索歌曲、歌手、专辑" 13sp `RB.TextDim`,装饰不可点)+ ⋮ `Icon(Icons.Filled.MoreVert)` tint `RB.Text`(装饰)。
2. 标题块 `Column(padding 10/18/6)`:Text "我的曲库" 24sp Bold `RB.Text`;Text "本地曲库 · ${tracks.size} 首" 12sp `RB.TextDim`。
3. 播放全部 `Row(padding 8/16/12)` 底部 `Divider(color=RB.Divider)`:可点 `Row{ RedCirclePlay(24); Spacer(9); Text("播放全部",15,Medium,RB.Text); Spacer(6); Text("(${tracks.size})",13,RB.TextWeak) }`(整体 `clickable { onPlayAll() }`)+ `Spacer(weight 1)` + 排序 `Icon`(装饰,`Icons.AutoMirrored.Filled.Sort` 或 `FormatListBulleted`,tint `RB.TextDim`)。
4. 列表 `LazyColumn(Modifier.weight(1f))`:`items(tracks, key={it.id})`:`Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable{onPlayTrack(t)}.padding(10.dp,16.dp), verticalAlignment=CenterVertically)`:
   - 左 `Box(Modifier.width(22.dp))`:`if (t.id==currentId && isPlaying) EqualizerBars() else Text("%02d".format(index+1),14,RB.TextWeak)`(用 `itemsIndexed` 拿 index)。
   - 中 `Column(Modifier.weight(1f).padding(start=13.dp))`:Text 标题 15sp(`if t.id==currentId RB.Red else Color(0xFFF3F3F3)`,maxLines1 Ellipsis)+ Text "${t.artist} · ${t.album}" 12sp `RB.TextDim` maxLines1 Ellipsis。
   - 右 ⋮ `Icon(Icons.Filled.MoreVert)` tint `RB.TextWeak`(装饰)。
5. 空库:`Column` 居中文案 "曲库是空的 / 打开左上角抽屉,扫描局域网内的 Mac 同步曲库。"(`RB.Text` / `RB.TextDim`)。

> 用 `itemsIndexed(tracks, key={_,it->it.id})` 以拿到序号。所有颜色用 `RB.*`。

- [ ] **Step 2: 重写 MiniPlayer.kt**

按 spec §6:`Row(Modifier.fillMaxWidth().background(RB.MiniBg).padding(8.dp,12.dp), verticalAlignment=CenterVertically)` 顶部加一条 `Divider(color=RB.Divider)`(放在调用处或内部):
- `Box(Modifier.size(42.dp).clip(RoundedCornerShape(7.dp)).clickable{onOpen()})`:`CoverImage(track=track, isPlaying=false, sizeDp=42)`(方形封面:`CoverImage` 默认圆形,这里用 `RoundedCornerShape(7)` 外裁即可——给 `CoverImage` 包一层 `Box(clip(RoundedCornerShape(7)))`,内部圆形裁剪不影响,封面仍显示;若想方角,接受 CoverImage 的圆形裁剪在 42dp 上的观感)。
- `Column(Modifier.weight(1f).padding(horizontal=12.dp).clickable{onOpen()})`:Text 标题 13.5sp Medium `RB.Text` maxLines1 Ellipsis + Text 艺术家 11sp `RB.TextDim` maxLines1 Ellipsis。
- `ConicPlayButton(progress=progress, isPlaying=isPlaying, onClick=onToggle, sizeDp=38)`。
- 队列 `Icon`(装饰,`Icons.AutoMirrored.Filled.QueueMusic` 或 `PlaylistPlay`,tint `RB.TextDim`,size 21)。

- [ ] **Step 3: 局部编译检查(本任务文件语法)**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug` —— 仍会因 MainActivity 未接线失败(预期)。确认报错只来自 `MainActivity.kt`/`DrawerContent.kt`/`NowPlayingScreen.kt`(未改),而非本任务两文件的语法错。若本任务文件有 import/类型错,修到它们自身无错。

- [ ] **Step 4: Commit**
```bash
git add android/app/src/main/java/com/tingmusic/ui/PlaylistScreen.kt android/app/src/main/java/com/tingmusic/ui/MiniPlayer.kt
git commit -m "feat(redblack): song list (equalizer + play-all) and mini player (conic ring)"
```

---

### Task 3: 播放器页 + 黑胶/唱臂

**Files:**
- Create: `android/app/src/main/java/com/tingmusic/ui/PlayerScreen.kt`
- Rewrite: `android/app/src/main/java/com/tingmusic/ui/Vinyl.kt`

**Interfaces:**
- Consumes: Task1 `RB`、`rememberCover`、`CoverImage`、`PlaybackState`、`PlayMode`。
- Produces: `VinylDisc(cover: ImageBitmap?, isPlaying: Boolean, sizeDp: Int)`(凹槽碟 + 精致唱臂);`PlayerScreen(track, state, livePositionMs, onClose, onOpenLyrics, onToggle, onNext, onPrev, onSeek, onCycleMode)`。

- [ ] **Step 1: 重写 Vinyl.kt(凹槽碟 + 精致唱臂,改为接收 cover 参数)**

Replace `android/app/src/main/java/com/tingmusic/ui/Vinyl.kt` 全文:
```kotlin
package com.tingmusic.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.tingmusic.ui.theme.RB

/**
 * 凹槽黑胶 + 精致唱臂。播放时 18s/圈,暂停冻结当前角度;唱臂播放 26° / 暂停 10°。
 * cover 由调用方一次性 rememberCover 后传入(避免重复 IO)。
 */
@Composable
fun VinylDisc(cover: ImageBitmap?, isPlaying: Boolean, sizeDp: Int = 250) {
    var angle by remember { mutableStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            angle = (angle + (now - last) / 1_000_000_000f / 18f * 360f) % 360f
            last = now
        }
    }
    val spin = Modifier.graphicsLayer { rotationZ = angle }
    val armAngle by animateFloatAsState(if (isPlaying) 26f else 10f, label = "arm")

    // 容器留出唱臂空间
    Box(Modifier.size((sizeDp * 1.2f).dp), contentAlignment = Alignment.Center) {
        // 凹槽碟体
        Canvas(Modifier.size(sizeDp.dp).then(spin)) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color(0xFF101010), radius = r, center = c)
            // 同心凹槽(深浅交替)
            var rr = r * 0.95f
            var i = 0
            while (rr > r * 0.46f) {
                drawCircle(if (i % 2 == 0) Color(0xFF191919) else Color(0xFF0C0C0C),
                    radius = rr, center = c, style = Stroke(width = r * 0.012f))
                rr -= r * 0.035f; i++
            }
        }
        // 中心封面 106/250 比例
        val coverDp = (sizeDp * 0.424f)
        Box(Modifier.size(coverDp.dp).clip(CircleShape).then(spin), contentAlignment = Alignment.Center) {
            if (cover != null) {
                Image(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.size(coverDp.dp))
            } else {
                Canvas(Modifier.size(coverDp.dp)) {
                    val r = size.minDimension / 2f; val c = Offset(size.width/2f, size.height/2f)
                    drawCircle(Color(0xFF1A1A1A), r, c)
                    drawCircle(RB.Red, r * 0.5f, c)
                }
            }
        }
        // 中心轴孔
        Canvas(Modifier.size(11.dp)) {
            drawCircle(RB.Bg, size.minDimension/2f, Offset(size.width/2f, size.height/2f))
            drawCircle(Color(0x40FFFFFF), size.minDimension/2f, Offset(size.width/2f, size.height/2f), style = Stroke(1f))
        }
        // 唱臂:支点在容器右上,绕支点旋转
        Canvas(Modifier.size((sizeDp * 1.2f).dp)) {
            val pivot = Offset(size.width * 0.84f, size.height * 0.10f)
            val unit = size.minDimension / 250f  // 把设计的 px 换算到当前尺寸
            rotate(degrees = armAngle, pivot = pivot) {
                // 臂杆
                drawLine(Color(0xFF7A7A7A), pivot, Offset(pivot.x, pivot.y + 118f * unit), strokeWidth = 4f * unit)
                // 转轴底座
                drawCircle(Color(0xFF222222), 13f * unit, pivot)
                drawCircle(Color(0x1FFFFFFF), 13f * unit, pivot, style = Stroke(1f))
                drawCircle(RB.Red, 5f * unit, pivot)  // 红点
                // 唱头
                val head = Offset(pivot.x, pivot.y + 126f * unit)
                drawCircle(Color(0xFF222222), 10f * unit, head)
                drawCircle(RB.Red, 2.5f * unit, Offset(head.x, head.y + 12f * unit)) // 唱针红尖
            }
        }
    }
}
```
> 唱臂几何按设计近似(支点右上、播放更斜落向碟心)。实现者按观感微调 `pivot`/角度,目标是"播放时唱头落在碟面、暂停时抬起"。

- [ ] **Step 2: 创建 PlayerScreen.kt**

按 spec §7。`Column(Modifier.fillMaxSize().background(RB.Bg))`:
1. 顶栏 `Row`(h50):⌄ `IconButton(onClick=onClose)`(`Icons.Filled.KeyboardArrowDown`,tint `RB.Text`)+ `Column(weight1, CenterHorizontally)`{ 标题14 Medium `RB.Text` + 艺术家11 `RB.TextDim` } + ⋮ `Icon(MoreVert)` `RB.Text`(装饰)。
2. 黑胶区 `Box(Modifier.weight(1f).fillMaxWidth().clickable{onOpenLyrics()}, contentAlignment=Center)`:`val cover = rememberCover(track); VinylDisc(cover, state.isPlaying, 250)`。
3. 信息+控制 `Column(padding 0/28/22)`:
   - 标题 23sp SemiBold `RB.Text` + 艺术家 13.5sp `白@0.5`,居中。
   - 进度 `Row`(top 28):elapsed 11sp `RB.TextDim` + 自定义进度条 + duration。进度条用 `Slider` 或自绘:推荐 `Slider(value=pos/dur, onValueChange={onSeek((it*dur).toLong())}, colors=SliderDefaults.colors(thumbColor=RB.Red, activeTrackColor=RB.Red, inactiveTrackColor=Color(0x24FFFFFF)))`。`dur = if(state.durationMs>0) state.durationMs else track.durationMs`,`pos=livePositionMs`。
   - 传输控制 `Row(SpaceBetween, top 20)`:左 expand 图标(装饰,`Icons.Filled.OpenInFull` 或 `Fullscreen`,tint `白@0.65`)、prev `IconButton(onPrev)`(`Icons.Filled.SkipPrevious` 30 `RB.Text`)、**大红键** `Box(Modifier.size(68.dp).clip(CircleShape).background(RB.Red).clickable{onToggle()}, center)`{ 播放/暂停白图标 26 }、next `IconButton(onNext)`(`SkipNext` 30)、右 repeat 图标 `IconButton(onCycleMode)`(`Icons.Filled.Repeat`,tint `if(state.mode!=PlayMode.SEQUENTIAL) RB.Red else 白@0.65`;`PlayMode.REPEAT_ONE` 可用 `RepeatOne` 图标,`RANDOM` 用 `Shuffle`)。

- [ ] **Step 3: 局部编译检查**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug`(仍因 MainActivity 失败,预期)。确认 `Vinyl.kt`/`PlayerScreen.kt` 自身无语法/类型错。

- [ ] **Step 4: Commit**
```bash
git add android/app/src/main/java/com/tingmusic/ui/PlayerScreen.kt android/app/src/main/java/com/tingmusic/ui/Vinyl.kt
git commit -m "feat(redblack): player screen with grooved vinyl + detailed tonearm + big red button"
```

---

### Task 4: 歌词页 + 抽屉 + 三屏导航(收口编译)

**Files:**
- Create: `android/app/src/main/java/com/tingmusic/ui/LyricsScreen.kt`
- Rewrite: `android/app/src/main/java/com/tingmusic/ui/DrawerContent.kt`
- Rewrite: `android/app/src/main/java/com/tingmusic/MainActivity.kt`
- Delete: `android/app/src/main/java/com/tingmusic/ui/NowPlayingScreen.kt`

**Interfaces:**
- Consumes: Task1–3 全部;`SyncViewModel`、`LrcParser`、`LyricsIndex`、`PlaybackController`。
- Produces: `LyricsScreen(track, livePositionMs, onBack, onToggle, onNext, onPrev, isPlaying)`;`DrawerContent(vm, onClose)`。

- [ ] **Step 1: 删除 NowPlayingScreen.kt**
```bash
git rm android/app/src/main/java/com/tingmusic/ui/NowPlayingScreen.kt
```

- [ ] **Step 2: 创建 LyricsScreen.kt**

按 spec §8。`Box(Modifier.fillMaxSize().background(RB.Bg))`:
1. 背景:`val cover = rememberCover(track); if(cover!=null) Image(cover, null, ContentScale.Crop, Modifier.fillMaxSize().blur(46.dp).graphicsLayer{ scaleX=1.3f; scaleY=1.3f })`;再叠 `Box(Modifier.fillMaxSize().background(Color(0xD7080808)))`(rgba(8,8,8,0.84))。
2. `Column(Modifier.fillMaxSize())`:顶栏(h50)⌄ `IconButton(onBack)` + 居中 标题/艺术家(白)。
3. 歌词区 `Box(Modifier.weight(1f).clickable{onBack()})`:解析 `remember(track){ track.lrcFile?.takeIf{it.isFile}?.let{ runCatching{LrcParser.parse(it.readText())}.getOrNull() } }`。`Lyrics.Synced` → `val active=LyricsIndex.activeIndex(lines,livePositionMs); val st=rememberLazyListState(); LaunchedEffect(active){ if(active>=0) st.animateScrollToItem(active) }; LazyColumn(state=st, contentPadding=PaddingValues(vertical=...大}){ items(lines.size){ i-> Text(lines[i].text.ifBlank{"♪"}, Modifier.fillMaxWidth().padding(11.dp,24.dp), textAlign=Center, color= if(i==active) Color.White else Color(0x4DFFFFFF), fontSize= if(i==active)17.sp else 14.5.sp, fontWeight= if(i==active)SemiBold else Normal) } }`。`Plain`/null → 居中 "暂无歌词" `RB.TextDim`。
4. 底部控制 `Row(Center, gap 40, padding 10/0/22)`:prev(28)/ **红键 58dp**(`RB.Red`,播放/暂停白 24)/ next(28)。

- [ ] **Step 3: 重写 DrawerContent.kt(红黑 + 同步 + 关于)**

按 spec §9。`ModalDrawerSheet(drawerContainerColor=RB.DrawerBg)`,本地 `var view by remember{ mutableStateOf("menu") }`:
- 头部 `Row(padding 28/22)` 底部 `Divider(RB.Divider)`:红 logo `Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(RB.Red), center){ Icon(Icons.Filled.PlayArrow, null, tint=Color.White, size 22) }` + `Column{ Text("TingMusic",17,Bold,RB.Text); Text("局域网音乐播放器 · 你的私人电台",11.5,RB.TextDim) }`。
- **menu** 视图:
  - "局域网同步" 段标题(`RB.TextDim`),下面沿用 v2 DrawerContent 的同步控件(用 `vm.state`):`OutlinedButton("扫描 Mac"){vm.startDiscovery()}` + 红 `Button("手填同步", enabled=!syncing&&manualHost.isNotBlank()){vm.sync(vm.manualBaseUrl())}` + `OutlinedTextField(manualHost,...)` + 发现的服务器行(各 `Button("同步"){vm.sync(server.baseUrl)}`)+ 进度文案。按钮用红强调(`ButtonDefaults.buttonColors(containerColor=RB.Red)`)。
  - "关于 TingMusic" 行 `Row(clickable{view="about"})`{ 图标 + Text 15 + chevron }。
  - 底部 "© 2026 TingMusic · v0.1.0" 11 `RB.TextWeak`。
- **about** 视图:⌐ 返回(`view="menu"`)+ 红 logo 74 圆角20 + "TingMusic" 18 Bold + "版本 0.1.0" 12 `RB.TextDim` + "本地局域网同步音乐播放器" `RB.TextDim` + 底部 "© 2026 · Made with 红 & 黑"。
> 把 v2 DrawerContent 里同步控件的逻辑搬过来(`collectAsStateWithLifecycle` 取 `vm.state`、`progressText` 辅助函数),仅换配色与排版。去掉主题切换段。

- [ ] **Step 4: 重写 MainActivity.kt(三屏导航 + 抽屉 + 轮询)**

Replace `android/app/src/main/java/com/tingmusic/MainActivity.kt` 全文(基于现有结构改为三屏 + 单一 `TingMusicTheme{}`,去掉 theme 参数):
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingmusic.playback.PlaybackController
import com.tingmusic.ui.DrawerContent
import com.tingmusic.ui.LyricsScreen
import com.tingmusic.ui.MiniPlayer
import com.tingmusic.ui.PlayerScreen
import com.tingmusic.ui.PlaylistScreen
import com.tingmusic.ui.SyncViewModel
import com.tingmusic.ui.theme.TingMusicTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: SyncViewModel by viewModels()
    private lateinit var playback: PlaybackController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playback = PlaybackController(applicationContext)
        enableEdgeToEdge()
        setContent {
            TingMusicTheme {
                val ui by vm.state.collectAsStateWithLifecycle()
                val pstate by playback.state.collectAsState()
                var screen by remember { mutableStateOf("list") } // list | player | lyrics
                var livePos by remember { mutableLongStateOf(0L) }
                LaunchedEffect(pstate.isPlaying, pstate.currentId) {
                    while (true) { livePos = playback.currentPositionMs(); delay(300) }
                }
                LaunchedEffect(ui.tracks) { playback.setLibrary(ui.tracks) }

                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val current = ui.tracks.find { it.id == pstate.currentId }
                val dur = if (pstate.durationMs > 0) pstate.durationMs else (current?.durationMs ?: 0L)
                val progress = if (dur > 0) (livePos.toFloat() / dur) else 0f

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = { DrawerContent(vm, onClose = { scope.launch { drawerState.close() } }) },
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            if (current != null) {
                                MiniPlayer(
                                    track = current, isPlaying = pstate.isPlaying, progress = progress,
                                    onToggle = { playback.togglePlayPause() },
                                    onOpen = { screen = "player" },
                                )
                            }
                        },
                    ) { inner ->
                        PlaylistScreen(
                            tracks = ui.tracks, currentId = pstate.currentId, isPlaying = pstate.isPlaying,
                            onPlayTrack = { playback.play(it); screen = "player" },
                            onPlayAll = { ui.tracks.firstOrNull()?.let { playback.play(it); screen = "player" } },
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            modifier = Modifier.padding(inner),
                        )
                    }
                }

                AnimatedVisibility(
                    visible = screen != "list" && current != null,
                    enter = slideInVertically { it }, exit = slideOutVertically { it },
                ) {
                    current?.let { c ->
                        PlayerScreen(
                            track = c, state = pstate, livePositionMs = livePos,
                            onClose = { screen = "list" },
                            onOpenLyrics = { screen = "lyrics" },
                            onToggle = { playback.togglePlayPause() },
                            onNext = { playback.next() }, onPrev = { playback.prev() },
                            onSeek = { playback.seekTo(it) }, onCycleMode = { playback.cycleMode() },
                        )
                    }
                }
                AnimatedVisibility(
                    visible = screen == "lyrics" && current != null,
                    enter = slideInVertically { it }, exit = slideOutVertically { it },
                ) {
                    current?.let { c ->
                        LyricsScreen(
                            track = c, livePositionMs = livePos, isPlaying = pstate.isPlaying,
                            onBack = { screen = "player" },
                            onToggle = { playback.togglePlayPause() },
                            onNext = { playback.next() }, onPrev = { playback.prev() },
                        )
                    }
                }
                BackHandler(enabled = screen != "list") {
                    screen = if (screen == "lyrics") "player" else "list"
                }
            }
        }
    }

    override fun onStart() { super.onStart(); playback.connect() }
    override fun onStop() { super.onStop(); playback.release() }
}
```

- [ ] **Step 5: 整体编译 + 全部单测(此刻必须绿)**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL;单测 **38 个**通过(40 − AppTheme 2)。修正所有 Compose import/API/签名不匹配直到绿。

- [ ] **Step 6: Commit**
```bash
git add -A android/app/src/main/java/com/tingmusic/ui android/app/src/main/java/com/tingmusic/MainActivity.kt
git commit -m "feat(redblack): lyrics screen + red-black drawer + three-screen navigation"
```

---

### Task 5: 真机端到端验证(控制者执行)

**Files:** 无(验证)。前置:镜像曲库已在;手机已解锁。

- [ ] **Step 1: 装 + 启动**
```bash
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB wait-for-device; $ADB shell input keyevent KEYCODE_WAKEUP
$ADB install -r /Users/kongkongyzt/Sites/TingMusic/android/app/build/outputs/apk/debug/app-debug.apk
$ADB shell am start -n com.tingmusic/.MainActivity
$ADB exec-out screencap -p > /tmp/rb_list.png
```
Expected:红黑列表页——☰ + 搜索框、"我的曲库 · N 首"、播放全部、曲目行(封面+标题+艺术家),底部 mini 条。

- [ ] **Step 2: 列表/均衡器/播放全部**:点"播放全部"或某行 → 上滑进播放器;返回列表(⌄)看当前曲红色 + 红色均衡器在跳、mini 条在播。
- [ ] **Step 3: 播放器页**:凹槽黑胶旋转(播放)+ 唱臂落下、大红播放键、进度条走动、点 repeat 图标循环模式变色。`dumpsys media_session | grep state=` = 3。
- [ ] **Step 4: 歌词页**:点黑胶 → 模糊背景 + 居中滚动歌词(当前行白色大字),点歌词区/⌄ 回播放器。
- [ ] **Step 5: 抽屉**:列表点 ☰ → 红黑抽屉(红 logo + 局域网同步控件 + 关于);点"关于"进子页再返回。
- [ ] **Step 6: 返回键**:歌词→播放器→列表三级回退。截图存档。失败查 `$ADB logcat -d -t 300 | grep -iE "tingmusic|AndroidRuntime"`。

---

## Self-Review

**Spec 覆盖(§3–§14):** token→T1 ✅;列表/mini→T2 ✅;播放器/黑胶/唱臂→T3 ✅;歌词/抽屉/三屏导航→T4 ✅;删主题机制→T1 ✅;退役 NowPlayingScreen→T4 ✅;真机→T5 ✅。§14 不做项(搜索功能/⋮菜单/音质/定时/法务行)计划均未实现 ✅。

**占位扫描:** 难画组件(Theme/RedBlackPieces/Vinyl/MainActivity)给完整代码;各屏给布局结构 + 精确 token + 关键片段 + 指向 spec。`clickableNoRipple` 占位已注明实现者用标准 `clickable(indication=null,...)` 替换。无 TBD。

**类型一致性:** `RB.*`、`EqualizerBars`、`ConicPlayButton(progress,isPlaying,onClick,sizeDp)`、`RedCirclePlay(sizeDp)`、`VinylDisc(cover,isPlaying,sizeDp)`、`PlaylistScreen(tracks,currentId,isPlaying,onPlayTrack,onPlayAll,onOpenDrawer,modifier)`、`MiniPlayer(track,isPlaying,progress,onToggle,onOpen)`、`PlayerScreen(track,state,livePositionMs,onClose,onOpenLyrics,onToggle,onNext,onPrev,onSeek,onCycleMode)`、`LyricsScreen(track,livePositionMs,isPlaying,onBack,onToggle,onNext,onPrev)`、`DrawerContent(vm,onClose)` 在 MainActivity 调用处一致。复用 `PlaybackController`/`PlaybackState`/`PlayMode`/`SyncViewModel`/`rememberCover`/`CoverImage` 签名与 Global Constraints 一致。

**执行注记:**
- Task1–3 期间整体不编译(下游 MainActivity 未接线),属预期;Task4 Step5 收口必须绿。实现 SDD 时,Task1–3 的"局部编译检查"只要求各自文件无语法错;最终编译在 Task4 验。
- Material 图标(Menu/Search/MoreVert/KeyboardArrowDown/SkipPrevious/SkipNext/PlayArrow/Pause/Repeat/RepeatOne/Shuffle/OpenInFull/Sort/QueueMusic 等)来自 `material-icons-extended`(已在);个别名不存在就换最接近的同义图标。
- 删 `AppThemeTest` 后单测应为 38;若实际不同以 `./gradlew :app:testDebugUnitTest` XML 为准。
- 真机 UI 驱动用 `uiautomator dump` 取坐标 + `adb shell input tap`;USB 掉线 `wait-for-device` 兜;左边缘滑=系统返回,别用来开抽屉。
