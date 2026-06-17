# Mac 同步服务 (Android Plan 1/3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给现有 macOS TingMusic 应用加一个局域网同步服务:`axum` HTTP 服务暴露 `/manifest` + `/files/{*path}`,`mdns-sd` 广播 `_tingmusic._tcp`,Settings 里有开关并显示本机 `IP:端口`。

**Architecture:** 在 Tauri 后端新增两个纯 Rust 模块(`sync_server.rs`、`mdns_advertise.rs`),挂到现有 tokio async runtime 上。曲库根放在 `AppState` 里的 `Arc<Mutex<PathBuf>>`,随 `scan_folder`/`save_config` 更新,HTTP handler 每次请求读当前根,所以换曲库无需重启服务。`lan_sync` 开关走独立 Tauri command,不混入现有 `saveConfig` 流程。

**Tech Stack:** Rust、Tauri 2、axum 0.7、mdns-sd、local-ip-address、tokio;前端 React/TS 小改 Settings。

**关联 spec:** `docs/superpowers/specs/2026-06-16-tingmusic-android-design.md`(§4 协议、§6 Mac 端改动)。本计划是 Android 三阶段的第 1 阶段,产出可用 `curl`/`dns-sd` 独立验证的 Mac 同步服务。

---

### Task 1: 加依赖,确认能编译

**Files:**
- Modify: `src-tauri/Cargo.toml`

- [ ] **Step 1: 在 `[dependencies]` 末尾加三个新依赖,并给 tokio 加 `net` feature**

把现有这行:
```toml
tokio = { version = "1", features = ["sync", "time", "rt-multi-thread", "macros"] }
```
改成:
```toml
tokio = { version = "1", features = ["sync", "time", "rt-multi-thread", "macros", "net"] }
```
并在 `[dependencies]` 区块内(`urlencoding = "2"` 这行下面)追加:
```toml
axum = "0.7"
mdns-sd = "0.11"
local-ip-address = "0.6"
```

- [ ] **Step 2: 确认编译通过**

Run: `cd src-tauri && cargo build`
Expected: 编译成功(首次会拉取 axum/mdns-sd 等,耗时几分钟)。无报错即可。

- [ ] **Step 3: Commit**

```bash
git add src-tauri/Cargo.toml src-tauri/Cargo.lock
git commit -m "build(android): add axum + mdns-sd + local-ip-address for LAN sync server"
```

---

### Task 2: manifest 构建(纯函数 + TDD)

**Files:**
- Create: `src-tauri/src/sync_server.rs`
- Modify: `src-tauri/src/lib.rs:1-6`(注册模块)

- [ ] **Step 1: 在 `lib.rs` 顶部模块声明区注册新模块**

`src-tauri/src/lib.rs` 开头现在是:
```rust
pub mod types;
pub mod config;
pub mod lyrics;
pub mod scanner;
pub mod player;
pub mod cover_fetch;
```
在末尾加一行:
```rust
pub mod sync_server;
```

- [ ] **Step 2: 创建 `sync_server.rs`,写类型 + manifest 构建函数 + 失败测试**

