# TingMusic Android — 设计文档

- 日期:2026-06-16
- 状态:已与用户对齐(brainstorming 阶段),待 spec review
- 关联:现有 macOS 版设计 `docs/superpowers/specs/2026-05-09-tingmusic-design.md`

## 1. 目标

为 TingMusic 增加一个**安卓版本**,核心诉求是:Mac 与安卓在**同一局域网**下,安卓能**扫描发现** Mac 版,并把 Mac 上的曲库(**音乐文件 + 歌词文件**)**单向镜像同步**到手机本地,之后可离线播放。

安卓版本身是一个**功能完整、体验地道的手机音乐播放器**(后台播放、锁屏/通知栏控制、音频焦点),不是一个"只为看同步效果"的壳子。

## 2. 决策记录(已与用户确认)

| 决策点 | 结论 | 理由 |
|---|---|---|
| 安卓技术栈 | **原生 Kotlin + Jetpack Compose + Media3/ExoPlayer** | 手机音乐播放器强依赖后台播放/锁屏/通知栏/音频焦点,原生是唯一能做对的路子。代价是 UI/逻辑用 Kotlin 重写,但纯逻辑(LRC 解析、文件名兜底)很小,易移植。 |
| 同步语义 | **单向镜像**(Mac 为准) | 手机曲库 = Mac 曲库的镜像:新增下载、Mac 删了手机也删、改了重拉。靠 manifest 做增量。 |
| 元数据来源 | **安卓本地解析**(方案 A) | Mac 只传文件;安卓用系统 `MediaMetadataRetriever` 提取元数据/内嵌封面,`.lrc` 用移植的 Kotlin 解析器。镜像目录是纯文件,自洽、健壮、协议简单。 |
| 发现机制 | **mDNS / NSD**(`_tingmusic._tcp`)+ 手填 IP 兜底 | 零配置"打开就扫到",家庭局域网最稳。 |
| 信任/配对 | **局域网全开放**(v1 不做验证) | 用户选择,按可信家庭网络处理。做成可开关,预留将来加 PIN 的位置。 |
| 播放器功能范围 | **核心播放器** | 列表 + 播放页 + 同步歌词 + 三种模式 + 通知栏/锁屏 + 内嵌封面/黑胶占位。**v2 再加**:iTunes 联网拉封面、主题切换、圆/方封面切换。 |
| 镜像存储位置 | `getExternalFilesDir("Music")` | App 专属外部存储,现代安卓**免运行时权限**,卸载即清,镜像删除不会误伤手机里别的音乐。 |
| 仓库布局 | monorepo,新增 `android/` | Mac 与安卓共享同一套同步协议,同一产品,放一个仓库便于协同演进。 |

## 3. 总体架构

```
              同一局域网 WiFi
┌──────────────────────────┐          ┌────────────────────────────────┐
│  Mac: TingMusic.app       │          │  Android: TingMusic(新建)       │
│  (现有 Tauri 应用 + 新增)  │          │  Kotlin + Compose + Media3      │
│                           │          │                                │
│  ┌─────────────────────┐  │   mDNS   │  ┌──────────────────────────┐  │
│  │ 同步服务 (Rust)       │◄─广播───────│  NSD 发现 _tingmusic._tcp   │  │
│  │  axum HTTP server    │  │          │  └──────────────────────────┘  │
│  │   GET /manifest      │◄─HTTP GET───│  SyncEngine: 拉清单 + diff      │
│  │   GET /file?path=..  │◄─HTTP GET───│             + 下载 + 镜像删除    │
│  └─────────────────────┘  │          │              │                  │
│         ▲ 读曲库目录       │          │              ▼                  │
│   配置的曲库 (源, 如 ~/Music)│          │  本地索引 (Room) → 播放器 UI     │
└──────────────────────────┘          │              │                  │
                                       │              ▼                  │
                                       │  Media3 播放 + 通知栏/锁屏       │
                                       └────────────────────────────────┘
```

数据流向严格单向:**Mac(源)→ Android(镜像)**。安卓永远不向 Mac 写。

## 4. 同步协议

### 4.1 服务发现

- Mac 端用 `mdns-sd` 注册服务类型 `_tingmusic._tcp`,实例名取主机名/曲库名。
- TXT 记录:`v=<协议版本>`、`port=<端口>`、`lib=<曲库显示名>`。
- 安卓端用 `NsdManager` 浏览 `_tingmusic._tcp`,解析出 `host:port`。
- 兜底:UI 提供"手动输入 IP/端口"入口,绕过 mDNS(应对 multicast 被压制的网络)。

