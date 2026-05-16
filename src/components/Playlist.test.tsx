import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, beforeEach, vi } from "vitest";
import { useStore } from "../store";
import Playlist from "./Playlist";

vi.mock("../lib/api", () => ({
  api: { play: vi.fn(async () => undefined) },
}));

// happy-dom does not implement ResizeObserver or layout for getBoundingClientRect
// in a useful way. Stub ResizeObserver and force getBoundingClientRect so the
// Playlist's measurement effect resolves to a non-zero size.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
(globalThis as { ResizeObserver?: unknown }).ResizeObserver = ResizeObserverStub;
Element.prototype.getBoundingClientRect = function () {
  return { width: 400, height: 320, top: 0, left: 0, bottom: 320, right: 400, x: 0, y: 0, toJSON: () => ({}) } as DOMRect;
};

import { api } from "../lib/api";

beforeEach(() => useStore.setState(useStore.getState().__resetForTest()));

describe("Playlist", () => {
  it("renders empty state", () => {
    render(<Playlist />);
    expect(screen.getByText(/未找到音频文件/)).toBeTruthy();
  });

  it("clicks a row and calls api.play", () => {
    useStore.getState().setTracks([
      { id: "1", title: "T1", artist: "A", album: "Al", duration_ms: 1000, cover_data_url: null, lyrics: null },
    ]);
    render(<Playlist />);
    fireEvent.click(screen.getByText("T1"));
    expect(api.play).toHaveBeenCalledWith("1");
  });
});