Create `src-tauri/src/sync_server.rs`:
```rust
use serde::Serialize;
use std::path::Path;
use walkdir::WalkDir;

/// 与 macOS scanner 的 SUPPORTED_EXTS 保持一致,外加 lrc。
const AUDIO_EXTS: &[&str] = &["mp3", "flac", "wav", "ogg", "m4a", "aac"];

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
pub struct ManifestEntry {
    /// 相对曲库根,使用 `/` 分隔。
    pub path: String,
    pub size: u64,
    /// Unix 秒。
    pub mtime: u64,
}

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
pub struct Manifest {
    pub version: u32,
    pub library_name: String,
    pub files: Vec<ManifestEntry>,
}

/// 遍历曲库根,产出音频 + .lrc 文件的清单,按相对路径排序。
pub fn build_manifest(root: &Path) -> Manifest {
    let mut files: Vec<ManifestEntry> = Vec::new();
    for entry in WalkDir::new(root).follow_links(false).into_iter().filter_map(|e| e.ok()) {
        if !entry.file_type().is_file() {
            continue;
        }
        let path = entry.path();
        let ext = match path.extension().and_then(|e| e.to_str()) {
            Some(e) => e.to_lowercase(),
            None => continue,
        };
        if !AUDIO_EXTS.contains(&ext.as_str()) && ext != "lrc" {
            continue;
        }
        let rel = match path.strip_prefix(root) {
            Ok(r) => r,
            Err(_) => continue,
        };
        let rel_str = rel
            .components()
            .filter_map(|c| c.as_os_str().to_str())
            .collect::<Vec<_>>()
            .join("/");
        if rel_str.is_empty() {
            continue;
        }
        let meta = match entry.metadata() {
            Ok(m) => m,
            Err(_) => continue,
        };
        let mtime = meta
            .modified()
            .ok()
            .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
            .map(|d| d.as_secs())
            .unwrap_or(0);
        files.push(ManifestEntry { path: rel_str, size: meta.len(), mtime });
    }
    files.sort_by(|a, b| a.path.cmp(&b.path));
    let library_name = root
        .file_name()
        .map(|n| n.to_string_lossy().into_owned())
        .unwrap_or_else(|| "Music".into());
    Manifest { version: 1, library_name, files }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use std::io::Write;
    use tempfile::tempdir;

    fn write(dir: &Path, name: &str, bytes: &[u8]) {
        let p = dir.join(name);
        if let Some(parent) = p.parent() {
            fs::create_dir_all(parent).unwrap();
        }
        let mut f = fs::File::create(&p).unwrap();
        f.write_all(bytes).unwrap();
    }

    #[test]
    fn includes_only_audio_and_lrc() {
        let dir = tempdir().unwrap();
        write(dir.path(), "a.mp3", b"x");
        write(dir.path(), "a.lrc", b"[00:01.00]hi");
        write(dir.path(), "cover.jpg", b"x");
        write(dir.path(), "notes.txt", b"x");
        let m = build_manifest(dir.path());
        let paths: Vec<&str> = m.files.iter().map(|e| e.path.as_str()).collect();
        assert!(paths.contains(&"a.mp3"));
        assert!(paths.contains(&"a.lrc"));
        assert!(!paths.contains(&"cover.jpg"));
        assert!(!paths.contains(&"notes.txt"));
    }

    #[test]
    fn relative_paths_use_forward_slash_and_recurse() {
        let dir = tempdir().unwrap();
        write(dir.path(), "Anime/song.flac", b"x");
        let m = build_manifest(dir.path());
        assert_eq!(m.files.len(), 1);
        assert_eq!(m.files[0].path, "Anime/song.flac");
    }

    #[test]
    fn sorted_by_path() {
        let dir = tempdir().unwrap();
        write(dir.path(), "b.mp3", b"x");
        write(dir.path(), "a.mp3", b"x");
        let m = build_manifest(dir.path());
        assert_eq!(m.files[0].path, "a.mp3");
        assert_eq!(m.files[1].path, "b.mp3");
    }

    #[test]
    fn size_is_reported() {
        let dir = tempdir().unwrap();
        write(dir.path(), "a.mp3", b"12345");
        let m = build_manifest(dir.path());
        assert_eq!(m.files[0].size, 5);
    }

    #[test]
    fn empty_dir_yields_empty_files() {
        let dir = tempdir().unwrap();
        let m = build_manifest(dir.path());
        assert_eq!(m.version, 1);
        assert!(m.files.is_empty());
    }

    #[test]
    fn library_name_is_dir_name() {
        let dir = tempdir().unwrap();
        let sub = dir.path().join("MyMusic");
        std::fs::create_dir(&sub).unwrap();
        let m = build_manifest(&sub);
        assert_eq!(m.library_name, "MyMusic");
    }
}
```

- [ ] **Step 3: 跑测试,确认通过**

Run: `cd src-tauri && cargo test --lib sync_server::tests`
Expected: 6 个测试全部 PASS。

- [ ] **Step 4: Commit**

```bash
git add src-tauri/src/sync_server.rs src-tauri/src/lib.rs
git commit -m "feat(sync): build_manifest walks library for audio + lrc"
```

---

### Task 3: 路径安全解析(纯函数 + TDD)

**Files:**
- Modify: `src-tauri/src/sync_server.rs`

- [ ] **Step 1: 在 `sync_server.rs` 顶部 `use` 区补充导入**

把文件顶部:
```rust
use serde::Serialize;
use std::path::Path;
use walkdir::WalkDir;
```
改为:
```rust
use serde::Serialize;
use std::path::{Component, Path, PathBuf};
use walkdir::WalkDir;
```

- [ ] **Step 2: 在 `build_manifest` 函数下方加 `resolve_safe_path` + 测试**

在 `build_manifest` 的右花括号之后、`#[cfg(test)]` 之前,插入:
```rust
/// 把客户端给的相对路径安全地解析成曲库根内的绝对路径。
/// 拒绝 `..`、绝对路径、Windows 盘符前缀;canonicalize 后必须仍在根目录内。
/// 任何不安全或不存在的情况返回 None。
pub fn resolve_safe_path(root: &Path, rel: &str) -> Option<PathBuf> {
    let rel_path = PathBuf::from(rel);
    let unsafe_component = rel_path.components().any(|c| {
        matches!(c, Component::ParentDir | Component::RootDir | Component::Prefix(_))
    });
    if unsafe_component {
        return None;
    }
    let full = root.join(&rel_path);
    let canon_full = full.canonicalize().ok()?;
    let canon_root = root.canonicalize().ok()?;
    if !canon_full.starts_with(&canon_root) {
        return None;
    }
    Some(canon_full)
}
```

