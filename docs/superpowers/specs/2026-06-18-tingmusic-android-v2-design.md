# TingMusic Android v2 — 网易云风 UI 重做 + iTunes 封面 + 主题 设计文档

- 日期:2026-06-18
- 状态:已与用户对齐(brainstorming),待 spec review
- 关联:v1 设计 `docs/superpowers/specs/2026-06-16-tingmusic-android-design.md`(§11 曾把 iTunes 封面/主题/圆方切换列为 v2);本 v2 把 UI 重做成网易云风,并纳入 iTunes 封面 + 主题(**圆/方封面切换砍掉,YAGNI**)。

## 1. 目标

把现有"单屏 + 列表/歌词 tab"的安卓界面重做成**网易云音乐风格的多屏结构**,并补上两个 v1 缓做的能力:

1. **导航重做**:左侧抽屉(设置)+ 播放列表主屏(底部常驻 mini 播放条)+ 独立播放页;点歌/点 mini 条滑入播放页;播放页是旋转黑胶唱片,点一下切到全屏滚动歌词,再点切回。
2. **iTunes 联网拉封面**:无内嵌封面的曲目走 iTunes Search 拉 600×600 封面并本地缓存(镜像 Mac `cover_fetch.rs`)。
3. **两套主题**:默认灰 / 白红,抽屉里切换并持久化。

## 2. 决策记录(已确认)

| 决策点 | 结论 |
|---|---|
| 导航外壳 | 左抽屉 + 列表主屏 + **底部 mini 播放条**(网易云同款)+ 全屏播放页覆盖层 |
| 播放页精致度 | **网易云风**:模糊封面背景 + 唱臂(暂停抬起/播放落下)+ 旋转黑胶;点碟 ⇄ 全屏歌词 |
| 设置入口 | **左抽屉**(取代之前设想的"齿轮→底部弹窗") |
| 抽屉内容 | 主题切换 + 局域网同步控件 + 关于/版本(**手动重扫砍掉**) |
| v2 功能范围 | iTunes 联网封面 + 两套主题(**圆/方封面切换砍掉**) |
| 主题持久化 | `SettingsStore`(SharedPreferences,零依赖) |
| 导航实现 | 状态驱动(无 Navigation-Compose 依赖):`ModalNavigationDrawer` + `AnimatedVisibility` 上滑播放页 |

## 3. 不改动的部分

播放核心原样保留,UI 只是换了"驱动它的壳":
- `playback/PlaybackService.kt`(MediaSessionService + ExoPlayer)
- `playback/PlaybackController.kt`(MediaController 封装,`state: StateFlow<PlaybackState>` + 命令)
- `playback/PlayMode.kt`、`playback/LyricsIndex.kt`
- `library/*`(Track / LibraryIndexer / LrcParser / LrcMatch / Lyrics)
- `sync/*`(Discovery / SyncClient / SyncEngine / SyncStateStore / Manifest / SyncDiff)

## 4. 导航外壳

```
ModalNavigationDrawer(drawerContent = DrawerContent)        ← 左侧抽屉
└── Scaffold(
      topBar = { ☰(开抽屉) + "TingMusic" },
      bottomBar = { MiniPlayer(当前曲目;点→打开播放页) },   ← 仅当有当前曲目时显示
    ) { PlaylistScreen(点行→播放并打开播放页) }
+ AnimatedVisibility(showNowPlaying, 上滑/下滑) { NowPlayingScreen }   ← 全屏覆盖层
```

- 顶层 UI 状态(在 `MainActivity` 的 Compose 里 `remember`):`showNowPlaying: Boolean`、`lyricsMode: Boolean`、抽屉 `DrawerState`。
- 打开播放页:点列表行(先 `playback.play(track)` 再 `showNowPlaying = true`)或点 mini 条(只 `showNowPlaying = true`)。
- 返回:播放页顶部 ⌄ → `showNowPlaying = false`;系统返回键在播放页打开时也先关播放页(`BackHandler`)。

## 5. 播放列表主屏 `PlaylistScreen`

- 曲库 `List<Track>`(来自 `SyncViewModel.state.tracks`)的 `LazyColumn`。
- 每行:`CoverImage`(~48dp 圆形)+ 标题 + 艺术家(`· 有歌词` 标记);当前播放行高亮(主题强调色)。
- 点行:`onPlayTrack(track)` → `playback.play(track)` + 打开播放页。
- 空库时显示引导文案("打开左侧抽屉,扫描局域网内的 Mac 同步曲库")。

