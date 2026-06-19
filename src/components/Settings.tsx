import { useEffect, useRef, useState } from "react";
import { open } from "@tauri-apps/plugin-dialog";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { useStore } from "../store";
import { api } from "../lib/api";
import type { CoverShape, Mode, Theme } from "../lib/types";

const MODE_LABEL: Record<Mode, string> = {
  sequential: "顺序",
  random: "随机",
  repeatone: "单曲循环",
};

const SHAPE_LABEL: Record<CoverShape, string> = {
  circle: "圆形",
  square: "方形",
};

const THEME_LABEL: Record<Theme, string> = {
  default: "默认灰",
  whitered: "白红",
};

export default function Settings() {
  const setFolder = useStore((s) => s.setFolder);
  const setTracks = useStore((s) => s.setTracks);
  const setSkipped = useStore((s) => s.setSkipped);
  const skipped = useStore((s) => s.skipped);
  const volume = useStore((s) => s.volume);
  const mode = useStore((s) => s.mode);
  const setMode = useStore((s) => s.setMode);
  const setVolume = useStore((s) => s.setVolume);
  const coverShape = useStore((s) => s.coverShape);
  const setCoverShape = useStore((s) => s.setCoverShape);
  const theme = useStore((s) => s.theme);
  const setTheme = useStore((s) => s.setTheme);
  const folder = useStore((s) => s.folder);
  const [menuOpen, setMenuOpen] = useState(false);
  const setSettingsOpen = useStore((s) => s.setSettingsOpen);
  const wrapRef = useRef<HTMLDivElement>(null);
  const saveTimer = useRef<number | null>(null);
  const [lanSync, setLanSync] = useState(false);
  const [lanAddr, setLanAddr] = useState<string | null>(null);
  const [alwaysOnTop, setAlwaysOnTop] = useState(true);

  // 菜单打开时拉一次同步状态(地址依赖服务是否已起,实时性够用)+ 置顶开关。
  useEffect(() => {
    if (!menuOpen) return;
    api.lanSyncStatus()
      .then((st) => { setLanSync(st.enabled); setLanAddr(st.address); })
      .catch(() => {});
    api.loadConfig()
      .then((c) => setAlwaysOnTop(c.always_on_top))
      .catch(() => {});
  }, [menuOpen]);

  const toggleAlwaysOnTop = async (next: boolean) => {
    if (next === alwaysOnTop) return;
    setAlwaysOnTop(next);
    try {
      await api.setAlwaysOnTop(next);
    } catch (e) {
      console.error(e);
    }
  };

  const toggleLanSync = async (next: boolean) => {
    if (next === lanSync) return;
    setLanSync(next);
    try {
      await api.setLanSync(next);
      const st = await api.lanSyncStatus();
      setLanSync(st.enabled);
      setLanAddr(st.address);
    } catch (e) {
      console.error(e);
    }
  };

  // Mirror local menuOpen into the store so App.tsx can resize the window.
  useEffect(() => { setSettingsOpen(menuOpen); }, [menuOpen, setSettingsOpen]);

  useEffect(() => {
    if (!menuOpen) return;
    const onDocClick = (e: MouseEvent) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    };
    document.addEventListener("mousedown", onDocClick);
    return () => document.removeEventListener("mousedown", onDocClick);
  }, [menuOpen]);

  useEffect(() => () => { if (saveTimer.current) window.clearTimeout(saveTimer.current); }, []);

  const onVolumeChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const v = Number(e.target.value);
    setVolume(v);
    api.setVolume(v);
    if (saveTimer.current) window.clearTimeout(saveTimer.current);
    saveTimer.current = window.setTimeout(() => {
      api.saveConfig({ folder, volume: v, mode, cover_shape: coverShape, theme });
    }, 200);
  };

  const pickFolder = async () => {
    setMenuOpen(false);
    const picked = await open({ directory: true, multiple: false });
    if (typeof picked !== "string") return;
    setFolder(picked);
    const result = await api.scanFolder(picked);
    setTracks(result.tracks);
    setSkipped(result.skipped);
    await api.saveConfig({ folder: picked, volume, mode, cover_shape: coverShape, theme });
  };

  // Selection handlers don't close the menu — the user might want to compare
  // a few options without re-opening it. Menu closes only on outside click,
  // gear click again, or one of the action items (选择曲库 / 退出).
  const chooseMode = (m: Mode) => {
    if (m === mode) return;
    setMode(m);
    api.setMode(m);
    api.saveConfig({ folder, volume, mode: m, cover_shape: coverShape, theme });
  };

  const chooseShape = (s: CoverShape) => {
    if (s === coverShape) return;
    setCoverShape(s);
    api.saveConfig({ folder, volume, mode, cover_shape: s, theme });
  };

  const chooseTheme = (t: Theme) => {
    if (t === theme) return;
    setTheme(t);
    api.saveConfig({ folder, volume, mode, cover_shape: coverShape, theme: t });
  };

  return (
    <div className="settings-wrap" ref={wrapRef}>
      <button
        className="settings-btn"
        aria-label="settings"
        aria-expanded={menuOpen}
        onClick={() => setMenuOpen((v) => !v)}
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <circle cx="12" cy="12" r="3" />
          <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z" />
        </svg>
      </button>
      {menuOpen && (
        <div className="settings-menu" role="menu">
          <button role="menuitem" onClick={pickFolder}>选择曲库…</button>
          {skipped > 0 && (
            <div className="settings-section-label">已忽略 {skipped} 个文件</div>
          )}
          <div className="settings-divider" />
          <div className="settings-section-label">音量</div>
          <div className="settings-volume">
            <svg className="volume-icon" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
              <path d="M3 10v4h4l5 4V6L7 10H3z" fill="currentColor" />
              <path d="M14.5 8.5a4.5 4.5 0 0 1 0 7" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
              <path d="M17 6a8 8 0 0 1 0 12" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
            </svg>
            <input
              type="range" min={0} max={1} step={0.01} value={volume}
              onChange={onVolumeChange}
              aria-label="volume"
            />
          </div>
          <div className="settings-divider" />
          <div className="settings-section-label">播放顺序</div>
          <div className="settings-pill-row">
            {(["sequential", "random", "repeatone"] as Mode[]).map((m) => (
              <button
                key={m}
                role="menuitemradio"
                aria-checked={m === mode}
                className={`settings-pill ${m === mode ? "active" : ""}`}
                onClick={() => chooseMode(m)}
              >
                {MODE_LABEL[m]}
              </button>
            ))}
          </div>
          <div className="settings-divider" />
          <div className="settings-section-label">封面形状</div>
          <div className="settings-pill-row">
            {(["circle", "square"] as CoverShape[]).map((s) => (
              <button
                key={s}
                role="menuitemradio"
                aria-checked={s === coverShape}
                className={`settings-pill ${s === coverShape ? "active" : ""}`}
                onClick={() => chooseShape(s)}
              >
                {SHAPE_LABEL[s]}
              </button>
            ))}
          </div>
          <div className="settings-divider" />
          <div className="settings-section-label">主题</div>
          <div className="settings-pill-row">
            {(["default", "whitered"] as Theme[]).map((t) => (
              <button
                key={t}
                role="menuitemradio"
                aria-checked={t === theme}
                className={`settings-pill ${t === theme ? "active" : ""}`}
                onClick={() => chooseTheme(t)}
              >
                {THEME_LABEL[t]}
              </button>
            ))}
          </div>
          <div className="settings-divider" />
          <div className="settings-section-label">局域网同步</div>
          <div className="settings-pill-row">
            {([["on", true], ["off", false]] as [string, boolean][]).map(([key, val]) => (
              <button
                key={key}
                role="menuitemradio"
                aria-checked={lanSync === val}
                className={`settings-pill ${lanSync === val ? "active" : ""}`}
                onClick={() => toggleLanSync(val)}
              >
                {val ? "开启" : "关闭"}
              </button>
            ))}
          </div>
          {lanSync && lanAddr && (
            <div className="settings-section-label">本机 {lanAddr}</div>
          )}
          <div className="settings-divider" />
          <div className="settings-section-label">窗口置顶</div>
          <div className="settings-pill-row">
            {([["on", true], ["off", false]] as [string, boolean][]).map(([key, val]) => (
              <button
                key={key}
                role="menuitemradio"
                aria-checked={alwaysOnTop === val}
                className={`settings-pill ${alwaysOnTop === val ? "active" : ""}`}
                onClick={() => toggleAlwaysOnTop(val)}
              >
                {val ? "开启" : "关闭"}
              </button>
            ))}
          </div>
          <div className="settings-divider" />
          <button
            role="menuitem"
            onClick={() => { setMenuOpen(false); getCurrentWindow().close().catch(console.error); }}
          >
            <span className="settings-check"></span>
            退出
          </button>
        </div>
      )}
    </div>
  );
}
