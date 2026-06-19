# TingMusic Android — 红黑音乐播放器 UI 重做 设计文档

- 日期:2026-06-19
- 状态:已与用户对齐(adaptation 决策已确认),待 spec review
- 来源:用户在 Claude Design 设计的 `红黑音乐播放器.dc.html`(项目 `a17fe6f9-ea62-4c03-8d2c-95c48ad11d0f`)
- 关联:取代 v2 的网易云风 UI(`2026-06-18-tingmusic-android-v2-design.md`)的视觉与导航结构;播放/同步/曲库/歌词的功能层不变。

## 1. 目标

把安卓 UI 整体换成用户设计的**红黑配色**音乐播放器:纯黑底 + 红色强调,四个面:歌曲列表、播放器(凹槽黑胶 + 精致唱臂)、歌词、左侧设置抽屉。功能(Media3 播放、局域网同步、曲库索引、LRC 歌词)全部复用,只重做视觉层与三屏导航。

## 2. 落地决策(已确认)

| 决策 | 结论 |
|---|---|
| 主题 | **单一红黑**,去掉双主题切换(删 `AppTheme`/`SettingsStore` 主题部分/主题开关/`SyncViewModel.theme`)。 |
| 抽屉内容 | 红黑样式 + **真实功能**:局域网同步(扫描 Mac / 手填 IP / 同步)+ 关于。**去掉**示意项(播放音质/定时关闭)。 |
| 装饰元素 | 保留视觉、能接真的接真:顶部搜索框作**装饰**(暂不可点)、"播放全部"**可用**(从第 0 首播)、曲数显示**真实**、列表标题用"我的曲库"。去掉 ⋮ 过载菜单(纯装饰,可省)。 |

## 3. 设计 token(从设计精确抽取)

- **底色**:页面 `#0a0a0a`;mini 条 `#0d0d0d`;抽屉 `#0f0f0f`;搜索框 `#161616`。
- **文字**:主 `#F5F5F5`;次 `rgba(255,255,255,0.40)`;弱 `rgba(255,255,255,0.28)`。
- **强调红**:`#E60026`;红光阴影 `rgba(230,0,38,0.42)`(大播放键)/ `0.35`(logo)。
- **分隔线**:`rgba(255,255,255,0.06)`。
- **字体**:系统 sans(安卓中文即 Noto Sans SC,无需打包)。
- **圆角**:搜索框 18、mini 封面 7、抽屉 logo 13、关于 logo 20。

Compose `ColorScheme`(单一,暗):`background=#0a0a0a, surface=#0d0d0d, primary=#E60026, onPrimary=#FFFFFF, onBackground=#F5F5F5, onSurface=#F5F5F5, onSurfaceVariant=#FFFFFF@0.40`。

## 4. 导航结构(三屏滑动 + 左抽屉)

顶层状态(MainActivity `remember`):`screen ∈ {LIST, PLAYER, LYRICS}`,`drawerOpen`(用 `ModalNavigationDrawer`)。
- **LIST**(基础层):列表页常驻;点歌曲行或 mini 条 → `screen=PLAYER`(从底部上滑覆盖)。
- **PLAYER**(上滑覆盖列表):点黑胶 → `screen=LYRICS`;顶部 ⌄ → 回 LIST。
- **LYRICS**(上滑覆盖播放器):点歌词区或 ⌄ → 回 PLAYER。
- 转场:`AnimatedVisibility` + `slideInVertically{it}`/`slideOutVertically{it}`(对应设计的 `translateY` 0.36s)。
- `BackHandler`:LYRICS→PLAYER;PLAYER→LIST;LIST 时不拦截。
- 抽屉:左侧 `ModalNavigationDrawer`,只在 LIST 顶栏 ☰ 可开。

## 5. 歌曲列表页 `PlaylistScreen`

布局(`Column`,纯黑底):
1. **顶栏**(高约 46):☰ 菜单图标(22,开抽屉)+ 搜索框(`weight1`,h34,bg `#161616`,r18,放大镜 15 + "搜索歌曲、歌手、专辑" 13/40%,**装饰不可点**)+ ⋮(省略或弱化)。
2. **标题块**(padding 10/18):"我的曲库" 24/700;"本地曲库 · {N} 首" 12/40%。
3. **播放全部 行**(padding,底部分隔线):红色圈播图标(24,描边 `#E60026` + 实心三角)+ "播放全部" 15/500 + "({N})" 13/38%,点击 `playAll`(从第 0 首);右侧排序图标(装饰)。
4. **曲目列表** `LazyColumn`:每行(minHeight 56,padding 10/16):
   - 左 22dp:当前曲且在播 → **红色均衡器**(3 根 2dp 宽、红 `#E60026`,高在 4↔14dp 间脉动,`barpulse` 0.9s,错相 0/.3/.6s);否则 → 两位序号 `%02d` 14/28%。
   - 中:标题 15(当前曲 `#E60026`,否则 `#F3F3F3`,单行省略)+ "{artist} · {album}" 12/38%。
   - 右:⋮(装饰)。
   - 点行 → `play(track)` + `screen=PLAYER`。