- [ ] **Step 3: 在 `mod tests` 内追加路径安全测试(放在最后一个 `}` 之前)**

在 `library_name_is_dir_name` 测试之后、`mod tests` 的闭合 `}` 之前加:
```rust
    #[test]
    fn resolve_normal_file_in_root() {
        let dir = tempdir().unwrap();
        write(dir.path(), "Anime/song.mp3", b"x");
        let resolved = resolve_safe_path(dir.path(), "Anime/song.mp3");
        assert!(resolved.is_some());
    }

    #[test]
    fn resolve_rejects_parent_traversal() {
        let dir = tempdir().unwrap();
        assert!(resolve_safe_path(dir.path(), "../secret.mp3").is_none());
        assert!(resolve_safe_path(dir.path(), "a/../../secret.mp3").is_none());
    }

    #[test]
    fn resolve_rejects_absolute_path() {
        let dir = tempdir().unwrap();
        assert!(resolve_safe_path(dir.path(), "/etc/passwd").is_none());
    }

    #[test]
    fn resolve_missing_file_is_none() {
        let dir = tempdir().unwrap();
        assert!(resolve_safe_path(dir.path(), "nope.mp3").is_none());
    }
```

- [ ] **Step 4: 跑测试**

Run: `cd src-tauri && cargo test --lib sync_server::tests`
Expected: 10 个测试全部 PASS。

- [ ] **Step 5: Commit**

```bash
git add src-tauri/src/sync_server.rs
git commit -m "feat(sync): resolve_safe_path guards against traversal"
```

---

### Task 4: axum 路由 + 启动(含集成测试)

**Files:**
- Modify: `src-tauri/src/sync_server.rs`

- [ ] **Step 1: 在 `sync_server.rs` 顶部 `use` 区补充 axum/tokio/parking_lot 导入**

把顶部 `use` 区扩成:
```rust
use axum::extract::{Path as AxumPath, State};
use axum::http::{header, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::routing::get;
use axum::{Json, Router};
use parking_lot::Mutex;
use serde::Serialize;
use std::path::{Component, Path, PathBuf};
use std::sync::Arc;
use walkdir::WalkDir;
```

- [ ] **Step 2: 在 `resolve_safe_path` 之后、`#[cfg(test)]` 之前,加 server state / handlers / 路由 / 启动**

```rust
/// 服务端共享状态:曲库根可变(用户换曲库时更新),handler 每次请求读当前值。
#[derive(Clone)]
pub struct ServerState {
    pub root: Arc<Mutex<PathBuf>>,
}

/// 服务句柄:drop 或调用 stop() 时优雅关闭 HTTP 服务。
pub struct ServerHandle {
    pub port: u16,
    shutdown: Option<tokio::sync::oneshot::Sender<()>>,
}

impl ServerHandle {
    pub fn stop(mut self) {
        if let Some(tx) = self.shutdown.take() {
            let _ = tx.send(());
        }
    }
}

async fn serve_manifest(State(state): State<ServerState>) -> Json<Manifest> {
    let root = state.root.lock().clone();
    Json(build_manifest(&root))
}

async fn serve_file(State(state): State<ServerState>, AxumPath(rel): AxumPath<String>) -> Response {
    let root = state.root.lock().clone();
    match resolve_safe_path(&root, &rel) {
        Some(full) => match tokio::fs::read(&full).await {
            Ok(bytes) => (
                [(header::CONTENT_TYPE, "application/octet-stream")],
                bytes,
            )
                .into_response(),
            Err(_) => StatusCode::NOT_FOUND.into_response(),
        },
        None => StatusCode::FORBIDDEN.into_response(),
    }
}

pub fn router(state: ServerState) -> Router {
    Router::new()
        .route("/manifest", get(serve_manifest))
        .route("/files/*path", get(serve_file))
        .with_state(state)
}

/// 从 preferred_port 起尝试绑定,占用则向上顺延 20 个端口。
/// 成功后在 tokio runtime 上 spawn 服务,返回实际端口 + 句柄。
pub async fn start(state: ServerState, preferred_port: u16) -> anyhow::Result<ServerHandle> {
    let mut bound: Option<(tokio::net::TcpListener, u16)> = None;
    for p in preferred_port..preferred_port.saturating_add(20) {
        if let Ok(l) = tokio::net::TcpListener::bind(("0.0.0.0", p)).await {
            bound = Some((l, p));
            break;
        }
    }
    let (listener, port) =
        bound.ok_or_else(|| anyhow::anyhow!("no free port near {preferred_port}"))?;
    let app = router(state);
    let (tx, rx) = tokio::sync::oneshot::channel::<()>();
    tokio::spawn(async move {
        let _ = axum::serve(listener, app)
            .with_graceful_shutdown(async {
                let _ = rx.await;
            })
            .await;
    });
    Ok(ServerHandle { port, shutdown: Some(tx) })
}
```

