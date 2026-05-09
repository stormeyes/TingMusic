# TingMusic v0.1.0 实施计划

- 版本: v0.1.0
- 日期: 2026-05-09
- 对应 spec: `docs/superpowers/specs/2026-05-09-tingmusic-design.md`

> **For agentic workers:** REQUIRED SUB-SKILL: `superpowers:subagent-driven-development`(推荐)或 `superpowers:executing-plans`。任务用 checkbox 跟踪。

**Goal:** 从零搭一个 macOS 浮动卡片式本地音乐播放器。Tauri 2(decorations:false + transparent + macOSPrivateApi)+ React/TS/zustand 前端 + Rust 后端(rodio 0.22 symphonia / lofty / walkdir / reqwest)。

**架构:** 单窗口三态(400×{100|580|640}),前端通过 Tauri command 操作 Rust 端的 player / scanner / config / lyrics / cover_fetch 五个模块。100ms tick 推送 progress + 自动切歌(顺序模式末尾环绕);封面缺失时异步从 iTunes 拉一张缓存;dev 模式通过 NSApplication FFI 设置 dock 图标。

---

## 阶段 0 — 脚手架

### Task 1: 初始化 Vite + React + TS + Tauri 2

**Files:**
- Create: `package.json` `vite.config.ts` `tsconfig.json` `tsconfig.node.json` `index.html` `src/main.tsx` `src/App.tsx` `src/App.css`
- Create: `src-tauri/Cargo.toml` `src-tauri/build.rs` `src-tauri/tauri.conf.json` `src-tauri/src/main.rs` `src-tauri/src/lib.rs` `src-tauri/icons/icon.png`(占位 RGBA PNG)`src-tauri/capabilities/main.json`
- Create: `.gitignore`

- [ ] **Step 1:** `.gitignore` 列出 node_modules / dist / src-tauri/target / src-tauri/gen / .DS_Store / *.log / .vite
- [ ] **Step 2:** `package.json` deps: `@tauri-apps/api ^2`, `@tauri-apps/plugin-dialog ^2`, `react ^18.3`, `react-dom`, `react-window`, `zustand ^4.5`;dev: tauri-cli, testing-library, vitest, happy-dom, vite, ts
- [ ] **Step 3:** `vite.config.ts` 监听 1420 + happy-dom 测试环境
- [ ] **Step 4:** `tsconfig` target ES2020 + react-jsx + strict + vitest globals
- [ ] **Step 5:** `index.html` lang="zh"
- [ ] **Step 6:** 最小 `App.tsx` 占位
- [ ] **Step 7:** `Cargo.toml` deps:
  ```toml
  tauri = { version = "2", features = ["macos-private-api"] }
  tauri-plugin-dialog = "2"
  lofty = "0.21"
  rodio = { version = "0.22", default-features = false, features = ["playback", "symphonia-all"] }
  walkdir = "2"
  anyhow = "1"
  parking_lot = "0.12"
  sha2 = "0.10"
  hex = "0.4"
  base64 = "0.22"
  urlencoding = "2"
  serde = { version = "1", features = ["derive"] }
  serde_json = "1"
  tokio = { version = "1", features = ["macros","sync","time","rt-multi-thread"] }
  rand = "0.8"
  reqwest = { version = "0.12", default-features = false, features = ["json","rustls-tls"] }

  [target.'cfg(target_os = "macos")'.dependencies]
  objc2 = "0.6"
  objc2-app-kit = { version = "0.3", features = ["NSApplication","NSImage"] }
  objc2-foundation = { version = "0.3", features = ["NSData"] }

  [dev-dependencies]
  tempfile = "3"
  ```
- [ ] **Step 8:** `tauri.conf.json` —
  - `app.macOSPrivateApi: true`
  - window: 400×100, minWidth 360, minHeight 96, maxWidth 640, maxHeight 900
  - `decorations: false`, `transparent: true`, `resizable: true`, `shadow: false`
  - label `"main"`, productName `"TingMusic"`
- [ ] **Step 9:** `capabilities/main.json` 权限:`core:default`, `core:window:allow-set-size`, `core:window:allow-set-min-size`, `core:window:allow-set-max-size`, `core:window:allow-close`, `core:window:allow-start-dragging`, `dialog:default`
- [ ] **Step 10:** 占位 icon — 1×1 RGBA PNG(Tauri 2 严格要求 RGBA)
- [ ] **Step 11:** `npm install && cd src-tauri && cargo build` — 首次拉依赖会慢
- [ ] **Step 12:** 提交

