# TingMusic 设计文档

- 版本: v0.1.0
- 日期: 2026-05-09
- 仓库: `/Users/kongkongyzt/Sites/TingMusic`

## 1. 目标与非目标

### 目标

- 一个**悬浮在桌面**的小巧 macOS 音乐播放器(单一窗口,无系统装饰,半透明)
- 默认呈现一张紧凑卡片(封面 + 标题/艺术家 + 进度条),展开后可看曲目列表/同步歌词
- 扫描本地音频文件夹播放(MP3/FLAC/WAV/OGG/M4A/AAC)
- 自动从网络补全缺失封面(iTunes Search API)
- 支持 sidecar `.lrc` + 内嵌 ID3 USLT 歌词,带 `#hash` 文件名容错
- 顺序(末尾循环)/ 随机(避免立即重复)/ 单曲循环 三种播放模式
- 持久化:曲库路径、音量、模式、封面形状、主题
- 圆形封面在播放时缓慢旋转(可在设置中切换形状)
- 两套主题:默认灰 / 白红
- 全局空格切换播放/暂停
- 自动从 `Title-Artist.mp3` 命名提取元数据(无 ID3 时)

### 非目标

- 不做在线音乐流(网易云 / QQ / Spotify 等)
- 不做歌单管理、收藏、评分
- 不做均衡器、音效处理
- 不做歌词在线检索 / 翻译
- 不做迷你模式以外的多窗口形态
- 不接续上次播放进度(每次启动从第一首歌开始)

## 2. 技术栈

| 层 | 选择 | 关键参数 |
|---|---|---|
| Shell | Tauri 2 | `decorations: false`, `transparent: true`, `macOSPrivateApi: true`,Cargo `macos-private-api` feature |
| 前端 | React 18 + TS + Vite | 单页 |
| 状态 | zustand 4 | 全局 store |
| 音频 | rodio 0.22 | `default-features = false, features = ["playback", "symphonia-all"]` |
| 元数据 | lofty 0.21 | tags + cover + USLT |
| 文件遍历 | walkdir 2 | 递归 |
| HTTP | reqwest 0.12 | `default-features = false, features = ["json", "rustls-tls"]` |
| 序列化 | serde / serde_json | `Config` 字段都用 `#[serde(default)]` 兼容旧文件 |
| 锁 | parking_lot | 比 std::sync 简洁 |
| 哈希 | sha2 + hex | 文件路径前 8 字节作 track id |
| macOS FFI | objc2 + objc2-app-kit + objc2-foundation | dev 模式 dock 图标 |

## 3. 整体架构

```
┌───────────────────────────────────────────────────────────────┐
│ 浮动透明窗口 (动态 400 × {100 | 580 | 640})                     │
│   ┌─────────────────────────────────────────────────────────┐ │
│   │ React UI                                                │ │
│   │  ╭───── now-playing card ───────────────────╮  ⚙ 设置弹窗 │ │
│   │  │ [cover]  Title - Artist            ⚙     │           │ │
│   │  │          ─●────────  0:23 / 3:30   ▾     │           │ │
│   │  ╰─────────────────────────────────────────╯           │ │
│   │  (展开时,以下作为同一卡片的下半部分:)                    │ │
│   │  ╭ Tabs: 列表 | 歌词 ───────────────────────╮          │ │
│   │  │ Playlist 或 Lyrics                       │          │ │
│   │  ╰─────────────────────────────────────────╯          │ │
│   └─────────────────────────────────────────────────────────┘ │
│       │ Tauri command / event                                 │
│   ┌───▼─────────────────────────────────────────────────────┐ │
│   │ Rust core                                              │ │
│   │  scanner · player · lyrics · cover_fetch · config      │ │
│   └────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────┘
```

**关键边界:**

- 前端只持有 DTO + 歌词 + 状态,不接触文件系统路径
- Rust 持有真实路径、音频流、Sink、磁盘缓存
- 进度由 Rust 端 100ms tick 任务通过 `app.emit("progress", PlaybackState)` 主动推送
- 切歌通过 `track_changed` 事件
- 窗口尺寸切换在前端用 `requestAnimationFrame` + `setSize` 做出 240ms ease-out 滑动动画
- macOS dock 图标在 setup hook 里通过 `NSApplication.setApplicationIconImage:` 设置

