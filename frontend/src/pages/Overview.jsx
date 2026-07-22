import { useEffect, useState, useCallback } from "react";
import client from "../api/client";
import InstanceCard from "../components/InstanceCard";
import RefreshIndicator from "../components/RefreshIndicator";
import { evaluateStatus, healthStatus } from "../config/thresholds";
import { formatPercent } from "../utils/format";
import "./Overview.css";

const REFRESH_INTERVAL_MS = 15000;

const ALERT_LABELS = {
  health: "Sağlık",
  memoryUsagePercent: "Bellek Kullanımı",
  hitRatio: "Hit Ratio",
  fragmentationRatio: "Fragmentasyon Oranı",
  connectionErrors: "Bağlantı Hataları",
};

function buildAlerts(instances, metricsById) {
  const alerts = [];

  instances.forEach((instance) => {
    const m = metricsById[instance.id];
    if (!m) return;

    const hStatus = healthStatus(m.health?.status);
    if (hStatus !== "ok") {
      alerts.push({
        instanceName: instance.name,
        label: ALERT_LABELS.health,
        valueText: m.health?.status ?? "Bilinmiyor",
        status: hStatus,
      });
    }

    if (m.resources?.memoryUsagePercent != null) {
      const s = evaluateStatus("memoryUsagePercent", m.resources.memoryUsagePercent);
      if (s !== "ok") {
        alerts.push({
          instanceName: instance.name,
          label: ALERT_LABELS.memoryUsagePercent,
          valueText: formatPercent(m.resources.memoryUsagePercent),
          status: s,
        });
      }
    }

    if (m.efficiency?.hitRatio != null) {
      const s = evaluateStatus("hitRatio", m.efficiency.hitRatio);
      if (s !== "ok") {
        alerts.push({
          instanceName: instance.name,
          label: ALERT_LABELS.hitRatio,
          valueText: formatPercent(m.efficiency.hitRatio * 100),
          status: s,
        });
      }
    }

    if (m.resources?.fragmentationRatio != null) {
      const s = evaluateStatus("fragmentationRatio", m.resources.fragmentationRatio);
      if (s !== "ok") {
        alerts.push({
          instanceName: instance.name,
          label: ALERT_LABELS.fragmentationRatio,
          valueText: m.resources.fragmentationRatio.toFixed(2),
          status: s,
        });
      }
    }

    if (m.connections?.totalConnectionErrors != null) {
      const s = evaluateStatus("connectionErrors", m.connections.totalConnectionErrors);
      if (s !== "ok") {
        alerts.push({
          instanceName: instance.name,
          label: ALERT_LABELS.connectionErrors,
          valueText: String(m.connections.totalConnectionErrors),
          status: s,
        });
      }
    }
  });

  alerts.sort((a, b) => {
    if (a.status === b.status) return 0;
    return a.status === "critical" ? -1 : 1;
  });

  return alerts;
}

