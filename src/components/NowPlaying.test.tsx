import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, beforeEach, vi } from "vitest";
import { useStore } from "../store";
import NowPlaying from "./NowPlaying";

vi.mock("../lib/api", () => ({
  api: {
    fetchCover: vi.fn(async () => null),
    togglePause: vi.fn(async () => undefined),
    play: vi.fn(async () => undefined),
    seek: vi.fn(async () => undefined),
  },
}));

import { api } from "../lib/api";

beforeEach(() => {
  useStore.setState(useStore.getState().__resetForTest());
  vi.clearAllMocks();
});

describe("NowPlaying", () => {
  it("empty state shows placeholder cover, 无歌曲 label and a 0:00 progress bar", () => {
    const { container } = render(<NowPlaying />);
    expect(container.querySelector(".now-playing.empty")).toBeTruthy();
    expect(container.querySelector(".placeholder")).toBeTruthy();
    expect(screen.getByText("无歌曲")).toBeTruthy();
    expect(screen.getByLabelText("progress")).toBeTruthy();
  });

  it("shows title and artist when current track set", () => {
    useStore.getState().setTracks([{
      id: "1", title: "Song", artist: "Artist", album: "Album",
      duration_ms: 1000, cover_data_url: null, lyrics: null,
    }]);
    useStore.getState().setCurrentTrackId("1");
    render(<NowPlaying />);
    expect(screen.getByText("Song")).toBeTruthy();
    expect(screen.getByText("Artist")).toBeTruthy();
  });

  it("first cover click on a fresh track calls api.play", () => {
    useStore.getState().setTracks([{
      id: "1", title: "Song", artist: "Artist", album: "Album",
      duration_ms: 1000, cover_data_url: null, lyrics: null,
    }]);
    useStore.getState().setCurrentTrackId("1");
    render(<NowPlaying />);
    fireEvent.click(screen.getByLabelText("play-pause"));
    expect(api.play).toHaveBeenCalledWith("1");
    expect(api.togglePause).not.toHaveBeenCalled();
  });

  it("cover click after playback has started calls togglePause", () => {
    useStore.getState().setTracks([{
      id: "1", title: "Song", artist: "Artist", album: "Album",
      duration_ms: 1000, cover_data_url: null, lyrics: null,
    }]);
    useStore.getState().setCurrentTrackId("1");
    useStore.getState().setPosition(500);
    render(<NowPlaying />);
    fireEvent.click(screen.getByLabelText("play-pause"));
    expect(api.togglePause).toHaveBeenCalled();
    expect(api.play).not.toHaveBeenCalled();
  });
});