---

## 阶段 1 — 后端核心

### Task 2: 共享类型 (`types.rs`)

- [ ] 枚举:`Mode { Sequential, Random, RepeatOne }` + `CoverShape { Circle, Square }` + `Theme { Default, Whitered }`,都用 `#[serde(rename_all = "lowercase")]` 和合适的 `Default`
- [ ] `Track` 不实现 Serialize;`TrackDto` 实现 + `From<&Track>`
- [ ] `Config` 字段:folder/volume/mode/cover_shape/theme;`cover_shape` 和 `theme` 用 `#[serde(default)]`
- [ ] `track_id_for_path` 用 sha256 + hex 截前 8 字节
- [ ] 5+ 单元测试
- [ ] 在 `lib.rs` 加 `pub mod types;`

### Task 3: 配置持久化 (`config.rs`)

- [ ] `read_from / write_to`,损坏自愈(读失败/解析失败 → 默认 + 立即覆写)
- [ ] 4 个测试:缺文件 / 损坏 / 损坏后被覆写 / 嵌套目录写入往返(往返字段必须包含 cover_shape + theme)

### Task 4: LRC 解析 (`lyrics.rs`)

- [ ] `parse_lrc(text) -> Lyrics`:支持 `[mm:ss.xx]` 多种精度、多时间戳同行、`[ti:][ar:]` 等元数据头(silently consume)、BOM、空输入,过半坏行降级为 `Plain(原文)`
- [ ] `load_sidecar_lrc(audio_path) -> Option<Lyrics>`:同目录、同 stem、大小写不敏感;**精确匹配失败时尝试把 audio stem 末尾 `#hash` 后缀去掉再匹配一次**(网易云/QQ 缓存哈希命名兼容);精确匹配优先
- [ ] `load_embedded_lyrics(audio_path)`:lofty 读 `ItemKey::Lyrics`(USLT)
- [ ] `load_lyrics`:sidecar 优先,然后 embedded
- [ ] 9+ 测试

### Task 5: 文件夹扫描 (`scanner.rs`)

- [ ] `pub struct ScanOutput { tracks, skipped }` + `pub fn scan(folder)`
- [ ] `build_track(path)`:lofty 读元数据 + base64 封面 + 时长 + 调 `lyrics::load_lyrics`
- [ ] **当 ID3 完全空时**(无 TrackTitle 也无 TrackArtist),从文件名 stem 按最右 `-` 拆分为 `title - artist`,artist 末尾 `#hash` 剥离;否则维持 stem 全名 + Unknown
- [ ] 排序:`tracks.sort_by(|a, b| a.path.cmp(&b.path))`
- [ ] 7 个测试:扩展名过滤 / 空目录 / 元数据缺失回退 / 递归子目录 / `Title-Artist` 拆分 / `#hash` 剥离 / 无 hyphen 不拆

### Task 6: 播放引擎 (`player.rs`)

最复杂的一块。

- [ ] `struct SendDevice(rodio::MixerDeviceSink)` + `unsafe impl Send + Sync`(SAFETY 注释:device sink 只是被持有,不在其他线程访问;CoreAudio 的 dealloc 跨线程安全)
- [ ] `Player` 内部:`Arc<Mutex<Inner>>` 持有 queue / current_index / current sink / started_at / paused_position_ms / volume / mode / last_random
- [ ] `Snapshot { track_id, position_ms, is_playing, is_finished }` 由 `pub fn snapshot()` 返回
- [ ] 公开方法:`new(volume, mode) -> Result<Self>`(用 `DeviceSinkBuilder::open_default_sink()`), `set_queue(tracks)`, `set_volume(f32)` (clamped), `set_mode(Mode)`, `play_track_id(&str) -> Result<()>`, `toggle_pause()`, `next() -> Result<()>`, `prev() -> Result<()>`, `seek(u64) -> Result<()>`, `compute_next_index(advance_on_finish: bool) -> Option<usize>`
- [ ] **建立 sink:** `RodioPlayer::connect_new(self.device.0.mixer())` 然后 set_volume + append + play
- [ ] **`seek` 双路径**:先尝试 `sink.try_seek(target)`,Ok 则更新内部时钟;Err 则 fallback 到 `build_sink + skip_duration`(慢)
- [ ] **`build_sink` 用 `catch_unwind` 包住 `Decoder::new`**:历史上 rodio 0.19 的 m4a 解码器会 panic,留作安全网
- [ ] `compute_next_index` 在 RepeatOne+advance_on_finish 返回当前;**Sequential/RepeatOne(手动 next)在末尾 wrap**:`(cur + 1) % len`;Random 排除当前和上一次 last_random
- [ ] 严格的锁纪律:不在持锁状态下做 IO(file open / decode);锁只在读/改 Inner 字段时短期持有
- [ ] `toggle_pause` 内部要先把 `is_paused` 抽到本地 bool 再做后续可变操作(避免 borrow 冲突)
- [ ] `seek` 取 queue 元素用 `.get(i).cloned()` 而不是 `[i]`(防 set_queue 与 seek 间隙的 panic)
- [ ] 4 个测试:Sequential **wrap** at end / RepeatOne 重播 / Random 不立即重复当前 / 音量夹紧