- [ ] **Step 3: 在 `mod tests` 内追加 server 集成测试(放在最后一个 `}` 之前)**

```rust
    fn test_state(root: &Path) -> ServerState {
        ServerState { root: Arc::new(Mutex::new(root.to_path_buf())) }
    }

    #[tokio::test]
    async fn manifest_endpoint_returns_files() {
        let dir = tempdir().unwrap();
        write(dir.path(), "a.mp3", b"hello");
        let handle = start(test_state(dir.path()), 18737).await.unwrap();
        let url = format!("http://127.0.0.1:{}/manifest", handle.port);
        let body: serde_json::Value = reqwest::get(&url).await.unwrap().json().await.unwrap();
        assert_eq!(body["version"], 1);
        assert_eq!(body["files"][0]["path"], "a.mp3");
        assert_eq!(body["files"][0]["size"], 5);
        handle.stop();
    }

    #[tokio::test]
    async fn file_endpoint_serves_bytes() {
        let dir = tempdir().unwrap();
        write(dir.path(), "a.mp3", b"hello");
        let handle = start(test_state(dir.path()), 18760).await.unwrap();
        let url = format!("http://127.0.0.1:{}/files/a.mp3", handle.port);
        let bytes = reqwest::get(&url).await.unwrap().bytes().await.unwrap();
        assert_eq!(&bytes[..], b"hello");
        handle.stop();
    }

    #[tokio::test]
    async fn file_endpoint_rejects_traversal() {
        let dir = tempdir().unwrap();
        write(dir.path(), "a.mp3", b"hello");
        let handle = start(test_state(dir.path()), 18780).await.unwrap();
        let url = format!("http://127.0.0.1:{}/files/..%2f..%2fetc%2fpasswd", handle.port);
        let status = reqwest::get(&url).await.unwrap().status();
        assert!(status.is_client_error(), "expected 4xx, got {status}");
        handle.stop();
    }
```

- [ ] **Step 4: 跑测试**

Run: `cd src-tauri && cargo test --lib sync_server`
Expected: 全部 PASS(13 个)。若 `axum::serve(listener, app)` 报类型错,改成 `axum::serve(listener, app.into_make_service())`。

- [ ] **Step 5: Commit**

```bash
git add src-tauri/src/sync_server.rs
git commit -m "feat(sync): axum router for /manifest and /files with graceful shutdown"
```

---

### Task 5: mDNS 广播模块

**Files:**
- Create: `src-tauri/src/mdns_advertise.rs`
- Modify: `src-tauri/src/lib.rs`(注册模块)

- [ ] **Step 1: 在 `lib.rs` 模块声明区追加**

在 `pub mod sync_server;` 下面加:
```rust
pub mod mdns_advertise;
```

- [ ] **Step 2: 创建 `mdns_advertise.rs`**

Create `src-tauri/src/mdns_advertise.rs`:
```rust
use mdns_sd::{ServiceDaemon, ServiceInfo};
use std::collections::HashMap;

/// 持有 mDNS daemon;drop 时注销服务。
pub struct MdnsHandle {
    daemon: ServiceDaemon,
    fullname: String,
}

impl Drop for MdnsHandle {
    fn drop(&mut self) {
        let _ = self.daemon.unregister(&self.fullname);
    }
}

/// 广播 `_tingmusic._tcp` 服务,TXT 带 v/port/lib。
pub fn advertise(port: u16, library_name: &str) -> anyhow::Result<MdnsHandle> {
    let daemon = ServiceDaemon::new()?;
    let ty_domain = "_tingmusic._tcp.local.";
    let instance = "TingMusic";
    let host = format!("tingmusic-{port}.local.");

    let mut props: HashMap<String, String> = HashMap::new();
    props.insert("v".into(), "1".into());
    props.insert("port".into(), port.to_string());
    props.insert("lib".into(), library_name.to_string());

    // 空 IP + enable_addr_auto:让 mdns-sd 自动用本机所有网卡地址。
    let service = ServiceInfo::new(ty_domain, instance, &host, "", port, props)?
        .enable_addr_auto();
    let fullname = service.get_fullname().to_string();
    daemon.register(service)?;
    Ok(MdnsHandle { daemon, fullname })
}
```

