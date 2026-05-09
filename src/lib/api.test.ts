import { describe, it, expect, vi } from "vitest";

vi.mock("@tauri-apps/api/core", () => ({
  invoke: vi.fn(async (name: string, args?: any) => {
    if (name === "load_config") return { folder: null, volume: 0.7, mode: "sequential" };
    if (name === "scan_folder") return { tracks: [], skipped: 0 };
    return undefined;
  }),
}));

import { api } from "./api";

describe("api", () => {
  it("load_config returns Config", async () => {
    const cfg = await api.loadConfig();
    expect(cfg.volume).toBe(0.7);
    expect(cfg.mode).toBe("sequential");
  });

  it("scan_folder returns ScanResult", async () => {
    const r = await api.scanFolder("/music");
    expect(r.tracks).toEqual([]);
    expect(r.skipped).toBe(0);
  });
});
