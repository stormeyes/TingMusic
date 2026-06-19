export type Mode = "sequential" | "random" | "repeatone";
export type CoverShape = "circle" | "square";
export type Theme = "default" | "whitered";

export interface LyricLine { time_ms: number; text: string }

export type Lyrics =
  | { kind: "synced"; value: LyricLine[] }
  | { kind: "plain"; value: string };

export interface Track {
  id: string;
  title: string;
  artist: string;
  album: string;
  duration_ms: number;
  cover_data_url: string | null;
  lyrics: Lyrics | null;
}

export interface ScanResult { tracks: Track[]; skipped: number }

export interface PlaybackState {
  track_id: string | null;
  position_ms: number;
  is_playing: boolean;
}

export interface Config {
  folder: string | null;
  volume: number;
  mode: Mode;
  cover_shape: CoverShape;
  theme: Theme;
  lan_sync: boolean;
  always_on_top: boolean;
}

export interface LanSyncStatus {
  enabled: boolean;
  address: string | null;
}

/**
 * saveConfig 不带 lan_sync / always_on_top —— 它们由各自的独立命令管理,
 * 后端在 save_config 里会强制保留已有值。
 */
export type SaveableConfig = Omit<Config, "lan_sync" | "always_on_top">;