- [ ] **Step 3: 确认编译**

Run: `cd src-tauri && cargo build`
Expected: 编译成功。(本模块不写单测;Task 8 真机验证时用 `dns-sd -B _tingmusic._tcp` 确认能被发现。若 `ServiceInfo::new`/`enable_addr_auto` 签名与 mdns-sd 0.11 不符,按当前版本文档微调:核心是注册 `_tingmusic._tcp.local.`、端口正确、TXT 含 v/port/lib。)

- [ ] **Step 4: Commit**

```bash
git add src-tauri/src/mdns_advertise.rs src-tauri/src/lib.rs
git commit -m "feat(sync): mdns_advertise registers _tingmusic._tcp service"
```

---

### Task 6: Config 加 `lan_sync` 字段

**Files:**
- Modify: `src-tauri/src/types.rs`

- [ ] **Step 1: 给 `Config` 结构体加字段 + 默认值函数**

在 `types.rs` 的 `Config` 定义(现含 folder/volume/mode/cover_shape/theme)里,在 `theme` 字段下面加:
```rust
    #[serde(default = "default_true")]
    pub lan_sync: bool,
```
并在 `Config` 结构体定义之后(`#[derive(...)] pub enum CoverShape` 之前)加一个自由函数:
```rust
fn default_true() -> bool {
    true
}
```

- [ ] **Step 2: 更新 `Config::default()`**

把 `impl Default for Config` 里的返回结构体补上 `lan_sync`:
```rust
impl Default for Config {
    fn default() -> Self {
        Self {
            folder: None,
            volume: 0.7,
            mode: Mode::Sequential,
            cover_shape: CoverShape::Circle,
            theme: Theme::Default,
            lan_sync: true,
        }
    }
}
```

- [ ] **Step 3: 在 `types.rs` 的 `mod tests` 里加一个 serde 默认值测试**

在 `mod tests` 内追加:
```rust
    #[test]
    fn lan_sync_defaults_true_when_absent() {
        // 老配置文件没有 lan_sync 字段时,应默认 true。
        let json = r#"{"folder":null,"volume":0.7,"mode":"sequential"}"#;
        let cfg: Config = serde_json::from_str(json).unwrap();
        assert!(cfg.lan_sync);
    }
```

- [ ] **Step 4: 跑测试**

Run: `cd src-tauri && cargo test --lib types::tests`
Expected: 全部 PASS(含新测试)。注:`config.rs` 的 `round_trip` 测试会构造 `Config { ... }`,若编译报缺字段,在那个字面量里补 `lan_sync: true,`。

- [ ] **Step 5: 若 `config.rs` 测试编译失败,补字段后再跑**

Run: `cd src-tauri && cargo test --lib`
Expected: 全部 PASS。

- [ ] **Step 6: Commit**

```bash
git add src-tauri/src/types.rs src-tauri/src/config.rs
git commit -m "feat(config): add lan_sync flag, defaults true"
```

---

### Task 7: 后端接线 —— AppState、setup 启动、命令、save_config 保留 lan_sync

**Files:**
- Modify: `src-tauri/src/lib.rs`

- [ ] **Step 1: 扩展 `AppState` 与导入**

把 `lib.rs` 的 `use` 区里:
```rust
use crate::scanner::scan;
```
之后补充:
```rust
use crate::sync_server::{self, ServerHandle, ServerState};
use crate::mdns_advertise::{self, MdnsHandle};
```
并把 `AppState` 结构体改成(新增 `library_root` 与 `sync_runtime` 两个字段):
```rust
pub struct AppState {
    pub player: Arc<Player>,
    pub tracks: Mutex<Vec<Track>>,
    pub config_path: PathBuf,
    pub config: Mutex<Config>,
    pub cover_cache_dir: PathBuf,
    pub library_root: Arc<Mutex<PathBuf>>,
    pub sync_runtime: Mutex<Option<SyncRuntime>>,
}

/// 运行中的同步服务:HTTP 句柄 + mDNS 句柄(drop 即停)。
pub struct SyncRuntime {
    server: ServerHandle,
    _mdns: MdnsHandle,
    port: u16,
}
```

- [ ] **Step 2: 加一个解析初始曲库根的 helper(放在 `config_path` 函数附近)**

