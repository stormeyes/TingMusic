use crate::lyrics;
use crate::types::{Track, track_id_for_path};
use std::path::{Path, PathBuf};
use walkdir::WalkDir;

const SUPPORTED_EXTS: &[&str] = &["mp3", "flac", "wav", "ogg", "m4a", "aac"];

pub struct ScanOutput {
    pub tracks: Vec<Track>,
    pub skipped: u32,
}

pub fn scan(folder: &Path) -> ScanOutput {
    let mut tracks: Vec<Track> = Vec::new();
    let mut skipped: u32 = 0;
    for entry in WalkDir::new(folder).follow_links(false).into_iter().filter_map(|e| e.ok()) {
        if !entry.file_type().is_file() { continue; }
        let path = entry.into_path();
        let ext = match path.extension().and_then(|e| e.to_str()) {
            Some(e) => e.to_lowercase(),
            None => continue,
        };
        if !SUPPORTED_EXTS.contains(&ext.as_str()) { continue; }
        match build_track(&path) {
            Some(t) => tracks.push(t),
            None => skipped += 1,
        }
    }
    tracks.sort_by(|a, b| a.path.cmp(&b.path));
    ScanOutput { tracks, skipped }
}

fn build_track(path: &Path) -> Option<Track> {
    use lofty::file::{AudioFile, TaggedFileExt};
    use lofty::read_from_path;
    use lofty::tag::ItemKey;
    use lofty::picture::MimeType;
    use base64::Engine;

    let id = track_id_for_path(path);
    let stem_title = path.file_stem().map(|s| s.to_string_lossy().into_owned()).unwrap_or_else(|| "Unknown".into());

    let tagged = match read_from_path(path) {
        Ok(t) => t,
        Err(_) => {
            let (file_title, file_artist) = parse_filename_stem(&stem_title);
            return Some(Track {
                id,
                path: path.to_path_buf(),
                title: file_title,
                artist: file_artist.unwrap_or_else(|| "Unknown".into()),
                album: "Unknown".into(),
                duration_ms: 0,
                cover_data_url: None,
                lyrics: lyrics::load_sidecar_lrc(path),
            });
        }
    };
    let duration_ms = tagged.properties().duration().as_millis() as u64;
    let tag = tagged.primary_tag().or_else(|| tagged.first_tag());
    let id3_title = tag.and_then(|t| t.get_string(&ItemKey::TrackTitle))
        .map(|s| s.to_string()).filter(|s| !s.is_empty());
    let id3_artist = tag.and_then(|t| t.get_string(&ItemKey::TrackArtist))
        .map(|s| s.to_string()).filter(|s| !s.is_empty());
    // Only apply the filename "Title-Artist" heuristic when the file has no
    // useful ID3 at all — otherwise we'd risk butchering legit titles that
    // happen to contain a hyphen (AC-DC, etc.).
    let (parsed_title, parsed_artist) = if id3_title.is_none() && id3_artist.is_none() {
        parse_filename_stem(&stem_title)
    } else {
        (stem_title.clone(), None)
    };
    let title = id3_title.unwrap_or(parsed_title);
    let artist = id3_artist.unwrap_or_else(|| parsed_artist.unwrap_or_else(|| "Unknown".into()));
    let album = tag.and_then(|t| t.get_string(&ItemKey::AlbumTitle))
        .map(|s| s.to_string()).filter(|s| !s.is_empty()).unwrap_or_else(|| "Unknown".into());

    let cover_data_url = tag.and_then(|t| t.pictures().first().cloned()).map(|pic| {
        let mime = match pic.mime_type() {
            Some(MimeType::Jpeg) => "image/jpeg",
            Some(MimeType::Png)  => "image/png",
            Some(MimeType::Gif)  => "image/gif",
            _ => "image/jpeg",
        };
        let b64 = base64::engine::general_purpose::STANDARD.encode(pic.data());
        format!("data:{mime};base64,{b64}")
    });

    Some(Track {
        id,
        path: path.to_path_buf(),
        title,
        artist,
        album,
        duration_ms,
        cover_data_url,
        lyrics: lyrics::load_lyrics(path),
    })
}