### 4.2 HTTP 接口

Mac 端 `axum` 起本地 HTTP 服务,默认端口 `8737`(被占用则向上顺延,实际端口写进 mDNS TXT)。

| 接口 | 方法 | 返回 |
|---|---|---|
| `/manifest` | GET | `application/json`,见 4.3 |
| `/file?path=<相对路径>` | GET | 文件原始字节,带 `Content-Length`,支持 `Range`(断点续传)。由 `tower-http::ServeDir` 提供,自带 Range 与**防目录穿越**。 |

`/file` 的 `path` 是相对曲库根的相对路径,经过 URL 编码;`ServeDir` 限定根目录,拒绝 `..` 穿越。

### 4.3 Manifest 格式

```json
{
  "version": 1,
  "library_name": "Music",
  "files": [
    { "path": "Anime/Butter Fly-和田光司.mp3", "size": 8123456, "mtime": 1715000000 },
    { "path": "Anime/Butter Fly-和田光司.lrc", "size": 2310, "mtime": 1715000001 }
  ]
}
```

- `files` 只列**音频文件**(`mp3/flac/wav/ogg/m4a/aac`,与 Mac scanner 的 `SUPPORTED_EXTS` 一致)**和 `.lrc` 文件**。
- `path`:相对曲库根,使用 `/` 分隔,UTF-8。
- `size`:字节数。`mtime`:Unix 秒。两者共同作为变更检测的版本键。
- `version`:协议版本,便于将来不兼容升级时双方协商。

### 4.4 同步算法(安卓端,单向镜像)

输入:本地保存的上次同步状态 `LocalState = Map<path, (size, mtime)>`(初次为空)。

1. 发现 Mac(NSD 或手填)→ `GET /manifest` 得到 `remote: Map<path, (size, mtime)>`。
2. 计算差异:
   - **下载集** = `remote` 中 `path` 不在 `LocalState`,或 `(size,mtime)` 与 `LocalState` 不同的项。
   - **删除集** = `LocalState` 中 `path` 不在 `remote` 的项。
3. 对下载集:`GET /file?path=...` 写入 `…/files/Music/<path>`(按需建子目录),支持 `Range` 续传。
4. 对删除集:删除本地镜像文件;同时清空目录树里因此变空的目录。
5. **重新索引**:仅对**变化过的音频文件**用 `MediaMetadataRetriever` 解析元数据/内嵌封面,关联的 `.lrc` 用 `LrcParser` 解析,写入 Room;删除集对应记录从 Room 移除。
6. 用最新 `remote` 覆盖 `LocalState` 持久化(DataStore/Room),通知 UI 刷新。

变更检测用 **size + mtime**(便宜、够用)。下载失败的单个文件**跳过并计数**,不更新该文件的 `LocalState`(下次自然重试),不中断整体。

## 5. 安卓 App 设计

技术:Kotlin、Jetpack Compose、Media3(ExoPlayer + MediaSession)、Room、DataStore、OkHttp、Coroutines/Flow。单 Gradle module(`app`),按包组织。

### 5.1 包结构与职责

```
com.tingmusic/
├── data/sync/
│   ├── Discovery.kt        NsdManager 封装,浏览 _tingmusic._tcp → Flow<ServerInfo>
│   ├── SyncClient.kt       OkHttp:getManifest()、downloadFile(path, range)
│   ├── SyncEngine.kt       diff + 下载 + 镜像删除 + 触发重索引,emit 进度
│   └── Manifest.kt         数据类(version/library_name/files)
├── data/library/
│   ├── TrackEntity.kt      Room 实体:id(path 哈希)/path/title/artist/album/durationMs/hasEmbeddedCover/lrcPath
│   ├── LibraryDao.kt       查询/增删
│   ├── LibraryRepository.kt 暴露 Flow<List<Track>>
│   ├── Indexer.kt          MediaMetadataRetriever 提取元数据 + 内嵌封面字节
│   └── LrcParser.kt        从 Rust lyrics.rs 移植:[mm:ss.xx] 解析、多时间戳、BOM、坏行降级
├── data/prefs/
│   └── Prefs.kt            DataStore:volume / mode / lastHost / lyricsView
├── playback/
│   ├── PlaybackService.kt  Media3 MediaSessionService,ExoPlayer 实例,通知/锁屏
│   └── PlayMode.kt         顺序/随机/单曲 ↔ Media3 repeat/shuffle 映射
└── ui/
    ├── LibraryScreen.kt    曲库列表(LazyColumn,点项即播)
    ├── NowPlayingScreen.kt 封面 + 标题/艺术家 + 进度条 + 控制按钮 + 模式切换
    ├── LyricsView.kt       同步歌词,二分查找高亮 + 居中平滑滚动
    ├── SyncSheet.kt        发现服务器列表/手填 IP + 同步进度
    ├── components/         CoverArt(内嵌图或黑胶占位)、ProgressBar 等
    └── theme/              默认灰调主题(沿用 Mac 配色锚点:红色 #d33a31)
```