## 4. 模块与职责

### 4.1 Rust 端 (`src-tauri/src/`)

| 模块 | 职责 | 主要对外接口 |
|---|---|---|
| `types.rs` | 共享类型 | `Track / TrackDto / Mode / Lyrics / LyricLine / Config / CoverShape / Theme / PlaybackState / ScanResult` + `track_id_for_path` |
| `config.rs` | 持久化 | `read_from(path) -> Config`(损坏自愈),`write_to(path, &Config)` |
| `lyrics.rs` | LRC 解析 + 加载 | `parse_lrc(text) -> Lyrics`,`load_sidecar_lrc(audio)`(带 `#hash` 容错),`load_embedded_lyrics(audio)`,`load_lyrics(audio) -> Option<Lyrics>` |
| `scanner.rs` | 目录扫描 | `scan(folder) -> ScanOutput`;walkdir + 扩展名过滤 + lofty 元数据 + base64 封面;**文件名兜底**:无 ID3 时按 `Title-Artist` 解析,带 `#hash` 后缀剥离 |
| `player.rs` | 播放引擎 | `Player` 持有 `MixerDeviceSink`(`SendDevice` 包装,unsafe Send/Sync)+ `RodioPlayer`(rodio 0.22 的 sink);命令式 API:`play_track_id / toggle_pause / next / prev / seek / set_volume / set_mode / set_queue / snapshot / compute_next_index`;`compute_next_index` 在 Sequential/RepeatOne 末尾会环绕回 0 |
| `cover_fetch.rs` | 封面查询 | `fetch_cover(title, artist, cache_dir, track_id)`;先查 disk cache,否则查 iTunes Search,把 600×600 JPEG 缓存到磁盘并返回 data URL |
| `lib.rs` | Tauri 装配 | 注册 11 条 commands、setup hook 启动 100ms tick 任务 + 自动切歌、`track_changed` 事件、macOS dock icon |

### 4.2 关键数据结构

```rust
pub enum Mode { Sequential, Random, RepeatOne }
pub enum CoverShape { Circle, Square }
pub enum Theme { Default, Whitered }

pub struct Track {
    pub id: String,                  // sha256(path)[..8] hex
    pub path: PathBuf,               // Rust-only
    pub title: String,
    pub artist: String,
    pub album: String,
    pub duration_ms: u64,
    pub cover_data_url: Option<String>,
    pub lyrics: Option<Lyrics>,
}

pub enum Lyrics {
    Synced(Vec<LyricLine>),
    Plain(String),
}

pub struct PlaybackState {
    pub track_id: Option<String>,
    pub position_ms: u64,
    pub is_playing: bool,
}

pub struct Config {
    pub folder: Option<String>,
    pub volume: f32,
    pub mode: Mode,
    #[serde(default)] pub cover_shape: CoverShape,
    #[serde(default)] pub theme: Theme,
}
```

### 4.3 Tauri commands

| 命令 | 参数 | 返回 | 说明 |
|---|---|---|---|
| `load_config` | — | `Config` | 启动时读 |
| `save_config` | `cfg: Config` | `Result<(), String>` | 任何字段变化都写一份完整 Config(全字段) |
| `scan_folder` | `folder: String` | `ScanResult` | 同步阻塞扫描;触发 player.set_queue |
| `play` | `track_id: String` | `Result<(), String>` | 通过 id 找到 queue 索引并播放 |
| `toggle_pause` | — | — | 暂停 / 恢复(无 sink 时 no-op) |
| `next_track` | — | `Result<(), String>` | 按 mode 决定下一首,sequential 末尾 wrap 到 0 |
| `prev_track` | — | `Result<(), String>` | 当前 - 1(夹到 0) |
| `seek` | `position_ms: u64` | `Result<(), String>` | 优先 `Sink::try_seek`;失败回退到 rebuild + skip_duration |
| `set_volume` | `volume: f32` | — | 实时 + 200ms debounce 写 config |
| `set_mode` | `mode: Mode` | — | 同上 |
| `fetch_cover` | `track_id, title, artist` | `Result<Option<String>, String>` | 异步;前端在封面缺失时调用 |

