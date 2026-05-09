import { useEffect, useRef } from "react";
import { useStore } from "../store";

export default function Lyrics() {
  const track = useStore((s) => s.currentTrack());
  const activeIndex = useStore((s) => s.activeLyricIndex());
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (activeIndex < 0) return;
    const node = containerRef.current?.querySelector<HTMLDivElement>(
      `[data-line="${activeIndex}"]`,
    );
    node?.scrollIntoView({ block: "center", behavior: "smooth" });
  }, [activeIndex]);

  if (!track || !track.lyrics) {
    return <div className="lyrics empty">暂无歌词</div>;
  }
  if (track.lyrics.kind === "plain") {
    return <div className="lyrics plain">{track.lyrics.value}</div>;
  }
  return (
    <div className="lyrics synced" ref={containerRef}>
      {track.lyrics.value.map((line, i) => (
        <div
          key={i}
          data-line={i}
          className={i === activeIndex ? "line active" : "line"}
        >
          {line.text || " "}
        </div>
      ))}
    </div>
  );
}
