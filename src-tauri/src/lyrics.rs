use crate::types::{LyricLine, Lyrics};
use std::path::Path;

const PARSE_FAIL_RATIO: f32 = 0.5;

pub fn parse_lrc(text: &str) -> Lyrics {
    let trimmed = text.trim_start_matches('\u{FEFF}');
    let mut lines: Vec<LyricLine> = Vec::new();
    let mut total_lines = 0u32;
    let mut bad_lines = 0u32;

    for raw in trimmed.lines() {
        let raw = raw.trim();
        if raw.is_empty() { continue; }
        total_lines += 1;
        match parse_lrc_line(raw) {
            Some(parsed) => lines.extend(parsed),
            None => bad_lines += 1,
        }
    }

    if total_lines == 0 {
        return Lyrics::Plain(text.to_string());
    }
    if (bad_lines as f32) / (total_lines as f32) > PARSE_FAIL_RATIO {
        return Lyrics::Plain(text.to_string());
    }
    if lines.is_empty() {
        return Lyrics::Plain(text.to_string());
    }

    lines.sort_by_key(|l| l.time_ms);
    Lyrics::Synced(lines)
}

fn parse_lrc_line(raw: &str) -> Option<Vec<LyricLine>> {
    if !raw.starts_with('[') { return None; }
    let mut rest = raw;
    let mut times: Vec<u64> = Vec::new();
    while rest.starts_with('[') {
        let end = rest.find(']')?;
        let tag = &rest[1..end];
        rest = &rest[end + 1..];
        if let Some(ms) = parse_timestamp(tag) {
            times.push(ms);
        } else if is_metadata_tag(tag) {
            // [ti:..] [ar:..] [offset:..] etc — silently consume
            continue;
        } else {
            return None;
        }
    }
    if times.is_empty() { return Some(Vec::new()); }
    let text = rest.trim().to_string();
    Some(times.into_iter().map(|t| LyricLine { time_ms: t, text: text.clone() }).collect())
}

fn parse_timestamp(tag: &str) -> Option<u64> {
    // Accept mm:ss.xx, mm:ss.xxx, mm:ss
    let (mm, rest) = tag.split_once(':')?;
    let minutes: u64 = mm.parse().ok()?;
    let (sec_str, frac_str) = match rest.split_once('.') {
        Some((s, f)) => (s, f),
        None => (rest, "0"),
    };
    let seconds: u64 = sec_str.parse().ok()?;
    if seconds >= 60 { return None; }
    let frac_digits = frac_str.len().min(3);
    let frac: u64 = frac_str[..frac_digits].parse().ok()?;
    let frac_ms = match frac_digits {
        0 => 0,
        1 => frac * 100,
        2 => frac * 10,
        3 => frac,
        _ => 0,
    };
    Some(minutes * 60_000 + seconds * 1000 + frac_ms)
}

fn is_metadata_tag(tag: &str) -> bool {
    matches!(tag.split(':').next().unwrap_or(""),
        "ti" | "ar" | "al" | "by" | "offset" | "re" | "ve" | "au" | "length")
}

pub fn load_sidecar_lrc(audio_path: &Path) -> Option<Lyrics> {
    let parent = audio_path.parent()?;
    let stem = audio_path.file_stem()?.to_string_lossy().to_lowercase();
    let stem_norm = strip_hash_suffix(&stem);
    let mut fallback: Option<std::path::PathBuf> = None;
    for entry in std::fs::read_dir(parent).ok()?.flatten() {
        let p = entry.path();
        if p.extension().and_then(|e| e.to_str()).map(|e| e.to_lowercase()) != Some("lrc".into()) {
            continue;
        }
        let Some(candidate_stem_os) = p.file_stem() else { continue };
        let candidate_stem = candidate_stem_os.to_string_lossy().to_lowercase();
        if candidate_stem == stem {
            return std::fs::read_to_string(&p).ok().map(|t| parse_lrc(&t));
        }
        if fallback.is_none() && strip_hash_suffix(&candidate_stem) == stem_norm {
            fallback = Some(p);
        }
    }
    let path = fallback?;
    std::fs::read_to_string(&path).ok().map(|t| parse_lrc(&t))
}

// Strips a trailing `#...` segment (and any whitespace before it) from a file
// stem. Common downloader tools (网易云 / QQ 音乐) append a cache hash like
// `Song-Artist#2ryCf3.mp3` while the matching `.lrc` is named without it.
fn strip_hash_suffix(s: &str) -> String {
    match s.rfind('#') {
        Some(idx) => s[..idx].trim_end().to_string(),
        None => s.to_string(),
    }
}

pub fn load_lyrics(audio_path: &Path) -> Option<Lyrics> {
    if let Some(l) = load_sidecar_lrc(audio_path) {
        return Some(l);
    }
    load_embedded_lyrics(audio_path)
}

