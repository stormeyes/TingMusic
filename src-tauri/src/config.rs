use crate::types::Config;
use anyhow::Result;
use std::path::Path;

pub fn read_from(path: &Path) -> Config {
    match std::fs::read_to_string(path) {
        Ok(text) => match serde_json::from_str(&text) {
            Ok(cfg) => cfg,
            Err(_) => {
                let cfg = Config::default();
                let _ = write_to(path, &cfg);
                cfg
            }
        },
        Err(_) => Config::default(),
    }
}

pub fn write_to(path: &Path, cfg: &Config) -> Result<()> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    let text = serde_json::to_string_pretty(cfg)?;
    std::fs::write(path, text)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::types::Mode;
    use tempfile::tempdir;

    #[test]
    fn missing_file_returns_default() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("config.json");
        let cfg = read_from(&path);
        assert_eq!(cfg.folder, None);
        assert_eq!(cfg.mode, Mode::Sequential);
    }

    #[test]
    fn corrupt_file_returns_default() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("config.json");
        std::fs::write(&path, "{not valid json").unwrap();
        let cfg = read_from(&path);
        assert_eq!(cfg.mode, Mode::Sequential);
    }

    #[test]
    fn corrupt_file_is_overwritten_with_default() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("config.json");
        std::fs::write(&path, "{not valid json").unwrap();
        let _ = read_from(&path);
        let after = std::fs::read_to_string(&path).unwrap();
        assert!(after.contains("\"folder\""));
        assert!(after.contains("\"volume\""));
    }

    #[test]
    fn round_trip() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("nested/config.json");
        let cfg = Config {
            folder: Some("/music".into()),
            volume: 0.42,
            mode: Mode::RepeatOne,
            cover_shape: crate::types::CoverShape::Square,
            theme: crate::types::Theme::Whitered,
        };
        write_to(&path, &cfg).unwrap();
        let back = read_from(&path);
        assert_eq!(back.folder.as_deref(), Some("/music"));
        assert!((back.volume - 0.42).abs() < 1e-6);
        assert_eq!(back.mode, Mode::RepeatOne);
    }
}
