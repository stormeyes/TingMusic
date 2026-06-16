pub mod types;
pub mod config;
pub mod lyrics;
pub mod scanner;
pub mod player;
pub mod cover_fetch;
pub mod sync_server;
pub mod mdns_advertise;

/// On macOS, set the dock icon at runtime so dev builds (which run an
/// unbundled binary) get the same vinyl artwork as the production .app.
/// Without this, Dock shows the generic "terminal binary" icon.
#[cfg(target_os = "macos")]
fn set_macos_dock_icon() {
    use objc2::AnyThread;
    use objc2_app_kit::{NSApplication, NSImage};
    use objc2_foundation::NSData;
    const ICON_BYTES: &[u8] = include_bytes!("../icons/icon.png");
    let mtm = match objc2::MainThreadMarker::new() {
        Some(m) => m,
        None => return,
    };
    unsafe {
        let data = NSData::with_bytes(ICON_BYTES);
        let Some(image) = NSImage::initWithData(NSImage::alloc(), &data) else { return };
        let app = NSApplication::sharedApplication(mtm);
        app.setApplicationIconImage(Some(&image));
    }
}

use crate::config::{read_from, write_to};
use crate::mdns_advertise::MdnsHandle;
use crate::player::Player;
use crate::scanner::scan;
use crate::sync_server::{ServerHandle, ServerState};
use crate::types::{Config, Mode, PlaybackState, ScanResult, Track, TrackDto};
use parking_lot::Mutex;
use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;
use tauri::{AppHandle, Emitter, Manager, State};

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

#[tauri::command]
fn load_config(state: State<'_, AppState>) -> Config {
    state.config.lock().clone()
}

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

#[tauri::command]
fn scan_folder(folder: String, state: State<'_, AppState>) -> ScanResult {
    *state.library_root.lock() = PathBuf::from(&folder);
    let out = scan(std::path::Path::new(&folder));
    let dtos: Vec<TrackDto> = out.tracks.iter().map(TrackDto::from).collect();
    state.player.set_queue(out.tracks.clone());
    *state.tracks.lock() = out.tracks;
    ScanResult { tracks: dtos, skipped: out.skipped }
}

#[tauri::command]
fn play(track_id: String, state: State<'_, AppState>) -> Result<(), String> {
    state.player.play_track_id(&track_id).map_err(|e| e.to_string())
}

#[tauri::command]
fn toggle_pause(state: State<'_, AppState>) {
    state.player.toggle_pause();
}

#[tauri::command]
fn next_track(state: State<'_, AppState>) -> Result<(), String> {
    state.player.next().map_err(|e| e.to_string())
}

#[tauri::command]
fn prev_track(state: State<'_, AppState>) -> Result<(), String> {
    state.player.prev().map_err(|e| e.to_string())
}

#[tauri::command]
fn seek(position_ms: u64, state: State<'_, AppState>) -> Result<(), String> {
    state.player.seek(position_ms).map_err(|e| e.to_string())
}

#[tauri::command]
fn set_volume(volume: f32, state: State<'_, AppState>) {
    state.player.set_volume(volume);
}

#[tauri::command]
fn set_mode(mode: Mode, state: State<'_, AppState>) {
    state.player.set_mode(mode);
}

#[tauri::command]
async fn fetch_cover(
    track_id: String,
    title: String,
    artist: String,
    state: State<'_, AppState>,
) -> Result<Option<String>, String> {
    let dir = state.cover_cache_dir.clone();
    cover_fetch::fetch_cover(&title, &artist, &dir, &track_id)
        .await
        .map_err(|e| e.to_string())
}

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

fn config_path(app: &AppHandle) -> PathBuf {
    app.path().app_config_dir().expect("app_config_dir").join("config.json")
}

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            #[cfg(target_os = "macos")]
            set_macos_dock_icon();
            let cfg_path = config_path(&app.handle());
            let cfg = read_from(&cfg_path);
            let player = Arc::new(Player::new(cfg.volume, cfg.mode).expect("audio init"));
            let cover_cache_dir = app.path().app_cache_dir().expect("app_cache_dir").join("covers");
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

            let handle = app.handle().clone();
            let player_for_tick = player.clone();
            tauri::async_runtime::spawn(async move {
                let mut last_track_id: Option<String> = None;
                loop {
                    tokio::time::sleep(Duration::from_millis(100)).await;
                    let snap = player_for_tick.snapshot();
                    let payload = PlaybackState {
                        track_id: snap.track_id.clone(),
                        position_ms: snap.position_ms,
                        is_playing: snap.is_playing,
                    };
                    let _ = handle.emit("progress", &payload);
                    if snap.is_finished {
                        if let Some(idx) = player_for_tick.compute_next_index(true) {
                            let queue_id = {
                                let state: tauri::State<AppState> = handle.state();
                                let tracks = state.tracks.lock();
                                tracks.get(idx).map(|t| t.id.clone())
                            };
                            if let Some(id) = queue_id {
                                let _ = player_for_tick.play_track_id(&id);
                                let _ = handle.emit("track_changed", &id);
                                last_track_id = Some(id);
                                continue;
                            }
                        }
                    }
                    if snap.track_id != last_track_id {
                        last_track_id = snap.track_id.clone();
                        let _ = handle.emit("track_changed", &snap.track_id);
                    }
                }
            });

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            load_config, save_config, scan_folder,
            play, toggle_pause, next_track, prev_track,
            seek, set_volume, set_mode, fetch_cover,
            lan_sync_status, set_lan_sync,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
