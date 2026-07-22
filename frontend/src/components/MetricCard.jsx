import "./MetricCard.css";

export default function MetricCard({ title, value, unit, subtitle, status = "ok" }) {
  const isAlert = status === "warning" || status === "critical";

  return (
    <div className={`metric-card ${isAlert ? `metric-card-${status}` : ""}`}>
      <div className="metric-card-title">
        {title}
        {status === "critical" && (
          <span className="metric-card-alert-icon" title="Kritik">
            ⚠
          </span>
        )}
      </div>
      <div className={`metric-card-value ${isAlert ? `metric-card-value-${status}` : ""}`}>
        {value}
        {unit && <span className="metric-card-unit">{unit}</span>}
      </div>
      {subtitle && <div className="metric-card-subtitle">{subtitle}</div>}
    </div>
  );
}