#[allow(dead_code)]
fn extensions_for_test() -> &'static [&'static str] { SUPPORTED_EXTS }

/// Parse a filename stem of the shape `Title-Artist` (rightmost hyphen is
/// the separator). Used as a last-resort fallback when ID3 has no metadata.
/// Strips a trailing `#hash` cache-buster off the artist if present.
fn parse_filename_stem(stem: &str) -> (String, Option<String>) {
    if let Some(idx) = stem.rfind('-') {
        let title = stem[..idx].trim().to_string();
        let mut artist = stem[idx + 1..].trim().to_string();
        if let Some(h) = artist.rfind('#') {
            artist = artist[..h].trim_end().to_string();
        }
        if !title.is_empty() && !artist.is_empty() {
            return (title, Some(artist));
        }
    }
    (stem.to_string(), None)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs::{self, File};
    use std::io::Write;
    use tempfile::tempdir;

    fn write_dummy(dir: &Path, name: &str) -> PathBuf {
        let path = dir.join(name);
        let mut f = File::create(&path).unwrap();
        f.write_all(b"not a real audio file").unwrap();
        path
    }

    #[test]
    fn filters_by_extension() {
        let dir = tempdir().unwrap();
        write_dummy(dir.path(), "a.mp3");
        write_dummy(dir.path(), "b.txt");
        write_dummy(dir.path(), "c.FLAC");
        write_dummy(dir.path(), "no_ext");
        let result = scan(dir.path());
        let names: Vec<_> = result.tracks.iter()
            .map(|t| t.path.file_name().unwrap().to_string_lossy().into_owned())
            .collect();
        assert!(names.iter().any(|n| n == "a.mp3"));
        assert!(names.iter().any(|n| n == "c.FLAC"));
        assert!(!names.iter().any(|n| n == "b.txt"));
    }

    #[test]
    fn empty_folder_returns_empty() {
        let dir = tempdir().unwrap();
        let result = scan(dir.path());
        assert_eq!(result.tracks.len(), 0);
        assert_eq!(result.skipped, 0);
    }

    #[test]
    fn falls_back_to_filename_when_metadata_missing() {
        let dir = tempdir().unwrap();
        let path = write_dummy(dir.path(), "MyTrack.mp3");
        let result = scan(dir.path());
        let t = result.tracks.iter().find(|t| t.path == path).unwrap();
        assert_eq!(t.title, "MyTrack");
        assert_eq!(t.artist, "Unknown");
    }

    #[test]
    fn recurses_into_subdirs() {
        let dir = tempdir().unwrap();
        let sub = dir.path().join("sub");
        fs::create_dir(&sub).unwrap();
        write_dummy(&sub, "deep.mp3");
        let result = scan(dir.path());
        assert_eq!(result.tracks.len(), 1);
    }

    #[test]
    fn filename_title_artist_split_on_last_hyphen() {
        let dir = tempdir().unwrap();
        let path = write_dummy(dir.path(), "Butter Fly-和田光司.mp3");
        let result = scan(dir.path());
        let t = result.tracks.iter().find(|t| t.path == path).unwrap();
        assert_eq!(t.title, "Butter Fly");
        assert_eq!(t.artist, "和田光司");
    }

    #[test]
    fn filename_strips_hash_suffix_from_artist() {
        let dir = tempdir().unwrap();
        let path = write_dummy(dir.path(), "Song-Singer#abc123.mp3");
        let result = scan(dir.path());
        let t = result.tracks.iter().find(|t| t.path == path).unwrap();
        assert_eq!(t.title, "Song");
        assert_eq!(t.artist, "Singer");
    }

    #[test]
    fn parse_filename_stem_no_hyphen() {
        assert_eq!(parse_filename_stem("MyTrack"), ("MyTrack".to_string(), None));
    }
}
