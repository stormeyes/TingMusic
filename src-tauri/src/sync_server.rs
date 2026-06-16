use serde::Serialize;
use std::path::{Component, Path, PathBuf};
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
}