## 6. 底部 mini 播放条 `MiniPlayer`

- 仅当 `playback.state.currentId != null` 显示。
- 小封面(~40dp)+ 标题/艺术家(单行省略)+ 播放/暂停按钮。
- 点条主体 → 打开播放页;点播放/暂停 → `playback.togglePlayPause()`。
- 顶部一条极细进度条(可选,按 position/duration)。

## 7. 播放页 `NowPlayingScreen`(网易云风)

布局(全屏,`Box` 叠层):
1. **背景**:当前封面的模糊放大图。用 `Modifier.blur(24.dp)`(API 31+ 生效);叠一层暗色 scrim(如 `Color.Black.copy(alpha=0.45f)`)保证前景文字可读。**API < 31**:`blur` 无效 → 仅靠 scrim 呈现加暗封面,不崩(`minSdk 26`)。封面为空时背景用主题底色。
2. **顶部**:⌄(关播放页)+ 居中标题(可选 ⋮ 占位,v2 不做菜单)。
3. **中央碟区**(点击切歌词):
   - 黑胶唱片:`CoverImage` 嵌在深色胶圈中(画 grooves + 红色中心标签);播放时 `rememberInfiniteTransition` 旋转 20s/圈,暂停停在当前角度(用可保存的角度状态)。
   - **唱臂**:从右上支点描边绘制(Canvas)。`animateFloatAsState` 控制臂角度:播放→落到碟面(贴近中心标签),暂停→抬起约 -20°。
4. **下部控制**:`Title · Artist`、进度 `Slider`(松手 seek)、`m:ss / m:ss`、⏮ ⏸/▶ ⏭、模式按钮(顺序/随机/单曲)。
5. **歌词态**(`lyricsMode = true`):中央碟区交叉淡出、全屏 `LyricsView`(复用 v1 逻辑:解析当前曲 `.lrc`、`LyricsIndex.activeIndex` 高亮、`animateScrollToItem` 居中)淡入;背景模糊图保留;再点回碟。控制区保持。

## 8. 封面 `CoverImage` + `CoverFetcher`

### CoverFetcher(镜像 Mac cover_fetch.rs)
- `suspend fun fetch(title, artist, trackId): ImageBitmap?`(`Dispatchers.IO`):
  1. 本地缓存命中(`cacheDir/covers/<key>.jpg`,`key` = trackId 的稳定哈希,因 trackId 是相对路径含 `/`)→ 直接解码返回。
  2. title 为空 → null。
  3. iTunes:`GET https://itunes.apple.com/search?term=<encode(title+" "+artist)>&media=music&limit=1`(8s 超时,UA `TingMusic/0.1`);取 `results[0].artworkUrl100`;`100x100bb`→`600x600bb`;下载 JPEG;非 200/空 → null;成功写缓存并返回。
- 纯逻辑(JVM 单测):search term 拼接(artist 为空/Unknown 时只用 title)、`100x100bb→600x600bb` 替换、从 JSON 取 `artworkUrl100`、缓存文件名 key 哈希稳定性。

