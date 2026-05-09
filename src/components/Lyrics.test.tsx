import { vi } from "vitest";
Object.defineProperty(Element.prototype, "scrollIntoView", { value: vi.fn(), writable: true });

import { render, screen } from "@testing-library/react";
import { describe, it, expect, beforeEach } from "vitest";
import { useStore } from "../store";
import Lyrics from "./Lyrics";

beforeEach(() => useStore.setState(useStore.getState().__resetForTest()));

describe("Lyrics", () => {
  it("shows empty state without lyrics", () => {
    render(<Lyrics />);
    expect(screen.getByText(/暂无歌词/)).toBeTruthy();
  });

  it("renders synced lyric lines with active class on current", () => {
    useStore.getState().setTracks([{
      id: "1", title: "T", artist: "A", album: "Al", duration_ms: 10000,
      cover_data_url: null,
      lyrics: { kind: "synced", value: [
        { time_ms: 0, text: "first" },
        { time_ms: 2000, text: "second" },
      ]},
    }]);
    useStore.getState().setCurrentTrackId("1");
    useStore.getState().setPosition(2500);
    render(<Lyrics />);
    expect(screen.getByText("second").className).toMatch(/active/);
    expect(screen.getByText("first").className).not.toMatch(/active/);
  });

  it("renders plain lyrics as a single block", () => {
    useStore.getState().setTracks([{
      id: "1", title: "T", artist: "A", album: "Al", duration_ms: 1000,
      cover_data_url: null,
      lyrics: { kind: "plain", value: "lalala" },
    }]);
    useStore.getState().setCurrentTrackId("1");
    render(<Lyrics />);
    expect(screen.getByText("lalala")).toBeTruthy();
  });
});
