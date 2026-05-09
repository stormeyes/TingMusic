use serde::{Deserialize, Serialize};
use std::path::PathBuf;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Mode {
    Sequential,
    Random,
    RepeatOne,
}

impl Default for Mode {
    fn default() -> Self { Mode::Sequential }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LyricLine {
    pub time_ms: u64,
    pub text: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "kind", content = "value", rename_all = "lowercase")]
pub enum Lyrics {
    Synced(Vec<LyricLine>),
    Plain(String),
}

#[derive(Debug, Clone)]
pub struct Track {
    pub id: String,
    pub path: PathBuf,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub duration_ms: u64,
    pub cover_data_url: Option<String>,
    pub lyrics: Option<Lyrics>,
}

#[derive(Debug, Clone, Serialize)]
pub struct TrackDto {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub duration_ms: u64,
    pub cover_data_url: Option<String>,
    pub lyrics: Option<Lyrics>,
}

impl From<&Track> for TrackDto {
    fn from(t: &Track) -> Self {
        Self {
            id: t.id.clone(),
            title: t.title.clone(),
            artist: t.artist.clone(),
            album: t.album.clone(),
            duration_ms: t.duration_ms,
            cover_data_url: t.cover_data_url.clone(),
            lyrics: t.lyrics.clone(),
        }
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct PlaybackState {
    pub track_id: Option<String>,
    pub position_ms: u64,
    pub is_playing: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Config {
    pub folder: Option<String>,
    pub volume: f32,
    pub mode: Mode,
    #[serde(default)]
    pub cover_shape: CoverShape,
    #[serde(default)]
    pub theme: Theme,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum CoverShape {
    Circle,
    Square,
}

impl Default for CoverShape {
    fn default() -> Self { CoverShape::Circle }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Theme {
    Default,
    Whitered,
}

impl Default for Theme {
    fn default() -> Self { Theme::Default }
}

impl Default for Config {
    fn default() -> Self {
        Self {
            folder: None,
            volume: 0.7,
            mode: Mode::Sequential,
            cover_shape: CoverShape::Circle,
            theme: Theme::Default,
        }
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct ScanResult {
    pub tracks: Vec<TrackDto>,
    pub skipped: u32,
}

pub fn track_id_for_path(path: &std::path::Path) -> String {
    use sha2::{Digest, Sha256};
    let mut hasher = Sha256::new();
    hasher.update(path.to_string_lossy().as_bytes());
    let digest = hasher.finalize();
    hex::encode(&digest[..8])
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn track_id_is_deterministic() {
        let a = track_id_for_path(std::path::Path::new("/music/song.mp3"));
        let b = track_id_for_path(std::path::Path::new("/music/song.mp3"));
        assert_eq!(a, b);
        assert_eq!(a.len(), 16);
    }

    #[test]
    fn track_id_differs_per_path() {
        let a = track_id_for_path(std::path::Path::new("/music/a.mp3"));
        let b = track_id_for_path(std::path::Path::new("/music/b.mp3"));
        assert_ne!(a, b);
    }

    #[test]
    fn config_default_values() {
        let c = Config::default();
        assert_eq!(c.folder, None);
        assert!((c.volume - 0.7).abs() < 1e-6);
        assert_eq!(c.mode, Mode::Sequential);
    }

    #[test]
    fn mode_serializes_lowercase() {
        let s = serde_json::to_string(&Mode::RepeatOne).unwrap();
        assert_eq!(s, "\"repeatone\"");
    }

    #[test]
    fn track_dto_drops_path() {
        let t = Track {
            id: "abc".into(),
            path: PathBuf::from("/secret/path.mp3"),
            title: "T".into(),
            artist: "A".into(),
            album: "Al".into(),
            duration_ms: 1000,
            cover_data_url: None,
            lyrics: None,
        };
        let dto = TrackDto::from(&t);
        let json = serde_json::to_string(&dto).unwrap();
        assert!(!json.contains("/secret/path.mp3"));
        assert!(json.contains("\"id\":\"abc\""));
    }
}