5. 底部留给 mini 条(见 §6)。空库时显示引导文案。

## 6. 迷你播放条 `MiniPlayer`(列表底部,仅当有当前曲)

`Row`(padding 8/12,顶部分隔线,bg `#0d0d0d`):
- 封面 42 圆角7(`CoverImage`,点 → PLAYER)。
- 标题 13.5/500 + 艺术家 11/40%(单行省略,点 → PLAYER)。
- **播放/暂停**:38dp 外环 = `conic-gradient(#E60026 {pct}%, rgba白.13 {pct}%)`(进度环),内 30dp `#0d0d0d` 圆 + 播放/暂停图标 16。点击 `togglePlayPause`。
- 队列图标(装饰)。

## 7. 播放器页 `PlayerScreen`(PLAYER 层)

`Column`,bg `#0a0a0a`:
1. **顶栏**(h50):⌄(回 LIST)+ 居中 标题14/500 + 艺术家11/40% + ⋮(装饰)。
2. **黑胶区**(`weight1`,居中,点 → LYRICS):
   - 唱片 250dp:凹槽纹理(同心环 `#191919`/`#0c0c0c` 交替)+ 径向 `#181818→#040404`,阴影 `0 20 50 rgba(0,0,0,.6)`;外加一层 conic 高光(rgba 白 0.11→0.02,可近似/省略)。
   - 中心封面 106dp 圆(`CoverImage`),环阴影 `0 0 0 7px #0a0a0a, 0 0 0 8px rgba白.08`;中心轴孔 11dp `#0a0a0a` 描边。
   - 播放时 18s/圈匀速旋转(`withFrameNanos` 累加,暂停冻结当前角度——沿用 v2 的实现)。
   - **唱臂**(容器 26×150,支点在 13,13,绝对定位右上):播放 `rotate(26°)` / 暂停 `rotate(10°)`,`animateFloatAsState` spring。组成:转轴底座 26 圆(径向 `#2c2c2c→#161616` + 边)、红点 10 在(8,8)带红光、臂杆 4×118(线性 `#2e2e2e→#7a7a7a→#2e2e2e`)在(top13,left11)、唱头 20×18(`#333→#1a1a1a`)在(top126,left3)、红色唱针 3×7 在(top143,left11.5)。
3. **信息 + 控制**(padding 0/28/22):
   - 标题 23/600 + 艺术家 13.5/50%(居中)。
   - 进度行:elapsed 11/40% + 轨道(3dp,`rgba白.14`,红填充 + 红 thumb 11dp 带 `0 0 0 4px rgba红.18` 光晕)+ duration。可 seek。
   - 传输控制行(`SpaceBetween`):左 expand 图标(装饰 65%白)、prev(30)、**大红播放键 68dp**(`#E60026` 圆,白播放/暂停图标 26,阴影 `0 8 26 rgba红.42`)、next(30)、右 repeat 图标 → **绑 `cycleMode`**(`#E60026` 当模式≠顺序,否则 65%白;长按/点循环 顺序→随机→单曲)。

## 8. 歌词页 `LyricsScreen`(LYRICS 层)

`Box`,bg `#0a0a0a`:
1. **背景**:当前封面模糊放大(`blur(46.dp)` API31+;`scale 1.3`)+ 暗罩 `rgba(8,8,8,0.84)`。封面取自 `CoverImage` 的 `rememberCover`。
2. **顶栏**(h50):⌄(回 PLAYER)+ 居中 标题/艺术家。
3. **歌词区**(`weight1`,点 → 回 PLAYER):居中滚动,复用 `LrcParser` + `LyricsIndex`。当前行 `#FFFFFF` 17/600,其余 `rgba白.30` 14.5/400;`LazyColumn` `animateScrollToItem(active)` 居中(对应设计 translateY(-curLy*44));行高约 44。
4. **底部控制**(居中,gap 40):prev(28)/ **红播放键 58dp**(`#E60026`,阴影同上)/ next(28)。

## 9. 设置抽屉 `DrawerContent`(红黑)