### 4.4 前端组件 (`src/`)

| 文件 | 职责 |
|---|---|
| `App.tsx` | 顶层布局;启动时 `loadConfig + scanFolder + listen("progress"/"track_changed")`;窗口动画 useEffect 用 RAF 在 240ms ease-out 内 setSize;`panelMounted` 比 `expanded` 多保留 260ms,让收起动画期间面板内容不消失;**全局空格切换播放/暂停**(焦点不在 button/input 时) |
| `store.ts` | zustand;`tracks / currentTrackId / position / isPlaying / volume / mode / view / folder / skipped / expanded / settingsOpen / coverShape / theme` + 一组 `setX` |
| `lib/types.ts` | TS 镜像 Rust 类型 |
| `lib/api.ts` | `invoke()` 包装,11 条 |
| `components/NowPlaying.tsx` | 卡片容器;封面按钮(首次点击 = play,后续 = togglePause)+ 元信息列(title-line + Progress);空状态显示「无歌曲」+ 0:00 进度条 |
| `components/Progress.tsx` | 进度条;拖动只更新本地 `seekingValue`,松手发 seek + 保持 350ms 不被推送覆盖,避免闪烁 |
| `components/Settings.tsx` | 齿轮下拉:选择曲库 / 音量(喇叭+声波图标 + 200ms debounce 写 config)/ 播放顺序 3 选 1 / 封面形状 2 选 1 / 主题 2 选 1 / 退出;选项是横排胶囊按钮(`.settings-pill`);menuOpen 状态镜像到 store 让 App 调整窗口;**点击选项不关闭菜单**,只有齿轮再点、点菜单外、动作类项才关 |
| `components/Tabs.tsx` | 列表 / 歌词 切换 |
| `components/Playlist.tsx` | 虚拟滚动(react-window);点击行 → setCurrent + api.play |
| `components/Lyrics.tsx` | 二分查找当前行 → 平滑 scrollIntoView;Synced/Plain/None 三态 |

## 5. 数据流与关键交互

### 5.1 启动序列

1. App.tsx mount → `loadConfig`
2. 应用 `volume / mode / cover_shape / theme` 到 store
3. `cfg.folder` 缺失 → 用 `homeDir() + "Music"` 作为默认值
4. `scan_folder` 同步返回 `ScanResult` → setTracks + setSkipped + 自动 `setCurrentTrackId(tracks[0].id)`(只设元数据,不开始播放)
5. 监听 `progress` 和 `track_changed` 事件
6. 首次启动写一次 config

### 5.2 播放生命周期

```
首次点击封面(currentTrackId 已设, isPlaying=false, position=0)
 → handleCoverClick: api.play(track.id)
   → Rust: 找到 queue 索引 → build_sink(file open + symphonia decode) → 替换旧 sink → sink.play()
     → tick 100ms 推 progress → 前端 store.setPosition / setIsPlaying

再次点封面 (or 按空格)
 → handleCoverClick: api.togglePause()  (因为 position > 0 || isPlaying)
   → Rust: sink.pause() 或 sink.play();paused 时记录 paused_position_ms,resume 时重置 started_at

歌曲结束
 → tick 检测 sink.empty() && current_index.is_some() → snapshot.is_finished = true
 → 同一 tick 算 next_index(advance_on_finish=true)→ play_track_id(next.id)
   → emit("track_changed", id) → 前端 setCurrentTrackId

到末尾的顺序模式
 → compute_next_index 在 Sequential 末尾返回 (i+1) % len = 0,即回到第一首
```

### 5.3 Seek

```
拖动滑杆
 → onChange 不停 setSeekingValue(local)
 → 渲染时 sliderValue = seekingValue ?? Math.min(position, duration)

松手 (mouseUp / touchEnd / keyUp)
 → commitSeek:
     api.seek(target);
     setPosition(target);            // 乐观更新
     setTimeout(() => setSeekingValue(null), 350)  // 撑过 IPC + tick 收敛
   → Rust: 优先 sink.try_seek(target) (symphonia seek table)
     失败 → build_sink(skip_duration=target) (慢路径,但保证可用)
```

