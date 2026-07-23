import { useEffect, useState, useCallback, useRef } from "react";
import client from "../api/client";
import InstanceCard from "../components/InstanceCard";
import RefreshIndicator from "../components/RefreshIndicator";
import { evaluateStatus, healthStatus } from "../config/thresholds";
import { formatBytes, formatPercent } from "../utils/format";
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

    if (instance.type === "REDIS" && m.efficiency?.hitRatio != null) {
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

    if (instance.type === "REDIS" && m.resources?.fragmentationRatio != null) {
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

const TOP_PATTERN_COUNT = 5;

async function aggregatePatterns(instances) {
  const results = await Promise.allSettled(
    instances.map((inst) => client.get(`/v1/instances/${inst.id}/key-patterns`))
  );

  const byKey = new Map();
  let hazelcastExcluded = false;

  results.forEach((result, idx) => {
    if (result.status !== "fulfilled") return;
    const inst = instances[idx];

    result.value.data.forEach((p) => {
      const key = `${p.label}|||${p.pattern}`;
      const existing = byKey.get(key) ?? { label: p.label, pattern: p.pattern, keyCount: 0, totalSizeBytes: 0 };
      existing.keyCount += p.keyCount;
      if (inst.type === "HAZELCAST") {
        hazelcastExcluded = true;
      } else {
        existing.totalSizeBytes += p.totalSizeBytes;
      }
      byKey.set(key, existing);
    });
  });

  return { rows: Array.from(byKey.values()), hazelcastExcluded };
}

export default function Overview() {
  const [instances, setInstances] = useState([]);
  const [metricsById, setMetricsById] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [lastUpdated, setLastUpdated] = useState(null);

  const [patternRows, setPatternRows] = useState([]);
  const [patternHazelcastExcluded, setPatternHazelcastExcluded] = useState(false);
  const [patternLoading, setPatternLoading] = useState(true);
  const [patternError, setPatternError] = useState("");
  const patternFetchedOnce = useRef(false);

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

  const fetchPatternSummary = useCallback(async (instancesList) => {
    setPatternLoading(true);
    setPatternError("");
    try {
      const { rows, hazelcastExcluded } = await aggregatePatterns(instancesList);
      setPatternRows(rows);
      setPatternHazelcastExcluded(hazelcastExcluded);
    } catch {
      setPatternError("Desen özeti alınamadı.");
    } finally {
      setPatternLoading(false);
    }
  }, []);

  useEffect(() => {
    // Ağır bir işlem olduğu için 15s auto-refresh döngüsüne dahil edilmez; sadece ilk yüklemede çalışır.
    if (instances.length > 0 && !patternFetchedOnce.current) {
      patternFetchedOnce.current = true;
      fetchPatternSummary(instances);
    }
  }, [instances, fetchPatternSummary]);

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
    .filter((i) => i.type === "REDIS")
    .map((i) => metricsById[i.id]?.efficiency?.hitRatio)
    .filter((v) => v != null);
  const avgHitRatio =
    hitRatios.length > 0 ? hitRatios.reduce((a, b) => a + b, 0) / hitRatios.length : null;

  const alerts = buildAlerts(instances, metricsById);
  const criticalCount = alerts.filter((a) => a.status === "critical").length;

  const patternTotalSize = patternRows.reduce((sum, p) => sum + p.totalSizeBytes, 0);
  const patternTotalKeyCount = patternRows.reduce((sum, p) => sum + p.keyCount, 0);
  const rankBySize = patternTotalSize > 0;
  const topPatternRows = [...patternRows]
    .sort((a, b) => (rankBySize ? b.totalSizeBytes - a.totalSizeBytes : b.keyCount - a.keyCount))
    .slice(0, TOP_PATTERN_COUNT);

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
                {avgHitRatio != null ? formatPercent(avgHitRatio * 100) : "—"}
              </div>
              <div className="overview-summary-label">Ortalama Hit Ratio</div>
              <div className="overview-summary-caption">(yalnızca Redis)</div>
            </div>
            <div className={`overview-summary-box ${criticalCount > 0 ? "overview-summary-down" : ""}`}>
              <div className="overview-summary-value">{criticalCount}</div>
              <div className="overview-summary-label">Aktif Kritik Uyarı</div>
            </div>
          </div>

          <div className="overview-patterns">
            <div className="overview-patterns-header">
              <h2 className="overview-section-title">Key Desen Özeti (Fleet)</h2>
              <button
                type="button"
                className="overview-patterns-refresh"
                onClick={() => fetchPatternSummary(instances)}
                disabled={patternLoading}
              >
                {patternLoading ? "Yükleniyor..." : "⟳ Yenile"}
              </button>
            </div>

            {patternLoading && (
              <div className="overview-status">Desen özeti hesaplanıyor...</div>
            )}
            {!patternLoading && patternError && (
              <div className="overview-error">{patternError}</div>
            )}
            {!patternLoading && !patternError && patternRows.length === 0 && (
              <div className="overview-patterns-empty">Henüz key bulunmuyor.</div>
            )}

            {!patternLoading && !patternError && patternRows.length > 0 && (
              <>
                <ul className="overview-pattern-list">
                  {topPatternRows.map((p) => {
                    const sizePercent = rankBySize ? (p.totalSizeBytes / patternTotalSize) * 100 : 0;
                    const countPercent =
                      patternTotalKeyCount > 0 ? (p.keyCount / patternTotalKeyCount) * 100 : 0;
                    const percent = rankBySize ? sizePercent : countPercent;

                    return (
                      <li key={`${p.label}|||${p.pattern}`} className="overview-pattern-row">
                        <span className="overview-pattern-label" title={p.pattern}>
                          {p.label}
                        </span>
                        <div className="overview-pattern-bar-track">
                          <div
                            className="overview-pattern-bar-fill"
                            style={{ width: `${percent.toFixed(2)}%` }}
                          />
                        </div>
                        <span className="overview-pattern-stat">
                          {percent.toFixed(1)}% · {p.keyCount} key
                          {rankBySize ? ` · ${formatBytes(p.totalSizeBytes)}` : ""}
                        </span>
                      </li>
                    );
                  })}
                </ul>
                {patternHazelcastExcluded && (
                  <div className="overview-patterns-note">
                    * Boyut yüzdeleri yalnızca Redis instance'larını kapsar; Hazelcast entry sayıları
                    key sayısına dahildir ancak boyuta dahil değildir.
                  </div>
                )}
              </>
            )}
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
        </>
      )}
    </div>
  );
}
