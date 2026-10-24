import MetricCard from "../MetricCard";
import { isApplicable, isSectionVisible } from "../../config/MetricStatus";

export default function EfficiencySection({ efficiency }) {
  if (!(efficiency &&
      isSectionVisible(efficiency.overallStatus, [
        efficiency.throughputStatus,
        efficiency.networkStatus,
        efficiency.overallStatus,
      ]))) return null;

  return (
    <div className="instance-detail-section">
      <h2 className="instance-detail-section-title">Cache Verimliliği</h2>
      <div className="instance-detail-grid">
        {isApplicable(efficiency.throughputStatus) && (
          <>
            <MetricCard
              title="Ops/sec"
              value={efficiency?.instantaneousOpsPerSec ?? "-"}
              metricStatus={efficiency?.throughputStatus}
              meta={efficiency?.meta}
            />
            <MetricCard
              title="Toplam Komut"
              value={efficiency?.totalCommandsProcessed ?? "-"}
              metricStatus={efficiency?.throughputStatus}
              meta={efficiency?.meta}
            />
          </>
        )}
        {isApplicable(efficiency.networkStatus) && (
          <>
            <MetricCard
              title="Network Giriş"
              value={efficiency ? `${efficiency.inputKbps} KB/s` : "-"}
              metricStatus={efficiency?.networkStatus}
              meta={efficiency?.meta}
            />
            <MetricCard
              title="Network Çıkış"
              value={efficiency ? `${efficiency.outputKbps} KB/s` : "-"}
              metricStatus={efficiency?.networkStatus}
              meta={efficiency?.meta}
            />
          </>
        )}
        {isApplicable(efficiency.overallStatus) && (
          <MetricCard
            title="Hata Yanıtları"
            value={efficiency?.totalErrorReplies ?? "-"}
            status={efficiency && efficiency.totalErrorReplies > 0 ? "critical" : "ok"}
            metricStatus={efficiency?.overallStatus}
            meta={efficiency?.meta}
          />
        )}
      </div>
    </div>
  );
}
