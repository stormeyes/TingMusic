import { useStore } from "../store";

export default function Tabs() {
  const view = useStore((s) => s.view);
  const setView = useStore((s) => s.setView);
  return (
    <div className="tabs">
      <button className={view === "list" ? "active" : ""} onClick={() => setView("list")}>列表</button>
      <button className={view === "lyrics" ? "active" : ""} onClick={() => setView("lyrics")}>歌词</button>
    </div>
  );
}