```rust
fn initial_library_root(cfg: &Config) -> PathBuf {
    if let Some(f) = &cfg.folder {
        return PathBuf::from(f);
    }
    if let Some(home) = std::env::var_os("HOME") {
        return PathBuf::from(home).join("Music");
    }
    PathBuf::from(".")
}

/// 启动 HTTP + mDNS,返回运行时句柄。失败时返回 None(同步是可选能力,不该让 app 崩)。
async fn start_sync_runtime(root: Arc<Mutex<PathBuf>>) -> Option<SyncRuntime> {
    let lib_name = root
        .lock()
        .file_name()
        .map(|n| n.to_string_lossy().into_owned())
        .unwrap_or_else(|| "Music".into());
    let server = match sync_server::start(ServerState { root: root.clone() }, 8737).await {
        Ok(h) => h,
        Err(e) => {
            eprintln!("sync server failed to start: {e}");
            return None;
        }
    };
    let port = server.port;
    let mdns = match mdns_advertise::advertise(port, &lib_name) {
        Ok(h) => h,
        Err(e) => {
            eprintln!("mdns advertise failed: {e}");
            server.stop();
            return None;
        }
    };
    Some(SyncRuntime { server, _mdns: mdns, port })
}
```

- [ ] **Step 3: 在 `setup` 里构造 library_root,并在 `lan_sync` 开启时启动同步**

把 `setup` 闭包里构造 `state` 的部分改成包含新字段。找到现有:
```rust
            let state = AppState {
                player: player.clone(),
                tracks: Mutex::new(Vec::new()),
                config_path: cfg_path,
                config: Mutex::new(cfg.clone()),
                cover_cache_dir,
            };
            app.manage(state);
```
替换为:
```rust
            let library_root = Arc::new(Mutex::new(initial_library_root(&cfg)));
            let state = AppState {
                player: player.clone(),
                tracks: Mutex::new(Vec::new()),
                config_path: cfg_path,
                config: Mutex::new(cfg.clone()),
                cover_cache_dir,
                library_root: library_root.clone(),
                sync_runtime: Mutex::new(None),
            };
            app.manage(state);

            // lan_sync 默认开:启动后台同步服务 + mDNS 广播。
            if cfg.lan_sync {
                let handle = app.handle().clone();
                let root_for_sync = library_root.clone();
                tauri::async_runtime::spawn(async move {
                    let rt = start_sync_runtime(root_for_sync).await;
                    if let Some(rt) = rt {
                        let state: tauri::State<AppState> = handle.state();
                        *state.sync_runtime.lock() = Some(rt);
                    }
                });
            }
```

- [ ] **Step 4: 让 `scan_folder` 与 `save_config` 同步更新 `library_root`,并让 `save_config` 保留 `lan_sync`**

把现有 `scan_folder` 命令体首行加一句更新曲库根。找到:
```rust
#[tauri::command]
fn scan_folder(folder: String, state: State<'_, AppState>) -> ScanResult {
    let out = scan(std::path::Path::new(&folder));
```
改为:
```rust
#[tauri::command]
fn scan_folder(folder: String, state: State<'_, AppState>) -> ScanResult {
    *state.library_root.lock() = PathBuf::from(&folder);
    let out = scan(std::path::Path::new(&folder));
```

把现有 `save_config` 改为保留服务端已有的 `lan_sync`,并按需更新曲库根:
```rust
#[tauri::command]
fn save_config(mut cfg: Config, state: State<'_, AppState>) -> Result<(), String> {
    // lan_sync 由独立命令管理,这里强制保留已有值,避免改音量/模式时被前端的
    // 默认值覆盖。
    cfg.lan_sync = state.config.lock().lan_sync;
    if let Some(f) = &cfg.folder {
        *state.library_root.lock() = PathBuf::from(f);
    }
    write_to(&state.config_path, &cfg).map_err(|e| e.to_string())?;
    *state.config.lock() = cfg;
    Ok(())
}
```

- [ ] **Step 5: 加 `lan_sync_status` 与 `set_lan_sync` 两个命令**

在 `fetch_cover` 命令之后、`fn config_path` 之前加:
```rust
#[derive(serde::Serialize)]
pub struct LanSyncStatus {
    pub enabled: bool,
    /// "192.168.x.x:8737";未运行或拿不到 IP 时为 None。
    pub address: Option<String>,
}

#[tauri::command]
fn lan_sync_status(state: State<'_, AppState>) -> LanSyncStatus {
    let enabled = state.config.lock().lan_sync;
    let address = state.sync_runtime.lock().as_ref().and_then(|rt| {
        local_ip_address::local_ip()
            .ok()
            .map(|ip| format!("{}:{}", ip, rt.port))
    });
    LanSyncStatus { enabled, address }
}

#[tauri::command]
async fn set_lan_sync(
    enabled: bool,
    state: State<'_, AppState>,
) -> Result<(), String> {
    // 持久化开关到 config.json。
    {
        let mut cfg = state.config.lock().clone();
        cfg.lan_sync = enabled;
        write_to(&state.config_path, &cfg).map_err(|e| e.to_string())?;
        *state.config.lock() = cfg;
    }
    if enabled {
        let already = state.sync_runtime.lock().is_some();
        if !already {
            let root = state.library_root.clone();
            if let Some(rt) = start_sync_runtime(root).await {
                *state.sync_runtime.lock() = Some(rt);
            }
        }
    } else if let Some(rt) = state.sync_runtime.lock().take() {
        rt.server.stop();
        // rt._mdns 在此 drop,自动注销广播。
    }
    Ok(())
}
```

