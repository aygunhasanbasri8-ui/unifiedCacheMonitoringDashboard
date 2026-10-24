import MetricCard from "../MetricCard";
import DonutMetric from "../DonutMetric";
import CapacityForecastCard from "../CapacityForecastCard";
import { evaluateStatus, healthStatus } from "../../config/thresholds";
import { isApplicable } from "../../config/MetricStatus";
import { formatBytes, formatUptime, formatPercent, formatDurationShort } from "../../utils/format";

// Sayfanın üstündeki halkalar, özet kartlar ve veritabanı başına key dağılımı.
export default function SummaryMetrics({ instanceId, isHazelcast, health, resources, connections, dataStats, efficiency }) {
  // Backend ConnectionStats.getClientUsagePercent() zaten yüzde döner (activeClients/maxClients*100).
  // Burada tekrar 100 ile çarpmak değeri 100 kat şişiriyordu ve eşikleri erken tetikliyordu.
  const clientUsagePercent = connections?.clientUsagePercent ?? null;

  return (
    <>
      <div className="instance-detail-donuts">
        {isApplicable(resources?.cpuStats) && (
          <DonutMetric
            title="CPU Kullanımı"
            percent={resources?.cpuUsagePercent}
            status={resources ? evaluateStatus("cpuUsagePercent", resources.cpuUsagePercent) : "ok"}
            metricStatus={resources?.cpuStats}
            meta={resources?.meta}
          />
        )}
        {isApplicable(resources?.memoryStatus) && (
          <DonutMetric
            title="Bellek Kullanım Yüzdesi"
            percent={resources?.memoryUsagePercent}
            status={resources ? evaluateStatus("memoryUsagePercent", resources.memoryUsagePercent) : "ok"}
            metricStatus={resources?.memoryStatus}
            meta={resources?.meta}
          />
        )}

        {isApplicable(efficiency?.hitRatioStatus) && (
          <DonutMetric
            title="Hit Ratio"
            percent={efficiency?.hitRatio != null ? efficiency.hitRatio * 100 : null}
            status={efficiency ? evaluateStatus("hitRatio", efficiency.hitRatio) : "ok"}
            subtitle={efficiency?.missRatio != null ? `Miss: ${formatPercent(efficiency.missRatio * 100)}` : ""}
            metricStatus={efficiency?.hitRatioStatus}
            meta={efficiency?.meta}
          />
        )}

        {isApplicable(connections?.maxClientsStatus) && (
          <DonutMetric
            title="Client Kullanımı"
            percent={clientUsagePercent}
            status={connections ? evaluateStatus("clientUsagePercent", clientUsagePercent) : "ok"}
            subtitle={connections ? `${connections.activeClients} / ${connections.maxClients}` : ""}
            metricStatus={connections?.maxClientsStatus}
            meta={connections?.meta}
          />
        )}
      </div>

      <div className="instance-detail-grid">
        <MetricCard
          title="Sağlık"
          value={health?.status ?? "-"}
          subtitle={health ? `v${health.version} · ${formatUptime(health.uptimeSeconds)}` : ""}
          status={health ? healthStatus(health.status) : "ok"}
        />
        {isApplicable(resources?.memoryStatus) && (
          <MetricCard
            title="Bellek Kullanımı"
            value={resources ? formatBytes(resources.memoryUsedBytes) : "-"}
            subtitle={
              resources
                ? `/ ${formatBytes(resources.memoryMaxBytes)} · ${formatPercent(resources.memoryUsagePercent)}`
                : ""
            }
            status={resources ? evaluateStatus("memoryUsagePercent", resources.memoryUsagePercent) : "ok"}
            metricStatus={resources?.memoryStatus}
            meta={resources?.meta}
          />
        )}
        {isApplicable(resources?.memoryStatus) && <CapacityForecastCard instanceId={instanceId} />}
        {isApplicable(connections?.activeClientStatus) && (
          <MetricCard
            title="Aktif Bağlantılar"
            value={connections?.activeClients ?? "-"}
            subtitle={connections ? `Maks: ${connections.maxClients}` : ""}
            metricStatus={connections?.activeClientStatus}
            meta={connections?.meta}
          />
        )}
        {isApplicable(connections?.blockedClientsStatus) && (
          <MetricCard
            title="Bloklu Client"
            value={connections?.blockedClients ?? "-"}
            status={connections && connections.blockedClients > 0 ? "warning" : "ok"}
            metricStatus={connections?.blockedClientsStatus}
            meta={connections?.meta}
          />
        )}
        {isApplicable(connections?.overallStatus) && (
          <MetricCard
            title="Reddedilen Bağlantılar"
            value={connections?.rejectedConnections ?? "-"}
            subtitle={
              connections
                ? `${isHazelcast ? "Aktif Bağlantı" : "Toplam alınan"}: ${connections.totalConnectionsReceived}`
                : ""
            }
            status={connections && connections.rejectedConnections > 0 ? "warning" : "ok"}
            metricStatus={connections?.overallStatus}
            meta={connections?.meta}
          />
        )}
        {isApplicable(dataStats?.overallStatus) && (
          <MetricCard
            title="Toplam Key Sayısı"
            value={dataStats?.totalKeyCount ?? "-"}
            metricStatus={dataStats?.overallStatus}
            meta={dataStats?.meta}
          />
        )}
        {isApplicable(resources?.fragmentationStatus) && (
          <MetricCard
            title="Fragmentasyon Oranı"
            value={resources?.fragmentationRatio != null ? resources.fragmentationRatio.toFixed(2) : "-"}
            status={resources ? evaluateStatus("fragmentationRatio", resources.fragmentationRatio) : "ok"}
            metricStatus={resources?.fragmentationStatus}
            meta={resources?.meta}
          />
        )}
        {isApplicable(dataStats?.fragmentationStatus) && (
          <MetricCard
            title="Fragmentasyon (bytes)"
            value={dataStats ? formatBytes(dataStats.fragmentationBytes) : "-"}
            metricStatus={dataStats?.fragmentationStatus}
            meta={dataStats?.meta}
          />
        )}
        {isApplicable(dataStats?.evictionStatus) && (
          <MetricCard
            title="Evict Edilen Key"
            value={dataStats?.evictedKeys ?? "-"}
            subtitle={dataStats?.evictedKeys > 0 ? "Bellek baskısı işareti" : ""}
            status={dataStats && dataStats.evictedKeys > 0 ? "critical" : "ok"}
            metricStatus={dataStats?.evictionStatus}
            meta={dataStats?.meta}
          />
        )}
        {isApplicable(dataStats?.ttlStatus) && (
          <MetricCard
            title="Süresi Dolan Key"
            value={dataStats?.expiredKeys ?? "-"}
            subtitle={dataStats != null ? `TTL'li key: ${dataStats.keysWithExpiry}` : ""}
            metricStatus={dataStats?.ttlStatus}
            meta={dataStats?.meta}
          />
        )}
        {isApplicable(dataStats?.ttlStatus) && (
          <MetricCard
            title="Ortalama TTL"
            value={
              !dataStats
                ? "-"
                : dataStats.avgTtlMillis === null
                  ? <span style={{ color: "var(--text-dim)" }}>TTL tanımlı key yok</span>
                  : formatDurationShort(dataStats.avgTtlMillis)
            }
            metricStatus={dataStats?.ttlStatus}
            meta={dataStats?.meta}
          />
        )}
      </div>

      {dataStats?.keysPerDatabase && Object.keys(dataStats.keysPerDatabase).length > 0 && (
        <div className="instance-detail-section">
          <h2 className="instance-detail-section-title">Veritabanı Dağılımı</h2>
          <table className="instance-detail-mini-table">
            <thead>
              <tr>
                <th>Veritabanı</th>
                <th>Key Sayısı</th>
              </tr>
            </thead>
            <tbody>
              {Object.entries(dataStats.keysPerDatabase).map(([db, count]) => (
                <tr key={db}>
                  <td>{db}</td>
                  <td>{count}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
