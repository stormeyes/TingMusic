import { FixedSizeList as List } from "react-window";
import AutoSizer from "react-virtualized-auto-sizer";
import { useStore } from "../store";
import { api } from "../lib/api";

export default function Playlist() {
  const tracks = useStore((s) => s.tracks);
  const currentId = useStore((s) => s.currentTrackId);
  const setCurrent = useStore((s) => s.setCurrentTrackId);

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
    <AutoSizer>
      {({ width, height }) => (
        <List
          className="playlist"
          height={height}
          itemCount={tracks.length}
          itemSize={44}
          width={width}
        >
          {Row}
        </List>
      )}
    </AutoSizer>
  );
}