- [ ] **Step 6: 把两个新命令注册进 `invoke_handler`**

把:
```rust
            seek, set_volume, set_mode, fetch_cover,
        ])
```
改为:
```rust
            seek, set_volume, set_mode, fetch_cover,
            lan_sync_status, set_lan_sync,
        ])
```

- [ ] **Step 7: 编译 + 跑全部 Rust 测试**

Run: `cd src-tauri && cargo build && cargo test --lib`
Expected: 编译成功,所有 Rust 测试 PASS。

- [ ] **Step 8: Commit**

```bash
git add src-tauri/src/lib.rs
git commit -m "feat(sync): wire LAN sync server + mDNS into app state and commands"
```

---

### Task 8: 前端 —— Settings 局域网同步开关 + 地址显示

**Files:**
- Modify: `src/lib/types.ts`
- Modify: `src/lib/api.ts`
- Modify: `src/components/Settings.tsx`

- [ ] **Step 1: `types.ts` 给 Config 加 `lan_sync`,加 LanSyncStatus 类型**

把 `Config` 接口:
```ts
export interface Config {
  folder: string | null;
  volume: number;
  mode: Mode;
  cover_shape: CoverShape;
  theme: Theme;
}
```
改为:
```ts
export interface Config {
  folder: string | null;
  volume: number;
  mode: Mode;
  cover_shape: CoverShape;
  theme: Theme;
  lan_sync: boolean;
}

export interface LanSyncStatus {
  enabled: boolean;
  address: string | null;
}

/** saveConfig 不带 lan_sync —— 它由 setLanSync 独立管理,后端会保留已有值。 */
export type SaveableConfig = Omit<Config, "lan_sync">;
```

- [ ] **Step 2: `api.ts` 改 saveConfig 入参类型,加两个新命令**

把顶部 import:
```ts
import type { Config, Mode, ScanResult } from "./types";
```
改为:
```ts
import type { Config, LanSyncStatus, Mode, SaveableConfig, ScanResult } from "./types";
```
把:
```ts
  saveConfig: (cfg: Config) => invoke<void>("save_config", { cfg }),
```
改为:
```ts
  saveConfig: (cfg: SaveableConfig) => invoke<void>("save_config", { cfg }),
```
并在 `fetchCover` 那一项后面加:
```ts
  lanSyncStatus: () => invoke<LanSyncStatus>("lan_sync_status"),
  setLanSync: (enabled: boolean) => invoke<void>("set_lan_sync", { enabled }),
```

- [ ] **Step 3: `Settings.tsx` 加同步状态 state + 拉取逻辑**

在组件内已有的 `const saveTimer = useRef<number | null>(null);` 下面加:
```ts
  const [lanSync, setLanSync] = useState(false);
  const [lanAddr, setLanAddr] = useState<string | null>(null);

  // 菜单打开时拉一次同步状态(地址依赖服务是否已起,实时性够用)。
  useEffect(() => {
    if (!menuOpen) return;
    api.lanSyncStatus()
      .then((st) => { setLanSync(st.enabled); setLanAddr(st.address); })
      .catch(() => {});
  }, [menuOpen]);

  const toggleLanSync = async (next: boolean) => {
    if (next === lanSync) return;
    setLanSync(next);
    try {
      await api.setLanSync(next);
      const st = await api.lanSyncStatus();
      setLanSync(st.enabled);
      setLanAddr(st.address);
    } catch (e) {
      console.error(e);
    }
  };
```

- [ ] **Step 4: 在主题那一段之后、退出按钮之前,插入"局域网同步"区块**

找到主题 pill-row 结束后的:
```tsx
          <div className="settings-divider" />
          <button
            role="menuitem"
            onClick={() => { setMenuOpen(false); getCurrentWindow().close().catch(console.error); }}
          >
```
在这个 `<div className="settings-divider" />` 之前插入:
```tsx
          <div className="settings-divider" />
          <div className="settings-section-label">局域网同步</div>
          <div className="settings-pill-row">
            {([["on", true], ["off", false]] as [string, boolean][]).map(([key, val]) => (
              <button
                key={key}
                role="menuitemradio"
                aria-checked={lanSync === val}
                className={`settings-pill ${lanSync === val ? "active" : ""}`}
                onClick={() => toggleLanSync(val)}
              >
                {val ? "开启" : "关闭"}
              </button>
            ))}
          </div>
          {lanSync && lanAddr && (
            <div className="settings-section-label">本机 {lanAddr}</div>
          )}
```

