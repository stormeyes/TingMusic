use crate::types::{Mode, Track};
use anyhow::{Context, Result};
use parking_lot::Mutex;
use rand::seq::SliceRandom;
use rodio::{Decoder, DeviceSinkBuilder, MixerDeviceSink, Player as RodioPlayer, Source};
use std::fs::File;
use std::io::BufReader;
use std::sync::Arc;
use std::time::{Duration, Instant};

/// rodio's MixerDeviceSink (backed by cpal) is !Send + !Sync on macOS because
/// CoreAudio stores a raw pointer in the stream internals. We only keep it
/// alive (never access it from another thread), so asserting Send+Sync is
/// safe here.
struct SendDevice(MixerDeviceSink);
// SAFETY: The device sink is stored only to keep the audio backend alive for
// the life of the Player. We never invoke any methods on the inner cpal::Stream
// from any thread. The sink may be dropped on whichever thread holds the last
// Arc<Player> reference; CoreAudio / cpal handle cross-thread dealloc safely.
unsafe impl Send for SendDevice {}
unsafe impl Sync for SendDevice {}

pub struct Player {
    inner: Arc<Mutex<Inner>>,
    device: SendDevice,
}

struct Inner {
    queue: Vec<Track>,
    current_index: Option<usize>,
    sink: Option<RodioPlayer>,
    started_at: Option<Instant>,
    paused_position_ms: u64,
    volume: f32,
    mode: Mode,
    last_random: Option<usize>,
}

#[derive(Debug, Clone)]
pub struct Snapshot {
    pub track_id: Option<String>,
    pub position_ms: u64,
    pub is_playing: bool,
    pub is_finished: bool,
}

impl Player {
    pub fn new(volume: f32, mode: Mode) -> Result<Self> {
        let device = DeviceSinkBuilder::open_default_sink()
            .map_err(|e| anyhow::anyhow!("failed to open default audio output: {e:?}"))?;
        Ok(Self {
            inner: Arc::new(Mutex::new(Inner {
                queue: Vec::new(),
                current_index: None,
                sink: None,
                started_at: None,
                paused_position_ms: 0,
                volume,
                mode,
                last_random: None,
            })),
            device: SendDevice(device),
        })
    }

    pub fn set_queue(&self, tracks: Vec<Track>) {
        let mut inner = self.inner.lock();
        inner.queue = tracks;
        inner.current_index = None;
        inner.sink = None;
        inner.started_at = None;
        inner.paused_position_ms = 0;
        inner.last_random = None;
    }

    pub fn set_volume(&self, v: f32) {
        let mut inner = self.inner.lock();
        let v = v.clamp(0.0, 1.0);
        inner.volume = v;
        if let Some(sink) = inner.sink.as_ref() {
            sink.set_volume(v);
        }
    }

    pub fn set_mode(&self, m: Mode) {
        let mut inner = self.inner.lock();
        inner.mode = m;
    }

    pub fn play_track_id(&self, track_id: &str) -> Result<()> {
        let idx = {
            let inner = self.inner.lock();
            inner.queue.iter().position(|t| t.id == track_id)
                .context("track id not in current queue")?
        };
        self.play_index(idx)
    }

    pub fn toggle_pause(&self) {
        let mut inner = self.inner.lock();
        let paused = inner.sink.as_ref().map(|s| s.is_paused());
        match paused {
            Some(true) => {
                if let Some(sink) = inner.sink.as_ref() { sink.play(); }
                inner.started_at = Some(Instant::now());
            }
            Some(false) => {
                let pos = current_position_ms(&inner);
                inner.paused_position_ms = pos;
                if let Some(sink) = inner.sink.as_ref() { sink.pause(); }
                inner.started_at = None;
            }
            None => {}
        }
    }

    pub fn next(&self) -> Result<()> {
        let next = self.compute_next_index(false);
        match next {
            Some(i) => self.play_index(i),
            None => {
                self.stop();
                Ok(())
            }
        }
    }

