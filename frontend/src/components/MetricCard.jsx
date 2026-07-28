import "./MetricCard.css";
import { interpretStatus } from "../config/metricStatus";

export default function MetricCard({ title, value, unit, subtitle, status = "ok", metricStatus }) {
  const si = interpretStatus(metricStatus);

  // NOT_APPLICABLE → kartı hiç render etme
  if (si.hidden) return null;

  // UNAVAILABLE / NOT_READY → değer yerine placeholder, eşik rengini bastır
  const displayValue = si.placeholder != null ? si.placeholder : value;
  const showUnit = si.placeholder == null && unit;
  const effectiveStatus = si.usable ? status : "ok"; // güvenilmezse renklendirme yapma
  const isAlert = effectiveStatus === "warning" || effectiveStatus === "critical";

  // Alt yazı: backend'den gelen not (veri yok / yaklaşık / eski) varsa öncelik onda
  const effectiveSubtitle = si.note || subtitle;

  return (
    <div
      className={`metric-card ${isAlert ? `metric-card-${effectiveStatus}` : ""}`}
      style={si.dim ? { opacity: 0.6 } : undefined}
    >
      <div className="metric-card-title">
        {title}
        {effectiveStatus === "critical" && (
          <span className="metric-card-alert-icon" title="Kritik">
            ⚠
          </span>
        )}
      </div>
      <div className={`metric-card-value ${isAlert ? `metric-card-value-${effectiveStatus}` : ""}`}>
        {displayValue}
        {si.badge && <span className="metric-card-unit" title="yaklaşık değer">{si.badge}</span>}
        {showUnit && <span className="metric-card-unit">{unit}</span>}
      </div>
      {effectiveSubtitle && <div className="metric-card-subtitle">{effectiveSubtitle}</div>}
    </div>
  );
}