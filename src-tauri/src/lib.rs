pub mod types;
pub mod config;
pub mod lyrics;
pub mod scanner;
pub mod player;
pub mod cover_fetch;
pub mod sync_server;

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
use crate::player::Player;
use crate::scanner::scan;
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
}

#[tauri::command]
fn load_config(state: State<'_, AppState>) -> Config {
    state.config.lock().clone()
}

#[tauri::command]
fn save_config(cfg: Config, state: State<'_, AppState>) -> Result<(), String> {
    write_to(&state.config_path, &cfg).map_err(|e| e.to_string())?;
    *state.config.lock() = cfg;
    Ok(())
}

#[tauri::command]
fn scan_folder(folder: String, state: State<'_, AppState>) -> ScanResult {
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
            let state = AppState {
                player: player.clone(),
                tracks: Mutex::new(Vec::new()),
                config_path: cfg_path,
                config: Mutex::new(cfg.clone()),
                cover_cache_dir,
            };
            app.manage(state);

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
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