    pub fn prev(&self) -> Result<()> {
        let prev = {
            let inner = self.inner.lock();
            match inner.current_index {
                Some(0) | None => Some(0),
                Some(i) => Some(i - 1),
            }
        };
        if let Some(i) = prev {
            if !self.inner.lock().queue.is_empty() {
                return self.play_index(i);
            }
        }
        Ok(())
    }

    pub fn seek(&self, ms: u64) -> Result<()> {
        let idx = self.inner.lock().current_index;
        let Some(i) = idx else { return Ok(()); };
        let track = match self.inner.lock().queue.get(i).cloned() {
            Some(t) => t,
            None => return Ok(()),
        };
        let target = Duration::from_millis(ms.min(track.duration_ms.saturating_sub(100).max(0)));

        // Fast path: ask the existing sink to seek in place. rodio delegates to
        // the underlying symphonia decoder which uses real seek tables for
        // mp3/flac/aac/wav and returns nearly instantly.
        {
            let inner = self.inner.lock();
            if let Some(sink) = inner.sink.as_ref() {
                if sink.try_seek(target).is_ok() {
                    drop(inner);
                    let mut inner = self.inner.lock();
                    inner.paused_position_ms = target.as_millis() as u64;
                    inner.started_at = Some(Instant::now());
                    return Ok(());
                }
            }
        }

        // Fallback for formats whose decoder does not implement seek: rebuild
        // the sink from scratch with skip_duration. Slower but always works.
        let new_sink = self.build_sink(&track, target)?;
        let mut inner = self.inner.lock();
        if let Some(old) = inner.sink.take() { old.stop(); }
        inner.sink = Some(new_sink);
        inner.paused_position_ms = target.as_millis() as u64;
        inner.started_at = Some(Instant::now());
        Ok(())
    }

    pub fn snapshot(&self) -> Snapshot {
        let inner = self.inner.lock();
        let track_id = inner.current_index.map(|i| inner.queue[i].id.clone());
        let is_playing = inner.sink.as_ref().map(|s| !s.is_paused() && !s.empty()).unwrap_or(false);
        let is_finished = inner.sink.as_ref().map(|s| s.empty()).unwrap_or(false) && inner.current_index.is_some();
        Snapshot {
            track_id,
            position_ms: current_position_ms(&inner),
            is_playing,
            is_finished,
        }
    }

    fn stop(&self) {
        let mut inner = self.inner.lock();
        if let Some(sink) = inner.sink.take() { sink.stop(); }
        inner.started_at = None;
        inner.paused_position_ms = 0;
        inner.current_index = None;
    }

    fn play_index(&self, idx: usize) -> Result<()> {
        let track = {
            let inner = self.inner.lock();
            inner.queue.get(idx).cloned().context("index out of range")?
        };
        let new_sink = self.build_sink(&track, Duration::ZERO)?;
        let mut inner = self.inner.lock();
        if let Some(old) = inner.sink.take() { old.stop(); }
        inner.sink = Some(new_sink);
        inner.current_index = Some(idx);
        inner.started_at = Some(Instant::now());
        inner.paused_position_ms = 0;
        Ok(())
    }