fn load_embedded_lyrics(audio_path: &Path) -> Option<Lyrics> {
    use lofty::file::TaggedFileExt;
    use lofty::read_from_path;
    use lofty::tag::ItemKey;

    let tagged = read_from_path(audio_path).ok()?;
    let tag = tagged.primary_tag().or_else(|| tagged.first_tag())?;
    let lyrics_text = tag.get_string(&ItemKey::Lyrics)?;
    if lyrics_text.trim().is_empty() {
        return None;
    }
    Some(parse_lrc(lyrics_text))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_basic_synced_lrc() {
        let text = "[00:01.00]Hello\n[00:02.50]World";
        let l = parse_lrc(text);
        match l {
            Lyrics::Synced(v) => {
                assert_eq!(v.len(), 2);
                assert_eq!(v[0].time_ms, 1000);
                assert_eq!(v[0].text, "Hello");
                assert_eq!(v[1].time_ms, 2500);
            }
            _ => panic!("expected synced"),
        }
    }

    #[test]
    fn handles_metadata_header() {
        let text = "[ti:Song]\n[ar:Artist]\n[00:01.00]Line";
        match parse_lrc(text) {
            Lyrics::Synced(v) => assert_eq!(v.len(), 1),
            _ => panic!(),
        }
    }

    #[test]
    fn multi_timestamp_line_yields_multiple_entries() {
        let text = "[00:01.00][00:05.00]Repeat";
        match parse_lrc(text) {
            Lyrics::Synced(v) => {
                assert_eq!(v.len(), 2);
                assert_eq!(v[0].time_ms, 1000);
                assert_eq!(v[1].time_ms, 5000);
            }
            _ => panic!(),
        }
    }

    #[test]
    fn sorts_unordered_input() {
        let text = "[00:05.00]Late\n[00:01.00]Early";
        match parse_lrc(text) {
            Lyrics::Synced(v) => {
                assert_eq!(v[0].text, "Early");
                assert_eq!(v[1].text, "Late");
            }
            _ => panic!(),
        }
    }

    #[test]
    fn handles_bom() {
        let text = "\u{FEFF}[00:01.00]Hi";
        match parse_lrc(text) {
            Lyrics::Synced(v) => assert_eq!(v[0].text, "Hi"),
            _ => panic!(),
        }
    }

    #[test]
    fn degrades_to_plain_when_majority_corrupt() {
        let text = "garbage1\ngarbage2\ngarbage3\n[00:01.00]Good";
        match parse_lrc(text) {
            Lyrics::Plain(_) => (),
            _ => panic!("expected plain fallback"),
        }
    }

    #[test]
    fn empty_input_is_plain() {
        match parse_lrc("") {
            Lyrics::Plain(s) => assert_eq!(s, ""),
            _ => panic!(),
        }
    }

    #[test]
    fn sidecar_lookup_is_case_insensitive() {
        let dir = tempfile::tempdir().unwrap();
        let audio = dir.path().join("Song.mp3");
        std::fs::write(&audio, b"x").unwrap();
        let lrc = dir.path().join("song.LRC");
        std::fs::write(&lrc, "[00:01.00]Hi").unwrap();
        let result = load_sidecar_lrc(&audio);
        assert!(matches!(result, Some(Lyrics::Synced(_))));
    }

    #[test]
    fn load_lyrics_returns_none_when_no_sidecar_and_no_tag() {
        let dir = tempfile::tempdir().unwrap();
        let audio = dir.path().join("none.mp3");
        std::fs::write(&audio, b"not a real mp3").unwrap();
        let result = load_lyrics(&audio);
        assert!(result.is_none());
    }

    #[test]
    fn sidecar_matches_when_audio_has_hash_suffix() {
        let dir = tempfile::tempdir().unwrap();
        let audio = dir.path().join("NIGHT DANCER-imase#2ryCf3.mp3");
        std::fs::write(&audio, b"x").unwrap();
        let lrc = dir.path().join("NIGHT DANCER-imase.lrc");
        std::fs::write(&lrc, "[00:01.00]Hi").unwrap();
        assert!(matches!(load_sidecar_lrc(&audio), Some(Lyrics::Synced(_))));
    }

    #[test]
    fn exact_match_wins_over_hash_normalized_match() {
        let dir = tempfile::tempdir().unwrap();
        let audio = dir.path().join("Song#abc.mp3");
        std::fs::write(&audio, b"x").unwrap();
        std::fs::write(dir.path().join("Song.lrc"), "[00:01.00]Wrong").unwrap();
        std::fs::write(dir.path().join("Song#abc.lrc"), "[00:02.00]Right").unwrap();
        match load_sidecar_lrc(&audio).expect("should match") {
            Lyrics::Synced(v) => assert_eq!(v[0].text, "Right"),
            _ => panic!("expected synced"),
        }
    }
}