### 5.4 自动切歌(`compute_next_index`)

| Mode | advance_on_finish=true | 手动 next |
|---|---|---|
| Sequential | (cur + 1) % len | 同左 |
| RepeatOne | 同一首(cur) | (cur + 1) % len |
| Random | 随机选,排除当前和上一次 random,len=1 时返回 0 | 同左 |

### 5.5 窗口三态 + 滑动动画

| 状态 | 高度 | 触发 | 类名 |
|---|---|---|---|
| 紧凑 | 100 | 默认 | `app.compact` |
| 设置 | 580 | 齿轮菜单展开 | (内部仍 compact) |
| 展开 | 640 | chevron | `app.expanded`(实际跟随 panelMounted)|

App.tsx:

```ts
const target = expanded ? EXPANDED_SIZE : settingsOpen ? SETTINGS_SIZE : COMPACT_SIZE;
// 用 requestAnimationFrame 在 240ms 内做 ease-out 把 setSize 推到 target
// panelMounted 比 expanded 多保留 260ms,这样:
//   - 展开:面板立即挂载并随窗口放大露出
//   - 收起:窗口先缩,面板内容仍渲染,直到动画结束才卸载
//   - .app.expanded 类绑定到 panelMounted,保证整个收起过程都是"无缝合并卡片"样式
```

### 5.6 封面与旋转

- 圆形模式:`.cover-circle` 应用 `border-radius: 50%`,并以 `@keyframes cover-spin` 作 20 秒一圈线性旋转
- `animation-play-state: paused`(默认)/ `running`(`.playing` 类)实现暂停时停在当前角度
- 占位封面(无 cover_data_url 且 fetchCover 失败)显示一张 SVG 黑胶 — 黑色碟身、三圈凹槽、红色中心、左上反光高光
- 真实封面优先:`track.cover_data_url ?? fetchedCover` — 来自 lofty 解码或 iTunes 缓存
- 封面是按钮:点击 = play / togglePause(取决于是否首次);hover/playing 显示中央播放/暂停图标

### 5.7 设置菜单

```
选择曲库…             (action — full-width 行)
─────
音量
🔊 ────●────         (slider with speaker+wave icon, 200ms debounce save)
─────
播放顺序
[顺序] [随机] [单曲循环]   (pill row, 激活态填红边)
─────
封面形状
[圆形] [方形]
─────
主题
[默认灰] [白红]
─────
退出                   (action — full-width 行,关闭窗口)
```

- 选项是 `.settings-pill`(全圆角胶囊),激活态 `border-color: var(--accent)` + `color: var(--hi)` + 加粗
- 选项点击不关闭菜单(连续比较时方便),只有动作类(选择曲库 / 退出)和点击外部 / 再点齿轮才关
- (条件)已忽略 N 个文件 — 仅当 scanResult.skipped > 0 时显示

### 5.8 持久化

- 路径:`app_config_dir()/config.json`
- 序列化用 `serde_json::to_string_pretty`
- 读时:`Ok(parsed) => parsed`,`Err(_) => default + 立刻覆写`(自愈)
- 字段 `cover_shape` 和 `theme` 用 `#[serde(default)]` 标注,旧 config 缺字段时自动落 enum 的 Default

### 5.9 主题

- `--card-bg` / `--card-border` / `--card-shadow` / `--row-hover` / `--row-active-bg` / `--hi` / `--accent` / `--track` / `--muted` 都是 CSS 变量
- `:root` 持有默认主题(色调跟随 `light-dark()` 自适应)
- `.app.theme-whitered` 重写以上变量(白底 + 红色高亮 + 红边激活)
- 切换瞬间生效,无 flash

### 5.10 macOS dock 图标

- `setup` hook 中 `set_macos_dock_icon()` 通过 `objc2-app-kit` 调 `NSApplication.setApplicationIconImage:`
- 图标 PNG 用 `include_bytes!("../icons/icon.png")` 编译进二进制
- 解决了 dev 模式下未打包 .app 时 dock 显示通用图标的问题

## 6. 错误处理

