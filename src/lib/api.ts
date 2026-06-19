import { invoke } from "@tauri-apps/api/core";
import type { Config, LanSyncStatus, Mode, SaveableConfig, ScanResult } from "./types";

export const api = {
  loadConfig: () => invoke<Config>("load_config"),
  saveConfig: (cfg: SaveableConfig) => invoke<void>("save_config", { cfg }),
  scanFolder: (folder: string) => invoke<ScanResult>("scan_folder", { folder }),
  play: (trackId: string) => invoke<void>("play", { trackId }),
  togglePause: () => invoke<void>("toggle_pause"),
  next: () => invoke<void>("next_track"),
  prev: () => invoke<void>("prev_track"),
  seek: (positionMs: number) => invoke<void>("seek", { positionMs }),
  setVolume: (volume: number) => invoke<void>("set_volume", { volume }),
  setMode: (mode: Mode) => invoke<void>("set_mode", { mode }),
  fetchCover: (trackId: string, title: string, artist: string) =>
    invoke<string | null>("fetch_cover", { trackId, title, artist }),
  lanSyncStatus: () => invoke<LanSyncStatus>("lan_sync_status"),
  setLanSync: (enabled: boolean) => invoke<void>("set_lan_sync", { enabled }),
  setAlwaysOnTop: (enabled: boolean) => invoke<void>("set_always_on_top", { enabled }),
};
