import { useEffect, useState } from "react";
import "./RefreshIndicator.css";

export default function RefreshIndicator({ lastUpdated, onRefresh }) {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const intervalId = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(intervalId);
  }, []);

  const secondsAgo = lastUpdated
    ? Math.max(0, Math.floor((now - lastUpdated.getTime()) / 1000))
    : null;

  return (
    <div className="refresh-indicator">
      <span className="refresh-indicator-text">
        {secondsAgo === null ? "Güncelleniyor..." : `Son güncelleme: ${secondsAgo} saniye önce`}
      </span>
      <button type="button" className="refresh-indicator-button" onClick={onRefresh}>
        ⟳ Yenile
      </button>
    </div>
  );
}
