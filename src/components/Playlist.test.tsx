import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, beforeEach, vi } from "vitest";
import { useStore } from "../store";
import Playlist from "./Playlist";

vi.mock("../lib/api", () => ({
  api: { play: vi.fn(async () => undefined) },
}));

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
