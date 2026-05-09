import { useEffect, useState } from "react";
import { listen } from "@tauri-apps/api/event";
import { homeDir, join } from "@tauri-apps/api/path";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { LogicalSize } from "@tauri-apps/api/dpi";
import { useStore } from "./store";
import { api } from "./lib/api";
import type { PlaybackState } from "./lib/types";
import NowPlaying from "./components/NowPlaying";
import Tabs from "./components/Tabs";
import Playlist from "./components/Playlist";
import Lyrics from "./components/Lyrics";

// Window dimensions. Three states: compact (card only), settings (card + room
// for the gear dropdown so it isn't clipped), expanded (card + full panel).
const COMPACT_SIZE = { width: 400, height: 100 };
const SETTINGS_SIZE = { width: 400, height: 580 };
const EXPANDED_SIZE = { width: 400, height: 640 };

export default function App() {
  const view = useStore((s) => s.view);
  const expanded = useStore((s) => s.expanded);
  const settingsOpen = useStore((s) => s.settingsOpen);
  const setPosition = useStore((s) => s.setPosition);
  const setIsPlaying = useStore((s) => s.setIsPlaying);
  const setCurrentTrackId = useStore((s) => s.setCurrentTrackId);
  const setVolume = useStore((s) => s.setVolume);
  const setMode = useStore((s) => s.setMode);
  const setFolder = useStore((s) => s.setFolder);
  const setTracks = useStore((s) => s.setTracks);
  const setSkipped = useStore((s) => s.setSkipped);
  const setCoverShape = useStore((s) => s.setCoverShape);
  const theme = useStore((s) => s.theme);
  const setTheme = useStore((s) => s.setTheme);

  // Keep the panel mounted across the collapse animation so the user sees
  // the window shrink with content still inside, instead of an empty
  // transparent rectangle suddenly closing in.
  const [panelMounted, setPanelMounted] = useState(expanded);
  useEffect(() => {
    if (expanded) {
      setPanelMounted(true);
      return;
    }
    const t = window.setTimeout(() => setPanelMounted(false), 260);
    return () => window.clearTimeout(t);
  }, [expanded]);

  // Animate window resize over ~250ms with an ease-out cubic so expand /
  // collapse feels like a slide instead of a snap.
  useEffect(() => {
    const target = expanded ? EXPANDED_SIZE : settingsOpen ? SETTINGS_SIZE : COMPACT_SIZE;
    const win = getCurrentWindow();
    let cancelled = false;
    let raf = 0;
    (async () => {
      let startHeight = COMPACT_SIZE.height;
      try {
        const factor = await win.scaleFactor();
        const cur = await win.outerSize();
        startHeight = cur.height / factor;
      } catch {}
      if (cancelled) return;
      const dur = 240;
      const t0 = performance.now();
      const tick = () => {
        if (cancelled) return;
        const t = Math.min((performance.now() - t0) / dur, 1);
        const eased = 1 - Math.pow(1 - t, 3);
        const h = Math.round(startHeight + (target.height - startHeight) * eased);
        win.setSize(new LogicalSize(target.width, h)).catch(() => {});
        if (t < 1) raf = requestAnimationFrame(tick);
      };
      raf = requestAnimationFrame(tick);
    })();
    return () => {
      cancelled = true;
      if (raf) cancelAnimationFrame(raf);
    };
  }, [expanded, settingsOpen]);

  useEffect(() => {
    let unlistenProgress: (() => void) | null = null;
    let unlistenTrack: (() => void) | null = null;

    (async () => {
      const cfg = await api.loadConfig();
      setVolume(cfg.volume);
      setMode(cfg.mode);
      setCoverShape(cfg.cover_shape);
      setTheme(cfg.theme);

      // Default folder = $HOME/Music when nothing has been picked yet.
      let activeFolder = cfg.folder;
      if (!activeFolder) {
        try { activeFolder = await join(await homeDir(), "Music"); } catch { activeFolder = null; }
      }
      setFolder(activeFolder);
      if (activeFolder) {
        try {
          const r = await api.scanFolder(activeFolder);
          setTracks(r.tracks);
          setSkipped(r.skipped);
          // Show the first track in the now-playing card so the card has
          // metadata to render even before the user picks anything; we don't
          // start playback — the user clicks the cover to begin.
          if (r.tracks.length > 0) setCurrentTrackId(r.tracks[0].id);
          if (!cfg.folder) {
            await api.saveConfig({ folder: activeFolder, volume: cfg.volume, mode: cfg.mode, cover_shape: cfg.cover_shape, theme: cfg.theme });
          }
        } catch (e) {
          console.error("scan failed", e);
        }
      }
      unlistenProgress = await listen<PlaybackState>("progress", (e) => {
        setPosition(e.payload.position_ms);
        setIsPlaying(e.payload.is_playing);
      });
      unlistenTrack = await listen<string | null>("track_changed", (e) => {
        setCurrentTrackId(e.payload);
      });
    })().catch(console.error);

    return () => { unlistenProgress?.(); unlistenTrack?.(); };
  }, [setPosition, setIsPlaying, setCurrentTrackId, setVolume, setMode, setFolder, setTracks, setSkipped, setCoverShape]);

  // Spacebar = play/pause toggle. Skip when focus is on a button or input
  // so we don't fight the element's own keyboard handling.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.code !== "Space") return;
      const t = e.target as HTMLElement | null;
      if (t && (t.tagName === "BUTTON" || t.tagName === "INPUT" || t.tagName === "TEXTAREA")) return;
      e.preventDefault();
      const s = useStore.getState();
      const track = s.currentTrack();
      if (!track) return;
      if (!s.isPlaying && s.position === 0) api.play(track.id);
      else api.togglePause();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  return (
    <div className={`app theme-${theme} ${panelMounted ? "expanded" : "compact"}`}>
      <div className="header-row">
        <NowPlaying />
      </div>
      {panelMounted && (
        <div className="panel">
          <Tabs />
          <div className="middle">{view === "list" ? <Playlist /> : <Lyrics />}</div>
        </div>
      )}
    </div>
  );
}
