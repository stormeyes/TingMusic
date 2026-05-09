import { useState } from "react";
import { useStore } from "../store";
import { api } from "../lib/api";

function fmt(ms: number) {
  const s = Math.floor(ms / 1000);
  const m = Math.floor(s / 60);
  const ss = s % 60;
  return `${m}:${ss.toString().padStart(2, "0")}`;
}

export default function Progress() {
  const track = useStore((s) => s.currentTrack());
  const position = useStore((s) => s.position);
  const setPosition = useStore((s) => s.setPosition);
  const [seekingValue, setSeekingValue] = useState<number | null>(null);

  const duration = track?.duration_ms ?? 0;
  const sliderValue = seekingValue ?? Math.min(position, duration);

  const onChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setSeekingValue(Number(e.target.value));
  };
  const commit = () => {
    if (seekingValue !== null) {
      const target = seekingValue;
      api.seek(target);
      setPosition(target);
      window.setTimeout(() => {
        setSeekingValue((cur) => (cur === target ? null : cur));
      }, 350);
    }
  };

  return (
    <div className="progress-row">
      <input
        type="range" min={0} max={Math.max(duration, 1)} value={sliderValue}
        onChange={onChange}
        onMouseUp={commit}
        onTouchEnd={commit}
        onKeyUp={commit}
        aria-label="progress"
      />
      <span className="time">{fmt(sliderValue)} / {fmt(duration)}</span>
    </div>
  );
}