- [ ] **Step 5: 前端类型检查 + 测试**

Run: `npm run build`
Expected: `tsc` 通过(无类型错误),vite 构建成功。
Run: `npm test`
Expected: 现有前端测试仍全部 PASS。

- [ ] **Step 6: Commit**

```bash
git add src/lib/types.ts src/lib/api.ts src/components/Settings.tsx
git commit -m "feat(sync): Settings toggle for LAN sync + show host address"
```

---

### Task 9: 端到端手动验证(curl + dns-sd)

**Files:** 无(验证任务)

- [ ] **Step 1: 起开发版应用**

Run: `npm run tauri dev`(后台运行;等窗口出现、曲库扫描完成)
Expected: 应用启动,默认曲库 `~/Music` 被扫描。

- [ ] **Step 2: 确认 mDNS 广播可见**

Run: `dns-sd -B _tingmusic._tcp` （另开一个终端,观察 2-3 秒后 Ctrl-C)
Expected: 列表里出现名为 `TingMusic` 的服务实例。

- [ ] **Step 3: 确认 manifest 接口**

Run: `curl -s http://127.0.0.1:8737/manifest | head -c 400`
Expected: 返回 JSON,含 `"version":1`、`"library_name"`、`"files":[...]`(里面是 `~/Music` 下的音频/lrc 相对路径)。若端口顺延了,从 Settings 菜单"局域网同步"显示的 `本机 IP:端口` 里读实际端口。

- [ ] **Step 4: 确认文件接口能下载且防穿越**

先从 manifest 里挑一个 `path`(假设是 `Foo.mp3`),URL 编码后:
Run: `curl -s -o /tmp/tm_test.bin "http://127.0.0.1:8737/files/Foo.mp3" && ls -l /tmp/tm_test.bin`
Expected: 文件被下载,大小与 manifest 中该项 `size` 一致。
Run: `curl -s -o /dev/null -w "%{http_code}\n" "http://127.0.0.1:8737/files/..%2f..%2f..%2fetc%2fpasswd"`
Expected: `403`(或 4xx),不会泄露系统文件。

- [ ] **Step 5: 确认 Settings 开关**

手动:打开齿轮菜单 → "局域网同步" → 点"关闭"。
Run: `curl -s -o /dev/null -w "%{http_code}\n" --max-time 3 http://127.0.0.1:8737/manifest`
Expected: 连接失败/超时(服务已停)。再点"开启",`curl` 又能拿到 manifest。

- [ ] **Step 6: 收尾**

停掉 `npm run tauri dev`。本阶段无需再 commit(纯验证)。若发现问题,回到对应 Task 修复。

---

## Self-Review

**Spec coverage(§4 协议 / §6 Mac 端):**
- §4.1 mDNS 广播 `_tingmusic._tcp` + TXT(v/port/lib) → Task 5 ✅
- §4.2 `/manifest` + `/files/{*path}` 自定义 handler + 路径安全 + 无 Range → Task 2/3/4 ✅
- §4.3 Manifest 格式(version/library_name/files[path,size,mtime]) → Task 2 ✅
- §6 Config `lan_sync` 默认开 → Task 6 ✅;setup 启动 + 命令 + save_config 保留 → Task 7 ✅;端口顺延 → Task 4 `start` ✅;Settings 开关 + IP 显示 → Task 8 ✅;Cargo 依赖 + tokio net → Task 1 ✅
- §7 错误处理:端口顺延(Task 4)、bind/mdns 失败不崩(Task 7 `start_sync_runtime` 返回 None)、路径穿越拦截(Task 3/4)✅

**Placeholder scan:** 无 TBD/TODO;每个代码步骤都给了完整代码。Task 9 为纯手动验证任务,不含代码占位。

**Type consistency:** `ServerState{root}`、`ServerHandle{port,stop()}`、`SyncRuntime{server,_mdns,port}`、`Manifest{version,library_name,files}`、`ManifestEntry{path,size,mtime}`、`LanSyncStatus{enabled,address}` 在各 Task 间一致;前端 `Config.lan_sync`/`SaveableConfig`/`LanSyncStatus` 与后端字段名(snake_case)对应。`set_lan_sync`/`lan_sync_status` 命令名两端一致。

**注意点(执行时留意):** axum 0.7 通配路由写 `/files/*path`(若用到 0.8 需改 `/files/{*path}`);`axum::serve(listener, app)` 若类型不匹配改 `.into_make_service()`;mdns-sd 0.11 的 `ServiceInfo` API 以实际版本为准,目标是注册成功且 TXT/端口正确。