    fn build_sink(&self, track: &Track, skip: Duration) -> Result<RodioPlayer> {
        // rodio 0.19's symphonia decoder has an internal unreachable!() that
        // fires on certain m4a/mp4 files during initialization (fixed
        // upstream in 0.20+). Catch the panic so a malformed file returns
        // Err instead of taking the whole app down.
        let path = track.path.clone();
        let source = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let file = File::open(&path)
                .with_context(|| format!("open {:?}", path))?;
            Decoder::new(BufReader::new(file))
                .with_context(|| format!("decode {:?}", path))
        }))
        .map_err(|_| anyhow::anyhow!("decoder panicked while opening {:?} — the file is probably malformed or uses a codec rodio can't handle", track.path))??;
        let sink = RodioPlayer::connect_new(self.device.0.mixer());
        sink.set_volume(self.inner.lock().volume);
        if skip > Duration::ZERO {
            sink.append(source.skip_duration(skip));
        } else {
            sink.append(source);
        }
        sink.play();
        Ok(sink)
    }

    pub fn compute_next_index(&self, advance_on_finish: bool) -> Option<usize> {
        let mut inner = self.inner.lock();
        if inner.queue.is_empty() { return None; }
        let cur = inner.current_index;
        match inner.mode {
            Mode::RepeatOne if advance_on_finish => cur,
            Mode::Sequential | Mode::RepeatOne => {
                // Sequential wraps at the end of the queue (the user expects
                // the playlist to loop forever, not stop). Manual next from
                // RepeatOne uses the same wrap.
                let len = inner.queue.len();
                let next = cur.map(|i| (i + 1) % len).unwrap_or(0);
                Some(next)
            }
            Mode::Random => {
                let len = inner.queue.len();
                if len == 1 { return Some(0); }
                let mut rng = rand::thread_rng();
                let mut candidates: Vec<usize> = (0..len).collect();
                if let Some(c) = cur { candidates.retain(|&i| i != c); }
                if let Some(last) = inner.last_random {
                    if candidates.len() > 1 { candidates.retain(|&i| i != last); }
                }
                let pick = *candidates.choose(&mut rng).unwrap_or(&0);
                inner.last_random = Some(pick);
                Some(pick)
            }
        }
    }
}

fn current_position_ms(inner: &Inner) -> u64 {
    let base = inner.paused_position_ms;
    if let (Some(sink), Some(started)) = (inner.sink.as_ref(), inner.started_at) {
        if !sink.is_paused() {
            return base + started.elapsed().as_millis() as u64;
        }
    }
    base
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::types::{Mode, Track};
    use std::path::PathBuf;

    fn fake_track(id: &str) -> Track {
        Track {
            id: id.into(),
            path: PathBuf::from(format!("/tmp/{id}.mp3")),
            title: id.into(),
            artist: "A".into(),
            album: "Al".into(),
            duration_ms: 1000,
            cover_data_url: None,
            lyrics: None,
        }
    }

    fn make_player() -> Option<Player> {
        // CI / headless boxes may have no audio device; skip the test instead of failing.
        Player::new(0.5, Mode::Sequential).ok()
    }

    #[test]
    fn next_in_sequential_wraps_at_end() {
        let Some(p) = make_player() else { return; };
        p.set_queue(vec![fake_track("a"), fake_track("b"), fake_track("c")]);
        // Manually pin current_index so we don't actually try to decode fake files.
        p.inner.lock().current_index = Some(0);
        assert_eq!(p.compute_next_index(false), Some(1));
        p.inner.lock().current_index = Some(1);
        assert_eq!(p.compute_next_index(false), Some(2));
        p.inner.lock().current_index = Some(2);
        assert_eq!(p.compute_next_index(false), Some(0));
    }

    #[test]
    fn repeat_one_replays_same_index_on_finish() {
        let Some(p) = make_player() else { return; };
        p.set_queue(vec![fake_track("a"), fake_track("b")]);
        p.set_mode(Mode::RepeatOne);
        p.inner.lock().current_index = Some(1);
        assert_eq!(p.compute_next_index(true), Some(1));
    }

    #[test]
    fn random_does_not_immediately_repeat_current() {
        let Some(p) = make_player() else { return; };
        let q: Vec<Track> = (0..10).map(|i| fake_track(&format!("t{i}"))).collect();
        p.set_queue(q);
        p.set_mode(Mode::Random);
        p.inner.lock().current_index = Some(3);
        for _ in 0..50 {
            let n = p.compute_next_index(false).unwrap();
            assert_ne!(n, 3, "random should not pick current immediately");
        }
    }

    #[test]
    fn set_volume_clamps() {
        let Some(p) = make_player() else { return; };
        p.set_volume(2.0);
        assert!((p.inner.lock().volume - 1.0).abs() < 1e-6);
        p.set_volume(-1.0);
        assert!((p.inner.lock().volume - 0.0).abs() < 1e-6);
    }
}
