import { useEffect, useRef, useState } from "react";
import { FixedSizeList as List } from "react-window";
import { useStore } from "../store";
import { api } from "../lib/api";

export default function Playlist() {
  const tracks = useStore((s) => s.tracks);
  const currentId = useStore((s) => s.currentTrackId);
  const setCurrent = useStore((s) => s.setCurrentTrackId);
  const wrapRef = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({ width: 0, height: 0 });

  useEffect(() => {
    const el = wrapRef.current;
    if (!el) return;
    const measure = () => {
      const r = el.getBoundingClientRect();
      setSize({ width: r.width, height: r.height });
    };
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  if (tracks.length === 0) {
    return <div className="playlist empty">未找到音频文件</div>;
  }

  const Row = ({ index, style }: { index: number; style: React.CSSProperties }) => {
    const t = tracks[index];
    const active = t.id === currentId;
    return (
      <div
        style={style}
        className={`row ${active ? "active" : ""}`}
        onClick={() => { setCurrent(t.id); api.play(t.id); }}
      >
        <div className="row-title" title={t.title}>{t.title}</div>
        <div className="row-artist" title={t.artist}>{t.artist}</div>
      </div>
    );
  };

  return (
    <div ref={wrapRef} className="playlist-wrap">
      {size.height > 0 && (
        <List
          className="playlist"
          height={size.height}
          itemCount={tracks.length}
          itemSize={44}
          width={size.width}
        >
          {Row}
        </List>
      )}
    </div>
  );
}