### Task 7: 联网拉封面 (`cover_fetch.rs`)

- [ ] `pub async fn fetch_cover(title, artist, cache_dir, track_id) -> anyhow::Result<Option<String>>`
- [ ] 先看 `cache_dir/<track_id>.jpg`,有则直接返回 base64 data URL
- [ ] 否则查 iTunes:`https://itunes.apple.com/search?term=...&media=music&limit=1`;reqwest client `.timeout(8s).user_agent("TingMusic/0.1")`
- [ ] 解析 `artworkUrl100`,把 `100x100bb` 替换为 `600x600bb`
- [ ] 下载 → 写文件 → 返回 data URL
- [ ] artist == "Unknown" 或空 → 仅用 title 查
- [ ] 任何步骤失败都返回 `Ok(None)`(让前端静默回落)

### Task 8: Tauri commands + tick + 自动切歌 + macOS dock 图标 (`lib.rs`)

- [ ] `AppState` 包含 `Arc<Player>`, `Mutex<Vec<Track>>`, `config_path`, `Mutex<Config>`, `cover_cache_dir`
- [ ] 11 个 `#[tauri::command]`:`load_config`, `save_config`, `scan_folder`, `play`, `toggle_pause`, `next_track`, `prev_track`, `seek`, `set_volume`, `set_mode`, `fetch_cover`(async)
- [ ] setup hook:
  - `#[cfg(target_os = "macos")] set_macos_dock_icon();`(用 objc2 调 `NSApplication::sharedApplication(mtm).setApplicationIconImage(image)`,图标 PNG 走 `include_bytes!`)
  - 从 `app.path().app_config_dir()` 读 config
  - 初始化 Player,manage AppState
  - spawn 100ms tick 任务:`player.snapshot()` → emit "progress";若 `is_finished` → 算 next_index → 通过 `state.tracks.lock().get(idx)` 拿 id → `play_track_id` + emit "track_changed";否则 if track_id 改变了 emit "track_changed"
- [ ] `cargo build` 通过

---

## 阶段 2 — 前端基础

### Task 9: 共享类型 + API wrapper (`src/lib/`)

- [ ] `types.ts` 镜像 Rust:`Mode`, `CoverShape`, `Theme`, `LyricLine`, `Lyrics(union)`, `Track`, `ScanResult`, `PlaybackState`, `Config(含 cover_shape + theme)`
- [ ] `api.ts` — `invoke<T>(name, args)` 包装 11 个 command。Tauri 自动 camelCase → snake_case
- [ ] 2 个测试:loadConfig + scanFolder via mock invoke

### Task 10: zustand store (`store.ts`)

- [ ] State 字段:`tracks, currentTrackId, position, isPlaying, volume, mode, view, folder, skipped, expanded, settingsOpen, coverShape, theme`
- [ ] Setters 一一对应
- [ ] `currentTrack()` 派生
- [ ] `activeLyricIndex()` 二分查找
- [ ] `__resetForTest()` 用于测试
- [ ] 3 个测试

---

## 阶段 3 — 前端核心组件

### Task 11: NowPlaying(卡片)