### 5.2 播放(Media3)

- `PlaybackService` 继承 `MediaSessionService`,持有一个 `ExoPlayer`,对外暴露 `MediaSession`,系统自动给通知栏 + 锁屏 + 蓝牙/耳机控制。
- 播放队列 = 当前曲库顺序;播 `file://` URI(镜像目录文件)。
- **播放模式映射**:
  - 顺序(到末尾回首)= `repeatMode = REPEAT_MODE_ALL`,`shuffleModeEnabled = false`。
  - 随机 = `shuffleModeEnabled = true`,`repeatMode = REPEAT_MODE_ALL`。
  - 单曲循环 = `repeatMode = REPEAT_MODE_ONE`。
- 音频焦点、来电暂停、拔耳机暂停由 Media3 默认处理(`setAudioAttributes(handleAudioFocus = true)`)。

### 5.3 歌词

- 仅 `.lrc` sidecar(覆盖网易云/QQ 缓存命名差异这一主场景);内嵌 USLT 歌词 v1 不处理(留 v2,需 Mac 侧额外暴露)。
- `.lrc` 与音频同名匹配,容忍 `Song#hash.mp3` ↔ `Song.lrc` 的 `#hash` 后缀差异(移植 Mac `strip_hash_suffix` 逻辑)。
- 播放页协程以 ~100ms 轮询 `player.currentPosition`,二分查找当前行(移植 `activeLyricIndex`),`LazyColumn` 用 `animateScrollToItem` 居中。

### 5.4 封面