| 场景 | 行为 |
|---|---|
| 损坏 / 不支持的音频 | scanner 跳过并计入 `skipped`;UI 在设置菜单顶部显示「已忽略 N 个文件」 |
| 文件夹无音频 | 列表显示「未找到音频文件」 |
| ID3 完全空 | 文件名按 `Title-Artist` 解析(剥 `#hash`),否则全 stem 作 title + artist=Unknown |
| Decoder panic(rodio 0.19 m4a 触发过) | `catch_unwind` 兜住,返回 Err;升级到 0.22 后已不会触发,留作安全网 |
| LRC 解析过半失败 | 降级为 `Lyrics::Plain(原文)` |
| 内嵌封面缺失 | 走 `fetch_cover`(iTunes),都拿不到则显示黑胶占位 |
| iTunes 网络失败 | catch 后保持占位图,无 toast |
| Config 损坏 | 读到 default 并立刻写回 |
| Seek 越界 | clamp 到 `duration_ms - 100ms`,触发自动切歌 |
| 音频设备缺失 | `Player::new` 返回 Err,setup hook 直接 panic(未来可降级到 mock 模式) |

## 7. 测试策略

### Rust 单元测试 (`cargo test --lib`)

- `types`: track_id 确定性 / 不同路径生成不同 id / Config 默认值 / Mode lowercase 序列化 / TrackDto 不含 path
- `config`: 缺失文件返回默认 / 损坏文件返回默认并被覆写 / 嵌套目录写入 + 往返(包括 cover_shape 和 theme 字段)
- `lyrics`: 标准 LRC / 元数据头 / 多时间戳行 / 乱序排序 / BOM / 多数损坏降级 Plain / 空输入 / sidecar 大小写不敏感 / `#hash` 后缀容错 / 精确匹配优先级 / 无 sidecar+无内嵌返回 None
- `scanner`: 扩展名过滤 / 空目录 / 元数据缺失时回退到文件名 / 递归子目录 / `Title-Artist` 拆分 / `#hash` 剥离 / 无 hyphen 不拆分
- `player`: Sequential 末尾环绕 / RepeatOne 重播 / Random 不立即重复当前 / 音量夹紧

约 31 个 Rust 单元测试。

### 前端测试 (`npm test`)

- `store`: setTracks 替换 / setView 切换 / activeLyricIndex 二分查找
- `api`: invoke 路由 + Config / ScanResult 解析
- `NowPlaying`: 空状态(无歌曲 + 0:00)+ 元信息渲染 + 首次点击 → api.play + 已开始时点击 → togglePause
- `Playlist`: 空状态 + 行点击 → api.play
- `Lyrics`: 空状态 + Synced 高亮 active class + Plain 单段渲染

约 14 个前端单元测试。

### 手动验收清单

- 曲库 ~/Music 自动扫描 → 卡片显示第一首歌元数据
- 点封面开始播放;再点暂停;继续;切歌
- 空格键能切换播放/暂停
- 拖动进度条 → 松手秒到目标位置;中间不闪
- 设置 → 选其他曲库 → 列表更新
- 设置 → 调音量 → 播放声变 → 重启后保留
- 设置 → 切播放模式 → 播完一首验证下一首选择;Sequential 末尾会回首
- 设置 → 切方形/圆形 → 播放时旋转/不旋转
- 设置 → 切默认/白红主题 → 配色全局切换
- 没封面的歌:首次播放后等 1-2 秒看到从 iTunes 拉到的封面;无网络/查不到时保持黑胶
- 同名 .lrc(`Song-Artist.lrc` 配 `Song-Artist#hash.mp3`)→ 切到歌词 tab 看到同步高亮
- 无 ID3 的 `Title-Artist.mp3` → 列表里也能正确显示标题和艺术家
- 拖动卡片 → 窗口跟着移动到桌面任意位置
- chevron 展开/收起 → 240ms 平滑动画 + 卡片与 panel 合为一个表面
- Cmd+Q / 设置→退出 → 进程清退
- macOS dock 图标显示黑胶艺术(不再是通用图标)

## 8. 状态

本文档反映 v0.1.0 实际实现。原始的早期设计已合并入此文。
