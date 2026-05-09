import { describe, it, expect, beforeEach } from "vitest";
import { useStore } from "./store";

beforeEach(() => {
  useStore.setState(useStore.getState().__resetForTest());
});

describe("store", () => {
  it("setTracks replaces tracks", () => {
    useStore.getState().setTracks([
      { id: "1", title: "T", artist: "A", album: "Al", duration_ms: 1000, cover_data_url: null, lyrics: null },
    ]);
    expect(useStore.getState().tracks).toHaveLength(1);
  });

  it("setView toggles between list and lyrics", () => {
    useStore.getState().setView("lyrics");
    expect(useStore.getState().view).toBe("lyrics");
    useStore.getState().setView("list");
    expect(useStore.getState().view).toBe("list");
  });

  it("activeLyricIndex finds current line by position", () => {
    useStore.getState().setTracks([{
      id: "1", title: "T", artist: "A", album: "Al", duration_ms: 10000,
      cover_data_url: null,
      lyrics: { kind: "synced", value: [
        { time_ms: 0, text: "a" },
        { time_ms: 2000, text: "b" },
        { time_ms: 5000, text: "c" },
      ]},
    }]);
    useStore.getState().setCurrentTrackId("1");
    useStore.getState().setPosition(2500);
    expect(useStore.getState().activeLyricIndex()).toBe(1);
    useStore.getState().setPosition(5500);
    expect(useStore.getState().activeLyricIndex()).toBe(2);
    useStore.getState().setPosition(0);
    expect(useStore.getState().activeLyricIndex()).toBe(0);
  });
});
