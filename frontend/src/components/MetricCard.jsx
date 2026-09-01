import "./MetricCard.css";
import { interpretStatus } from "../config/MetricStatus";
import { formatAgeSeconds } from "../utils/format";

export default function MetricCard({ title, value, unit, subtitle, status = "ok", metricStatus, meta }) {
  const ageSeconds = formatAgeSeconds(meta?.ageMillis);
  const si = interpretStatus(metricStatus, ageSeconds);

  // UNAVAILABLE / NOT_APPLICABLE / PERMISSION_DENIED / TIMEOUT / NOT_READY → değer yerine placeholder, eşik rengini bastır
  const displayValue = si.placeholder != null ? si.placeholder : value;
  const showUnit = si.placeholder == null && unit;
  const effectiveStatus = si.usable ? status : "ok"; // güvenilmezse renklendirme yapma
  const isAlert = effectiveStatus === "warning" || effectiveStatus === "critical";
  const isStale = si.warn || meta?.stale === true;

  // Alt yazı: backend'den gelen not (veri yok / yaklaşık / eski / N/A) varsa öncelik onda
  const effectiveSubtitle = si.note || subtitle;

  return (
    <div
      className={`metric-card ${isAlert ? `metric-card-${effectiveStatus}` : ""} ${isStale ? "metric-card-stale" : ""}`}
      style={si.dim ? { opacity: 0.6 } : undefined}
    >
      {meta && (
        <div className="metric-card-meta">
          {ageSeconds != null && <span>{ageSeconds} sn önce güncellendi</span>}
          {meta.stale && <span className="metric-card-meta-badge">Bayat veri</span>}
        </div>
      )}
      <div className="metric-card-title">
        {title}
        {effectiveStatus === "critical" && (
          <span className="metric-card-alert-icon" title="Kritik">
            ⚠
          </span>
        )}
        {si.warn && (
          <span className="metric-card-warn-icon" title={si.note}>
            ⚠
          </span>
        )}
      </div>
      <div className={`metric-card-value ${isAlert ? `metric-card-value-${effectiveStatus}` : ""}`}>
        {displayValue}
        {si.badge && <span className="metric-card-unit" title="yaklaşık değer">{si.badge}</span>}
        {showUnit && <span className="metric-card-unit">{unit}</span>}
      </div>
      {effectiveSubtitle && <div className="metric-card-subtitle" title={si.note ? si.note : undefined}>{effectiveSubtitle}</div>}
    </div>
  );
}
