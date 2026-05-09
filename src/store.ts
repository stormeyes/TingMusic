import { create } from "zustand";
import type { CoverShape, Mode, Theme, Track } from "./lib/types";

type View = "list" | "lyrics";

interface State {
  tracks: Track[];
  currentTrackId: string | null;
  position: number;
  isPlaying: boolean;
  volume: number;
  mode: Mode;
  view: View;
  folder: string | null;
  skipped: number;
  expanded: boolean;
  settingsOpen: boolean;
  coverShape: CoverShape;
  theme: Theme;
  setTracks: (tracks: Track[]) => void;
  setCurrentTrackId: (id: string | null) => void;
  setPosition: (ms: number) => void;
  setIsPlaying: (p: boolean) => void;
  setVolume: (v: number) => void;
  setMode: (m: Mode) => void;
  setView: (v: View) => void;
  setFolder: (f: string | null) => void;
  setSkipped: (n: number) => void;
  setExpanded: (e: boolean) => void;
  setSettingsOpen: (o: boolean) => void;
  setCoverShape: (s: CoverShape) => void;
  setTheme: (t: Theme) => void;
  currentTrack: () => Track | null;
  activeLyricIndex: () => number;
  __resetForTest: () => Partial<State>;
}

const initial: Pick<State,
  "tracks" | "currentTrackId" | "position" | "isPlaying" | "volume" | "mode" | "view" | "folder" | "skipped" | "expanded" | "settingsOpen" | "coverShape" | "theme"> = {
  tracks: [], currentTrackId: null, position: 0, isPlaying: false,
  volume: 0.7, mode: "sequential", view: "list", folder: null, skipped: 0,
  expanded: false, settingsOpen: false, coverShape: "circle", theme: "default",
};

export const useStore = create<State>((set, get) => ({
  ...initial,
  setTracks: (tracks) => set({ tracks }),
  setCurrentTrackId: (currentTrackId) => set({ currentTrackId }),
  setPosition: (position) => set({ position }),
  setIsPlaying: (isPlaying) => set({ isPlaying }),
  setVolume: (volume) => set({ volume }),
  setMode: (mode) => set({ mode }),
  setView: (view) => set({ view }),
  setFolder: (folder) => set({ folder }),
  setSkipped: (skipped) => set({ skipped }),
  setExpanded: (expanded) => set({ expanded }),
  setSettingsOpen: (settingsOpen) => set({ settingsOpen }),
  setCoverShape: (coverShape) => set({ coverShape }),
  setTheme: (theme) => set({ theme }),
  currentTrack: () => {
    const id = get().currentTrackId;
    if (!id) return null;
    return get().tracks.find((t) => t.id === id) ?? null;
  },
  activeLyricIndex: () => {
    const t = get().currentTrack();
    if (!t || !t.lyrics || t.lyrics.kind !== "synced") return -1;
    const lines = t.lyrics.value;
    if (lines.length === 0) return -1;
    const pos = get().position;
    let lo = 0, hi = lines.length - 1, ans = 0;
    while (lo <= hi) {
      const mid = (lo + hi) >> 1;
      if (lines[mid].time_ms <= pos) { ans = mid; lo = mid + 1; } else { hi = mid - 1; }
    }
    return ans;
  },
  __resetForTest: () => ({ ...initial }),
}));