export default function Overview() {
  const [instances, setInstances] = useState([]);
  const [metricsById, setMetricsById] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [lastUpdated, setLastUpdated] = useState(null);

  const fetchAll = useCallback(async () => {
    try {
      const instancesRes = await client.get("/v1/instances");
      const list = instancesRes.data;
      setInstances(list);

      const entries = await Promise.all(
        list.map(async (inst) => {
          const [healthR, resourcesR, connectionsR, dataStatsR, efficiencyR] = await Promise.allSettled([
            client.get(`/v1/instances/${inst.id}/health`),
            client.get(`/v1/instances/${inst.id}/resources`),
            client.get(`/v1/instances/${inst.id}/connections`),
            client.get(`/v1/instances/${inst.id}/data-stats`),
            client.get(`/v1/instances/${inst.id}/efficiency`),
          ]);

          return [
            inst.id,
            {
              health: healthR.status === "fulfilled" ? healthR.value.data : null,
              resources: resourcesR.status === "fulfilled" ? resourcesR.value.data : null,
              connections: connectionsR.status === "fulfilled" ? connectionsR.value.data : null,
              dataStats: dataStatsR.status === "fulfilled" ? dataStatsR.value.data : null,
              efficiency: efficiencyR.status === "fulfilled" ? efficiencyR.value.data : null,
            },
          ];
        })
      );

      setMetricsById(Object.fromEntries(entries));
      setLastUpdated(new Date());
      setError("");
    } catch {
      setError("Instance listesi alınamadı.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- ilk veri çekimi için kasıtlı
    fetchAll();
    const intervalId = setInterval(fetchAll, REFRESH_INTERVAL_MS);
    return () => clearInterval(intervalId);
  }, [fetchAll]);

  const upCount = instances.filter((i) => metricsById[i.id]?.health?.status === "UP").length;
  const downCount = instances.length - upCount;

  const totalKeyCount = instances.reduce((sum, i) => {
    const v = metricsById[i.id]?.dataStats?.totalKeyCount;
    return v != null ? sum + v : sum;
  }, 0);

  const totalActiveConnections = instances.reduce((sum, i) => {
    const v = metricsById[i.id]?.connections?.activeClients;
    return v != null ? sum + v : sum;
  }, 0);

  const hitRatios = instances
    .map((i) => metricsById[i.id]?.efficiency?.hitRatio)
    .filter((v) => v != null);
  const avgHitRatio =
    hitRatios.length > 0 ? hitRatios.reduce((a, b) => a + b, 0) / hitRatios.length : null;

  const alerts = buildAlerts(instances, metricsById);
  const criticalCount = alerts.filter((a) => a.status === "critical").length;

  return (
    <div className="overview-page">
      <div className="overview-header">
        <h1 className="overview-title">Instances</h1>
        <RefreshIndicator lastUpdated={lastUpdated} onRefresh={fetchAll} />
      </div>

      {loading && <div className="overview-status">Yükleniyor...</div>}
      {error && <div className="overview-error">{error}</div>}

      {!loading && !error && (
        <>
          <div className="overview-summary">
            <div className="overview-summary-box">
              <div className="overview-summary-value">{instances.length}</div>
              <div className="overview-summary-label">Toplam Instance</div>
            </div>
            <div className="overview-summary-box overview-summary-up">
              <div className="overview-summary-value">{upCount}</div>
              <div className="overview-summary-label">Sağlıklı (UP)</div>
            </div>
            <div className={`overview-summary-box ${downCount > 0 ? "overview-summary-down" : ""}`}>
              <div className="overview-summary-value">{downCount}</div>
              <div className="overview-summary-label">Sorunlu (DOWN)</div>
            </div>
            <div className="overview-summary-box">
              <div className="overview-summary-value">{totalKeyCount}</div>
              <div className="overview-summary-label">Toplam Key Sayısı</div>
            </div>
            <div className="overview-summary-box">
              <div className="overview-summary-value">{totalActiveConnections}</div>
              <div className="overview-summary-label">Toplam Aktif Bağlantı</div>
            </div>
            <div className="overview-summary-box">
              <div className="overview-summary-value">
                {avgHitRatio != null ? formatPercent(avgHitRatio * 100) : "-"}
              </div>
              <div className="overview-summary-label">Ortalama Hit Ratio</div>
            </div>
            <div className={`overview-summary-box ${criticalCount > 0 ? "overview-summary-down" : ""}`}>
              <div className="overview-summary-value">{criticalCount}</div>
              <div className="overview-summary-label">Aktif Kritik Uyarı</div>
            </div>
          </div>

          <div className="overview-alerts">
            <h2 className="overview-section-title">Aktif Uyarılar</h2>
            {alerts.length === 0 ? (
              <div className="overview-alerts-clear">✓ Tüm sistemler normal</div>
            ) : (
              <ul className="overview-alerts-list">
                {alerts.map((alert) => (
                  <li
                    key={`${alert.instanceName}-${alert.label}`}
                    className={`overview-alert-item overview-alert-${alert.status}`}
                  >
                    {alert.instanceName} — {alert.label}: {alert.valueText} (
                    {alert.status === "critical" ? "Kritik" : "Uyarı"})
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="overview-grid">
            {instances.map((instance) => (
              <InstanceCard
                key={instance.id}
                instance={instance}
                health={metricsById[instance.id]?.health?.status ?? null}
                keyCount={metricsById[instance.id]?.dataStats?.totalKeyCount}
                hitRatio={metricsById[instance.id]?.efficiency?.hitRatio}
                activeClients={metricsById[instance.id]?.connections?.activeClients}
              />
            ))}
          </div>
        </>
      )}
    </div>
  );
}