- 内嵌封面:`MediaMetadataRetriever.embeddedPicture` 取字节,Coil 显示。
- 无封面:**黑胶占位**(Compose 画:深色圆盘 + 红色中心标签 #d33a31 + 唱针孔),播放时缓慢旋转(20s 一圈),暂停停在当前角度——沿用 Mac 版语义。
- iTunes 联网拉封面:**v2**,不在本期。

### 5.5 存储与索引

- 镜像文件:`context.getExternalFilesDir("Music")` 下按相对路径落盘。
- Room 缓存元数据,键为 `path`;`Indexer` 只对**变化文件**重新解析,避免每次启动全量 `MediaMetadataRetriever`(逐文件打开较慢)。
- 配置(音量/模式/上次主机)用 DataStore。

### 5.6 权限与网络

- `AndroidManifest`:`INTERNET`、`ACCESS_NETWORK_STATE`、`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`(Media3 通知)、`POST_NOTIFICATIONS`(Android 13+ 通知运行时权限)。
- 镜像目录用 App 专属外部存储,**无需** `READ/WRITE_EXTERNAL_STORAGE`。
- 局域网是明文 HTTP:`network_security_config.xml` 仅对本地/私网地址放行 cleartext,其余仍禁明文。

## 6. Mac 端改动(现有 Tauri 应用)

改动克制,不触碰现有播放/扫描核心逻辑。

- 新增 `src-tauri/src/sync_server.rs`:
  - `axum` Router:`/manifest`(自定义 handler,遍历曲库目录,过滤音频 + `.lrc`,产出 4.3 的 JSON)+ `/file`(`tower-http::services::ServeDir` 根在曲库目录)。
  - 监听 `0.0.0.0:8737`(占用则顺延);返回实际端口给上层。
- 新增 `src-tauri/src/mdns_advertise.rs`:`mdns-sd` 注册 `_tingmusic._tcp`,TXT 带 `v/port/lib`。
- `src-tauri/src/types.rs`:`Config` 加字段 `#[serde(default = "default_true")] lan_sync: bool`(默认开)。
- `src-tauri/src/lib.rs`:`setup` 里当 `lan_sync` 为真时启动 server + 广播;新增 Tauri command `set_lan_sync(enabled)` 与 `lan_sync_status()`(返回是否开启 + 本机 IP:端口)用于 UI 显示与开关。
- 前端 `src/components/Settings.tsx`:齿轮菜单加"局域网同步"开关 + 显示 `IP:端口`;`src/lib/api.ts` 加对应包装。
- `src-tauri/Cargo.toml`:新增 `axum`、`tower-http`(features: `fs`)、`mdns-sd`、`local-ip-address`(取本机局域网 IP 展示)。均纯 Rust,不破坏跨平台编译。

## 7. 错误处理

- **Mac**:端口被占自动顺延;HTTP server bind 失败在 UI 提示且不 crash 主应用(同步是可选能力);`/file` 路径穿越由 ServeDir 拦截;曲库目录不存在时 `/manifest` 返回空 `files`。
- **安卓同步**:单文件下载失败 → 跳过 + 计数,结尾汇总("同步完成,3 个文件失败");网络中断 → 已成功文件保留,下次 diff 自然续传;mDNS 发现超时(如 5s 无结果)→ 引导手填 IP;manifest 解析失败 → 提示并中止本次,不动本地镜像。
- **播放**:文件缺失/解码失败 → 跳下一首 + toast 提示。

## 8. 测试策略

- **Rust(Mac)**:
  - manifest 构建:相对路径正确、只含音频 + `.lrc`、子目录递归、空目录返回空。
  - 路径安全:`/file?path=../..` 被拒(ServeDir 行为,加一条集成测试覆盖)。
- **Kotlin(安卓,JVM 单测,不依赖设备)**:
  - `LrcParser`:照搬 Mac `lyrics.rs` 的用例(基础同步、metadata 头、多时间戳、乱序排序、BOM、坏行降级、空输入)。
  - `SyncEngine.diff`:给定 `LocalState` + `manifest` → 正确的下载集/删除集;含 size 变化、mtime 变化、纯新增、纯删除。
  - 活动歌词二分查找:与 store.ts `activeLyricIndex` 同样的边界用例。
- **真机集成**(连接设备 `c19636e1`):Gradle 出 debug APK → `adb install -r` → 对运行中的 Mac 跑一次真同步 → 验证列表出现、点歌播放、歌词滚动、通知栏/锁屏控制、删除 Mac 文件后再同步手机随之删除。

## 9. 仓库布局

```
TingMusic/
├── src/                         现有 Mac 前端
├── src-tauri/                   现有 Mac 后端(+ sync_server.rs, mdns_advertise.rs)
├── android/                     新增 Kotlin/Gradle 工程
│   ├── settings.gradle(.kts)
│   ├── build.gradle(.kts)
│   └── app/
│       ├── build.gradle(.kts)
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── res/xml/network_security_config.xml
│           └── java/com/tingmusic/...
└── docs/superpowers/specs/2026-06-16-tingmusic-android-design.md
```

## 10. 前置依赖与环境

- **构建工具链**(实现第一步检查):Android SDK(平台 + build-tools)、JDK 17+、Gradle(或 Android Studio 命令行工具);缺失时给出 `brew install` 等指引由用户执行。
- **真机**:测试机为小米/HyperOS(`marble`)。HyperOS 对后台服务与 multicast 较激进;v1 同步在**前台**进行(有进度条),不受后台压制影响。播放服务为前台 Media 服务,正常存活。

## 11. 明确不做(v1 范围外)

- iTunes 联网拉封面(v2)。
- 主题切换、圆/方封面切换(v1 固定默认灰调 + 黑胶占位)。
- 内嵌 USLT 歌词同步(v1 只读 `.lrc` sidecar)。
- 安卓 → Mac 反向同步 / 双向同步。
- 配对验证(PIN 等):v1 局域网开放,但接口与开关预留扩展位。
- WorkManager 后台/定时同步(v1 前台手动触发)。

## 12. 里程碑(供 writing-plans 细化)

1. **Mac 同步服务**:`sync_server.rs` + `mdns_advertise.rs` + config 字段 + Settings 开关与 IP 显示 + Rust 测试。可用 `curl` 验证 `/manifest`、`/file`。
2. **安卓骨架**:`android/` Gradle 工程、Manifest、网络安全配置、空 Compose 壳能跑起来、`adb install` 通。
3. **安卓同步引擎**:Discovery + SyncClient + SyncEngine + Room 索引 + LrcParser 移植 + JVM 单测;跑通对 Mac 的真同步,文件落到镜像目录。
4. **安卓播放器**:Media3 PlaybackService + 列表 + 播放页 + 通知/锁屏 + 三模式。
5. **歌词与封面**:LyricsView 同步滚动 + 黑胶占位封面 + 内嵌封面。
6. **真机端到端联调**:对运行中的 Mac 全流程验证(同步→播放→歌词→镜像删除)。