### CoverImage(Composable)
- 入参 `track`(取 file 内嵌、title/artist/id 拉网)、`sizeDp`、`isPlaying`(是否旋转,仅大碟用)、`vinylFrame: Boolean`(大碟=true)。
- 回退链(`produceState`,`Dispatchers.IO`):`MediaMetadataRetriever.embeddedPicture` → `CoverFetcher.fetch` → null。
- 有图:圆形裁剪显示(大碟带胶圈 + 可旋转);无图:画黑胶占位(红心 #d33a31)。
- 列表行 / mini 条:小尺寸、无唱臂、不旋转(或仅大碟旋转)。

## 9. 抽屉 `DrawerContent`

竖向排列(可滚动):
- **主题**:`默认灰 / 白红` 胶囊按钮 → `settings.setTheme(...)`(即时生效 + 持久化)。
- **局域网同步**:把 v1 主屏上的控件搬来——"扫描 Mac" / 手填 IP 输入框 / 发现的服务器列表(各带"同步")/ 同步进度文案。逻辑复用 `SyncViewModel`。
- **关于**:`TingMusic` + 版本号(`BuildConfig.VERSION_NAME` 或硬编码 "0.1.0")。

## 10. 主题 + 持久化

- `ui/theme/Theme.kt`:`enum AppTheme { DEFAULT, WHITE_RED }`;两套 `ColorScheme`(DEFAULT 偏中性灰、红仅作锚点;WHITE_RED 白底 + 红强调);`@Composable fun TingMusicTheme(theme, content)` 选 `colorScheme` 套 `MaterialTheme`。
- `data/SettingsStore.kt`:`SharedPreferences` 包装,`themeFlow: StateFlow<AppTheme>` + `setTheme(AppTheme)`。在 `SyncViewModel` 里持有(它已是 `AndroidViewModel`,有 app context),`MainActivity` 收集 theme 驱动 `TingMusicTheme`。

## 11. 数据流

- `PlaybackController.state`(StateFlow)→ mini 条 + 播放页(currentId/isPlaying/duration/mode)。
- 进度:`MainActivity` 每 ~300ms 主线程轮询 `playback.currentPositionMs()` → `livePos`,喂播放页进度条 + 歌词高亮。
- `SyncViewModel.state.tracks` → 列表 + `playback.setLibrary(...)`。
- `SettingsStore.themeFlow` → `TingMusicTheme`。

## 12. 错误处理

- iTunes 超时/非 200/空结果/解码失败 → 返回 null → 黑胶占位(静默)。
- `Modifier.blur` 在 API<31 无效 → 仅 scrim 加暗封面(不崩)。
- 当前曲无 `.lrc` → 歌词态显示"无歌词";有但解析失败 → 纯文本/"无歌词"。
- 列表为空(未同步)→ 引导文案,不显示 mini 条/播放页。

## 13. 测试

- **JVM 单测**:`CoverFetcher` 的 term 拼接、`100→600` 替换、`artworkUrl100` JSON 解析、缓存 key 哈希稳定。(v1 既有 32 个单测保持通过。)
- **真机验证**:抽屉开合 + 主题即时切换;列表点歌→上滑进播放页;唱片旋转(播放)/停(暂停);唱臂落下/抬起;模糊背景(API 33);点碟⇄歌词;歌词高亮滚动;mini 条显示/点击;iTunes 封面对一首无内嵌封面的歌生效(需公网);返回键/⌄ 关播放页。

## 14. 文件结构

```
com/tingmusic/
├── MainActivity.kt            改:抽屉+Scaffold+mini条+播放页覆盖层+主题应用+位置轮询
├── data/SettingsStore.kt      新:主题持久化(SharedPreferences)
├── sync/CoverFetcher.kt       新:iTunes 拉封面 + 缓存(+ JVM 单测)
├── ui/
│   ├── theme/Theme.kt         新:两套 ColorScheme + TingMusicTheme
│   ├── PlaylistScreen.kt      新:曲库列表(点行→播放)
│   ├── MiniPlayer.kt          新:底部 mini 播放条
│   ├── NowPlayingScreen.kt    新:模糊背景+旋转碟+唱臂+控制+歌词态(复用 LyricsView 逻辑)
│   ├── DrawerContent.kt       新:主题/局域网同步/关于
│   └── CoverImage.kt          新:内嵌→缓存→iTunes→黑胶(并入旧 CoverArt)
└── (退役) ui/SyncScreen.kt、ui/NowPlaying.kt(逻辑迁入上面新屏)
```

## 15. 明确不做(v2 范围外)

- 圆/方封面形状切换(砍掉)。
- 播放队列管理 UI(增删、拖拽排序)、收藏/歌单。
- 播放页 ⋮ 菜单(分享/下载/定时等)。
- 手动重扫曲库入口。
- Navigation-Compose 依赖(用状态驱动即可)。

## 16. 里程碑(供 writing-plans 细化)

1. **主题地基**:`AppTheme` + 两套 ColorScheme + `TingMusicTheme` + `SettingsStore` + SyncViewModel 接入,抽屉里能切主题并持久化。
2. **iTunes 封面**:`CoverFetcher`(+ 单测)+ `CoverImage` 回退链。
3. **导航外壳**:`ModalNavigationDrawer` + Scaffold + `PlaylistScreen` + `MiniPlayer` + `DrawerContent`(含搬来的同步控件)+ 播放页上滑/下滑 + BackHandler。
4. **播放页**:模糊背景 + 旋转黑胶 + 唱臂 + 控制 + 点碟⇄全屏歌词。
5. **真机端到端**:全流程验证(同步→列表→点歌→播放页唱片/唱臂/歌词→mini 条→主题→iTunes 封面)。
```