- [ ] 子组件:`CoverPlaceholder`(SVG 黑胶,viewBox 24×24:disc r=11 #0a0a0a,3 圈 grooves,中心红色 #d33a31 r=3.5,spindle hole r=0.6,左上 radial-gradient 反光)、`PlayGlyph`、`PauseGlyph`、`ExpandGlyph(open: bool)`
- [ ] 主组件:订阅 store(track / isPlaying / position / expanded / coverShape)
- [ ] 空状态:`now-playing.empty` + 占位封面 + meta(标题 "无歌曲" + Progress) + Settings + ExpandButton
- [ ] 有 track:封面按钮 + meta(title-line 单行 "Title - Artist" + Progress) + Settings + ExpandButton
- [ ] cover-button onClick:**首次播放语义** — `if (!isPlaying && position === 0) api.play(track.id) else api.togglePause()`
- [ ] cover 按钮的类:`cover-button` + `playing|paused` + (coverShape === "circle" ? "cover-circle" : "")
- [ ] 在卡片根加 `data-tauri-drag-region`,meta 也加
- [ ] useEffect 在切歌且 cover_data_url 缺失时调 `api.fetchCover` → setFetchedCover;清理用 cancelled 标志
- [ ] 4 个测试

### Task 12: Progress

- [ ] state `seekingValue: number | null`
- [ ] `sliderValue = seekingValue ?? Math.min(position, duration)`
- [ ] onChange 设 seekingValue
- [ ] commitSeek(mouseUp / touchEnd / keyUp):`api.seek(target) + setPosition(target) + setTimeout(clear seekingValue, 350)`(撑过 IPC + 一次 tick)
- [ ] 时间显示:`${fmt(sliderValue)} / ${fmt(duration)}`

### Task 13: Playlist + Lyrics + Tabs

- [ ] **Playlist**:`react-window` `FixedSizeList`,行 = title + artist 两栏(`flex: 2 / 1, min-width: 0` 保 ellipsis);click → `setCurrentTrackId + api.play`;空状态 "未找到音频文件";1-2 个测试
- [ ] **Lyrics**:订阅 currentTrack + activeLyricIndex;Synced 时遍历渲染 `[data-line=i]`,active 行 `transform: scale(1.05) + 加粗 + 100% 不透明 + color: var(--hi)`;useEffect 监听 activeIndex 变化用 `scrollIntoView({block:'center', behavior:'smooth'})`;Plain 整段;空状态 "暂无歌词";测试需要给 happy-dom 填 `Element.prototype.scrollIntoView` shim
- [ ] **Tabs**:列表 / 歌词 两按钮,active 加灰色下划线 + var(--hi)

### Task 14: Settings(齿轮下拉)

- [ ] state `menuOpen` + `useEffect` 把 menuOpen 镜像到 store.settingsOpen
- [ ] `useEffect` 注册 document mousedown 监听点击外部关闭(对 wrapRef.current.contains 判断)
- [ ] 折叠所有功能到下拉:
  - 选择曲库… → `dialog::open({directory: true})` → `api.scanFolder` → `api.saveConfig`(关闭菜单)
  - (条件)已忽略 N 个文件
  - divider
  - 音量 (label) + 喇叭+声波图标 + range slider — 200ms debounce 写 config
  - divider
  - 播放顺序 (label) + 3 个 `.settings-pill` 横排:`顺序 / 随机 / 单曲循环`
  - divider
  - 封面形状 (label) + 2 个 pill:`圆形 / 方形`
  - divider
  - 主题 (label) + 2 个 pill:`默认灰 / 白红`
  - divider
  - 退出 (`getCurrentWindow().close()`)(关闭菜单)
- [ ] **选项点击不关闭菜单**(`chooseMode/chooseShape/chooseTheme` 都不调 `setMenuOpen(false)`)— 只有动作类项 + 点击外部 + 再点齿轮才关
- [ ] **每个 saveConfig 调用必须包含全部 5 个字段**(folder, volume, mode, cover_shape, theme)— 否则 `#[serde(default)]` 会把缺字段重置成默认

### Task 15: App.tsx — 顶层布局 + 窗口动画 + 空格快捷键

- [ ] 三种尺寸常量:`COMPACT_SIZE 400×100`, `SETTINGS_SIZE 400×580`, `EXPANDED_SIZE 400×640`
- [ ] **`panelMounted` 状态**:`expanded -> true` 时立即 setPanelMounted(true);`expanded -> false` 时 setTimeout 260ms
- [ ] **窗口动画 useEffect**:依赖 `[expanded, settingsOpen]`;await 当前 logical height,然后 `requestAnimationFrame` 循环 240ms ease-out cubic 把 setSize 推到 target;cleanup 设 cancelled 标志
- [ ] **空格快捷键 useEffect**:`window.addEventListener("keydown", onKey)`;判 `e.code === "Space"` 且 target 不是 BUTTON/INPUT/TEXTAREA 时,e.preventDefault + 用 `useStore.getState()` 拿当前 track,首次播放走 api.play 否则 togglePause
- [ ] 启动 effect:`api.loadConfig` → 应用 volume/mode/coverShape/theme;若 cfg.folder 缺失用 `homeDir() + "Music"`;`api.scanFolder` + setTracks/setSkipped;若有 tracks 自动 `setCurrentTrackId(tracks[0].id)`(只设元数据,不播放);首次启动写一次 saveConfig
- [ ] 注册 listen("progress") + listen("track_changed")
- [ ] JSX 根:`<div className={`app theme-${theme} ${panelMounted ? "expanded" : "compact"}`}>`(用 panelMounted 而非 expanded,避免收起时类突变导致间隙)
- [ ] 子结构:`.header-row > NowPlaying`;`{panelMounted && <div className="panel"><Tabs/><div className="middle">{view === "list" ? <Playlist/> : <Lyrics/>}</div></div>}`

---

## 阶段 4 — 视觉打磨

### Task 16: CSS 主题与卡片

- [ ] `:root` CSS 变量:`--accent: #d33a31`(用在黑胶中心 + 激活 pill border),`--hi: light-dark(#1a1a1a, #f0f0f0)`,`--card-bg: light-dark(rgba(255,255,255,0.95), rgba(36,36,36,0.95))`,`--card-border`, `--card-shadow`, `--row-hover`, `--row-active-bg`, `--muted`, `--track`, `--text`
- [ ] body/html/root `background: transparent` + `overflow: hidden`(防设置展开时滚动条闪现)
- [ ] `.app` flex 列 + 8px padding + 8px gap;`.app.compact { padding-bottom: 0 }`;`.app.expanded { gap: 0 }` + 卡片底部圆角清零 + panel 顶部圆角和 border 清零(单一表面)
- [ ] `.now-playing` `position: relative; z-index: 10` + flex + padding-right 36 + var card-bg/border/shadow + backdrop-filter blur 12
- [ ] `.cover` 64×64 border-radius 8;`.cover-circle` 50% + 占位封面 bg #0a0a0a + svg 100%;`@keyframes cover-spin` to rotate 360deg 20s linear infinite;`.cover-circle.playing > img/.placeholder` animation-play-state running
- [ ] cover-overlay 32×32 圆 white-95 bg + 暗灰 icon;`.paused .cover-overlay { opacity: 1 }` `.playing:hover .cover-overlay { opacity: 1 }`
- [ ] 标题行:title 14px 600 max-width 60%,artist 12px var(--muted),dash 12px
- [ ] 进度条 + 音量手柄:**珍珠风** — radial-gradient + box-shadow 多层(crisp outline + halo + drop + inset top specular + inset bottom shading);hover 微缩放;focus 改用更亮 halo
- [ ] Settings 菜单:**无 z-index on `.settings-wrap`**(否则会困住菜单 z-index);`.settings-menu` position absolute top calc(100% + 4px) right 0,bg/border/shadow var(--card-*),backdrop-filter blur 12,z-index 100
- [ ] **`.settings-pill` 横排胶囊按钮**:flex: 1, justify-content: center, padding 7px 10px, border-radius 999px, transparent border by default;hover 加深 bg;active = `border-color: var(--accent); color: var(--hi); font-weight: 600; bg: var(--row-active-bg)`
- [ ] panel 与 tabs:flex 1, var(--card-bg) + shadow + backdrop-filter;tabs 一行,active 字色 var(--hi) + 灰色下划线
- [ ] playlist 行:hover var(--row-hover);active var(--row-active-bg) + active row-title var(--hi) 加粗
- [ ] lyrics:active 行 var(--hi) + 加粗 + scale 1.05;非 active 透明度 0.45
- [ ] **`.app.theme-whitered`** 重写所有变量给白底 + 红色高亮:`--card-bg: #ffffff`,`--accent + --hi: #d33a31`,`--row-active-bg: rgba(211,58,49,0.10)`,`--muted: #999`,激活 tab/列表行/歌词/pill 都自然变红;其中 `cover-overlay` 在白红主题下背景换为红色 + 白色 glyph(白底白圆看不见)

### Task 17: 端到端 smoke test(用户手动)

- [ ] `npm run tauri dev` 启动
- [ ] 首启自动扫描 ~/Music
- [ ] 拖动卡片 / 展开 chevron → 平滑动画 + 卡片与面板拼成一体 / 收起 → 同样平滑
- [ ] 设置:换曲库 / 调音量(滑动慢放快收 200ms)/ 切顺序 / 切形状 / 切主题 / 退出
- [ ] 圆形:播放时旋转 20s 一圈,暂停凝固
- [ ] 拖进度条松手 → 秒到位 + 不闪
- [ ] 没封面的歌:1-2 秒后从 iTunes 拉到的封面出现
- [ ] sidecar `.lrc` 同名 / `Song-Artist#hash.mp3` 与 `Song-Artist.lrc` 也能匹配
- [ ] 无 ID3 的 `Title-Artist.mp3` → 列表里也能正确显示标题和艺术家
- [ ] 顺序模式播完最后一首会回到第一首
- [ ] 空格切播放/暂停
- [ ] 重启:曲库 / 音量 / 模式 / 形状 / 主题全部记住
- [ ] dock 图标显示自定义黑胶艺术
- [ ] Cmd+Q / 设置→退出 → 进程清退

### Task 18: 自定义 dock 图标素材

- [ ] `scripts/make_icon.py`:Pillow 绘制 1024×1024 PNG — squircle mask(radius 22.5%)+ vertical gradient bg + 中央 vinyl(30% radius)+ 红色中心标签 + 反光高光
- [ ] 输出主图标 `src-tauri/icons/icon.png` + 32/64/128/256/512 变体
- [ ] 在 `lib.rs` 的 `set_macos_dock_icon` 函数里 `include_bytes!` 主图标

---

## 注意事项 / 历史踩坑

- **不要在持锁状态下做 IO** — player 的几个公开方法都遵循这个;违反会导致 100ms tick 阻塞从而音频卡顿
- **macOSPrivateApi** — 没开它,`transparent: true` 在 macOS 上会被静默忽略 + setSize 也会失败,这是早期最坑的一关
- **ACL 权限** — Tauri 2 的 `core:default` 不带 `core:window:allow-set-size`,要显式列。错误信息 `window.set_size not allowed. Permissions associated with this command: core:window:allow-set-size` 是关键线索
- **rodio 版本** — 0.19 默认特性带 minimp3/claxon,seek 不工作 + 某些 m4a 会触发内部 panic;升级到 0.22 + 显式 `playback + symphonia-all` features 是必须的;API 变化:`OutputStream/OutputStreamHandle/Sink` → `MixerDeviceSink/Mixer/Player`,设备打开走 `DeviceSinkBuilder::open_default_sink()`
- **`Sink::try_seek` 是 fast path** — 但要保留 build_sink + skip_duration 的 fallback
- **`light-dark()` CSS 函数** — Tauri 2 / WebKit 17.5+ 支持,直接用即可
- **不要在 `.settings-wrap` 上加 z-index** — 否则会把内部 settings-menu 的 z-index 困在 wrap 的 stacking context,让 chevron 浮在菜单之上
- **panelMounted 用于 className** — 收起动画期间用 `expanded` 类作切换会让样式瞬间退化,产生间隙;必须用 panelMounted
- **设置展开高度** — 至少 580 才能容下完整菜单(选择曲库 + 音量 + 三组 pill + 退出)
- **跨平台编译不能阻断** — transparency / windowEffects / objc2 是 macOS 特化路径但代码里没有平台判断,Linux/Windows 上视觉效果未验证
- **测试** — Rust ~31 单元 / 前端 ~14 单元;运行 `cd src-tauri && cargo test --lib` 和 `npm test`,本仓库不跑 `cargo clippy` / `golangci-lint` 等重型检查

## 状态

本计划反映 v0.1.0 实际实现路径。所有 18 个任务均已完成,Task 17 是用户的持续手动验收。
