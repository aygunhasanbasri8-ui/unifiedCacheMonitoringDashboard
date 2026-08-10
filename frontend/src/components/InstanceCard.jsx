import { useNavigate } from "react-router-dom";
import { healthStatus } from "../config/thresholds";
import { isApplicable } from "../config/metricStatus";
import { formatPercent } from "../utils/format";
import "./InstanceCard.css";

export default function InstanceCard({
  instance,
  health,
  keyCount,
  hitRatio,
  hitRatioStatus,
  activeClients,
  deadlockedThreadCount,
}) {
  const navigate = useNavigate();

  const isDown = health != null && healthStatus(health) === "critical";
  const dotClass = health == null ? "status-unknown" : isDown ? "status-down" : "status-up";

  return (
    <div className="instance-card" onClick={() => navigate(`/instances/${instance.id}`)}>
      <div className="instance-card-header">
        <span className="instance-card-name">{instance.name}</span>
        <span className={`instance-card-type instance-card-type-${instance.type?.toLowerCase()}`}>
          {instance.type}
        </span>
      </div>
      <div className="instance-card-address">
        {instance.host}:{instance.port}
      </div>
      <div className="instance-card-status">
        <span className={`status-dot ${dotClass}`} />
        {health ?? "Kontrol ediliyor..."}
        {isDown && (
          <span className="instance-card-alert-icon" title="Kritik">
            ⚠
          </span>
        )}
      </div>
      {deadlockedThreadCount > 0 && (
        <div className="instance-card-deadlock-badge">
          ⚠ {deadlockedThreadCount} thread deadlock'ta
        </div>
      )}
      <div className="instance-card-metrics">
        <div className="instance-card-metric">
          <span className="instance-card-metric-label">Key</span>
          <span className="instance-card-metric-value">{keyCount ?? "-"}</span>
        </div>
        {isApplicable(hitRatioStatus) && (
          <div className="instance-card-metric">
            <span className="instance-card-metric-label">Hit Ratio</span>
            <span className="instance-card-metric-value">
              {hitRatio != null ? formatPercent(hitRatio * 100) : "N/A"}
            </span>
          </div>
        )}
        <div className="instance-card-metric">
          <span className="instance-card-metric-label">Bağlantı</span>
          <span className="instance-card-metric-value">{activeClients ?? "-"}</span>
        </div>
      </div>
      <button
        type="button"
        className="instance-card-action"
        onClick={(e) => {
          e.stopPropagation();
          navigate(`/instances/${instance.id}/keys`);
        }}
      >
        {instance.type === "HAZELCAST" ? "IMap Listesi →" : "Key Analizi →"}
      </button>
    </div>
  );
}