`ModalDrawerSheet`(bg `#0f0f0f`,宽 82%/最大 330,右边框 `rgba白.06`),两个子视图 menu / about:
- **头部**(padding 28/22,底部分隔线):红 logo 46 圆角13(`#E60026`,白播放三角,红光)+ "TingMusic" 17/700 + "局域网音乐播放器 · 你的私人电台" 11.5/40%。
- **menu**:
  - **局域网同步** 段:沿用 `SyncViewModel`——"扫描 Mac" / 手填 IP 输入框 / 发现的服务器各带"同步" / 同步进度文案;红黑样式(按钮红强调)。
  - **关于 TingMusic** 行(图标 + chevron)→ `drawerView=about`。
  - 底部:"© 2026 TingMusic · v0.1.0" 11/28%。
- **about**(子视图,⌐ 返回 menu):红 logo 74 圆角20 + "TingMusic" 18/700 + "版本 0.1.0" 12/40% + "本地局域网同步音乐播放器" + 底部 "© 2026 · Made with 红 & 黑"。(去掉设计里的检查更新/用户协议/隐私政策假行。)

## 10. 删除 / 改动

- **删**:`ui/theme/Theme.kt` 的 `AppTheme` 枚举与双 ColorScheme;`data/SettingsStore.kt`(仅存主题);`AppThemeTest.kt`;`SyncViewModel` 的 `theme`/`setTheme`/`settings`;`ui/NowPlayingScreen.kt`(拆成 PlayerScreen + LyricsScreen)。
- **改**:`Theme.kt`(单一红黑 `TingMusicTheme(content)`)、`PlaylistScreen.kt`、`MiniPlayer.kt`、`DrawerContent.kt`、`Vinyl.kt`(凹槽碟 + 精致唱臂)、`MainActivity.kt`(三屏导航)、`CoverImage.kt`(保留;`rememberCover` 仍 internal 供 PlayerScreen/LyricsScreen 取图)。
- **新增**:`ui/PlayerScreen.kt`、`ui/LyricsScreen.kt`、`ui/RedBlackPieces.kt`(均衡器 bars / conic 进度环 / 红圈播图标 等小组件,集中放,避免散落)。
- **不动**:`playback/*`(Service/Controller/PlayMode/LyricsIndex)、`sync/*`、`library/*`。

## 11. 数据流 / 功能映射

- `PlaybackController.state` → 列表当前高亮/均衡器、mini 条、播放器、歌词。
- 进度:MainActivity ~300ms 轮询 `currentPositionMs()` → `livePos`,喂进度环/进度条/歌词高亮。
- `SyncViewModel.state.tracks` → 列表 + `setLibrary`;"播放全部" → `play(tracks.first())`。
- repeat 图标 → `cycleMode`,按 `state.mode` 上色。

## 12. 错误处理

- 无封面 → 黑胶占位(`CoverImage` 既有逻辑,红心中心保留)。`Modifier.blur` API<31 退化为暗罩。
- 无 `.lrc` → 歌词区显示"暂无歌词"。空库 → 列表引导文案,不显示 mini 条 / 播放器入口。

## 13. 测试

- 删除 `AppThemeTest` 后,既有 JVM 单测应剩 38(40 − AppTheme 2);其余不动。本次无新纯逻辑(均为 UI/视觉),不新增单测;靠真机验证。
- 真机:列表(均衡器/播放全部/封面/底部 mini 条)、点歌上滑进播放器(凹槽黑胶旋转 + 唱臂落下 + 大红键)、点碟进歌词(模糊背景 + 滚动高亮)、抽屉(红 logo + 同步控件 + 关于)、红黑配色整体一致、返回键三屏回退。

## 14. 明确不做

- 搜索功能(搜索框仅装饰)、⋮ 过载菜单、播放音质/定时关闭、检查更新/用户协议/隐私政策(关于里去掉)。
- 不改播放/同步/曲库后端逻辑。

## 15. 里程碑(供 writing-plans)

1. **红黑主题地基**:`Theme.kt` 单一红黑 + 删主题机制(AppTheme/SettingsStore/开关/vm.theme/AppThemeTest)+ `RedBlackPieces.kt` 小组件。
2. **列表 + mini 条**:`PlaylistScreen`(顶栏/标题/播放全部/均衡器列表)+ `MiniPlayer`(conic 进度环)。
3. **播放器页**:`PlayerScreen` + `Vinyl`(凹槽碟 + 精致唱臂 + 大红键 + 进度 + repeat→cycleMode)。
4. **歌词页 + 抽屉 + 导航**:`LyricsScreen`(模糊 + 滚动)+ `DrawerContent`(红黑 + 同步 + 关于)+ `MainActivity` 三屏导航;退役 `NowPlayingScreen`。
5. **真机端到端**:整套红黑流程验证。
