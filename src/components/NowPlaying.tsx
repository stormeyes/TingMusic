import { useEffect, useState } from "react";
import { useStore } from "../store";
import { api } from "../lib/api";
import Progress from "./Progress";
import Settings from "./Settings";

function CoverPlaceholder() {
  return (
    <div className="placeholder">
      <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
        <defs>
          <radialGradient id="vinyl-shine" cx="32%" cy="32%" r="55%">
            <stop offset="0%" stopColor="rgba(255,255,255,0.22)" />
            <stop offset="55%" stopColor="rgba(255,255,255,0)" />
          </radialGradient>
        </defs>
        {/* Disc body */}
        <circle cx="12" cy="12" r="11" fill="#0a0a0a" />
        {/* Specular highlight */}
        <circle cx="12" cy="12" r="11" fill="url(#vinyl-shine)" />
        {/* Concentric grooves */}
        <circle cx="12" cy="12" r="9.5" fill="none" stroke="rgba(255,255,255,0.06)" strokeWidth="0.25" />
        <circle cx="12" cy="12" r="7.5" fill="none" stroke="rgba(255,255,255,0.06)" strokeWidth="0.25" />
        <circle cx="12" cy="12" r="5.5" fill="none" stroke="rgba(255,255,255,0.06)" strokeWidth="0.25" />
        {/* Center label */}
        <circle cx="12" cy="12" r="3.5" fill="#d33a31" />
        {/* Spindle hole */}
        <circle cx="12" cy="12" r="0.6" fill="#0a0a0a" />
      </svg>
    </div>
  );
}

function PlayGlyph() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <path d="M8 5v14l11-7z" />
    </svg>
  );
}

function PauseGlyph() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <rect x="6" y="5" width="4" height="14" rx="1" />
      <rect x="14" y="5" width="4" height="14" rx="1" />
    </svg>
  );
}

function ExpandGlyph({ open }: { open: boolean }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      {open
        ? <polyline points="18 15 12 9 6 15" />
        : <polyline points="6 9 12 15 18 9" />}
    </svg>
  );
}

export default function NowPlaying() {
  const track = useStore((s) => s.currentTrack());
  const isPlaying = useStore((s) => s.isPlaying);
  const position = useStore((s) => s.position);
  const expanded = useStore((s) => s.expanded);
  const setExpanded = useStore((s) => s.setExpanded);
  const coverShape = useStore((s) => s.coverShape);
  const isCircle = coverShape === "circle";
  const [fetchedCover, setFetchedCover] = useState<string | null>(null);

  // First-click semantics: when nothing has actually started yet (no sink
  // in the Rust player) togglePause is a no-op, so we explicitly play.
  // Subsequent clicks toggle as usual.
  const handleCoverClick = () => {
    if (!track) return;
    if (!isPlaying && position === 0) api.play(track.id);
    else api.togglePause();
  };

  useEffect(() => {
    setFetchedCover(null);
    if (!track || track.cover_data_url) return;
    let cancelled = false;
    api
      .fetchCover(track.id, track.title, track.artist)
      .then((url) => { if (!cancelled && url) setFetchedCover(url); })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [track?.id, track?.cover_data_url]);

  const ExpandButton = (
    <button
      type="button"
      className="expand-btn"
      aria-label={expanded ? "collapse" : "expand"}
      aria-expanded={expanded}
      onClick={() => setExpanded(!expanded)}
    >
      <ExpandGlyph open={expanded} />
    </button>
  );

  if (!track) {
    return (
      <div className="now-playing empty" data-tauri-drag-region>
        <div className={`cover ${isCircle ? "cover-circle" : ""}`}><CoverPlaceholder /></div>
        <div className="meta" data-tauri-drag-region>
          <div className="title-line">
            <span className="title">无歌曲</span>
          </div>
          <Progress />
        </div>
        <Settings />
        {ExpandButton}
      </div>
    );
  }

  const displayedCover = track.cover_data_url ?? fetchedCover;
  const coverState = isPlaying ? "playing" : "paused";

  return (
    <div className="now-playing" data-tauri-drag-region>
      <button
        type="button"
        className={`cover cover-button ${coverState}${isCircle ? " cover-circle" : ""}`}
        onClick={handleCoverClick}
        aria-label="play-pause"
      >
        {displayedCover
          ? <img src={displayedCover} alt={track.album} />
          : <CoverPlaceholder />}
        <span className="cover-overlay">
          {isPlaying ? <PauseGlyph /> : <PlayGlyph />}
        </span>
      </button>
      <div className="meta" data-tauri-drag-region>
        <div className="title-line" title={`${track.title} - ${track.artist}`}>
          <span className="title">{track.title}</span>
          <span className="dash">-</span>
          <span className="artist">{track.artist}</span>
        </div>
        <Progress />
      </div>
      <Settings />
      {ExpandButton}
    </div>
  );
}
