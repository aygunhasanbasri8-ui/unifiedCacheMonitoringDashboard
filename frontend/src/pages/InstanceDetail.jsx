import { useEffect, useState, useCallback, useMemo, Fragment } from "react";
import { useParams, Link } from "react-router-dom";
import {
  ResponsiveContainer,
  LineChart,
  Line,
  CartesianGrid,
  XAxis,
  YAxis,
  Tooltip,
} from "recharts";
import client from "../api/client";
import { toProblem } from "../api/problemDetails";
import MetricCard from "../components/MetricCard";
import DonutMetric from "../components/DonutMetric";
import RefreshIndicator from "../components/RefreshIndicator";
import ProblemError from "../components/ProblemError";
import { useTransientErrors, MAX_CONSECUTIVE_TRANSIENT_ERRORS } from "../hooks/useTransientErrors";
import { evaluateStatus, healthStatus } from "../config/thresholds";
import { isApplicable, isSectionVisible } from "../config/MetricStatus";
import { extractAlertEvents } from "../utils/alertEvents";
import {
  formatBytes,
  formatUptime,
  formatPercent,
  formatTime,
  formatDurationShort,
  formatEpochSeconds,
  formatEpochMillis,
  formatCompactNumber,
  formatShortDateTime,
} from "../utils/format";
import "./InstanceDetail.css";

const REFRESH_INTERVAL_MS = 15000;
const HISTORY_MAX_POINTS = 500;
const HISTORY_RANGES = [
  { label: "1 saat", minutes: 60 },
  { label: "6 saat", minutes: 360 },
  { label: "24 saat", minutes: 1440 },
  { label: "7 gün", minutes: 10080 },
];
// Uyarı geçmişi snapshot retention sınırına (7 gün) kadar, ayrı ve düşük öncelikli
// bir çağrıyla hesaplanır — grafiklerin seçilebilir aralığından (historyRangeMinutes)
// bağımsızdır ve 15 sn'lik polling'e dahil edilmez.
const ALERT_HISTORY_LAST_MINUTES = 10080;
const ALERT_HISTORY_MAX_POINTS = 2000;

// Uyarı başlangıcından bu yana geçen süreyi "2 sa 14 dk" biçiminde döner.
function elapsedSince(isoString) {
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(isoString).getTime()) / 1000));
  return formatUptime(seconds);
}

// Çözülmüş bir uyarı olayı için "DD.MM HH:mm → HH:mm" (aynı gün) ya da
// "DD.MM HH:mm → DD.MM HH:mm" (farklı gün) aralığını döner.
function alertEventRange(startedAt, endedAt) {
  if (!endedAt) return formatShortDateTime(startedAt);
  const sameDay = new Date(startedAt).toDateString() === new Date(endedAt).toDateString();
  const endText = sameDay ? formatTime(endedAt).slice(0, 5) : formatShortDateTime(endedAt);
  return `${formatShortDateTime(startedAt)} → ${endText}`;
}

function TimeSeriesChart({ title, data, dataKey, color, valueFormatter, seriesName }) {
  const tickInterval = Math.max(0, Math.ceil(data.length / 6) - 1);

  return (
    <div className="instance-detail-chart-card">
      <div className="instance-detail-chart-title">{title}</div>
      <ResponsiveContainer width="100%" height={220}>
        <LineChart data={data} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
          <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
          <XAxis
            dataKey="timestamp"
            tickFormatter={formatTime}
            interval={tickInterval}
            stroke="var(--text-dim)"
            fontSize={11}
          />
          <YAxis stroke="var(--text-dim)" fontSize={11} width={48} />
          <Tooltip
            contentStyle={{
              background: "var(--bg-card)",
              border: "1px solid var(--border)",
              borderRadius: 8,
              color: "var(--text)",
            }}
            labelFormatter={formatTime}
            // Seri değeri null olabilir (ör. hitRatio hiç sorgu yapılmamışsa) —
            // formatter'a null geçmek toFixed çağrısında crash'e yol açar.
            formatter={(value) => [
              value == null ? "-" : valueFormatter ? valueFormatter(value) : value,
              seriesName,
            ]}
          />
          <Line
            type="monotone"
            dataKey={dataKey}
            name={seriesName}
            stroke={color}
            strokeWidth={2}
            dot={data.length <= 30}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

export default function InstanceDetail() {
  const { id } = useParams();

  const [instance, setInstance] = useState(null);
  const [health, setHealth] = useState(null);
  const [resources, setResources] = useState(null);
  const [connections, setConnections] = useState(null);
  const [dataStats, setDataStats] = useState(null);
  const [efficiency, setEfficiency] = useState(null);
  const [cluster, setCluster] = useState(null);
  const [replication, setReplication] = useState(null);
  const [persistence, setPersistence] = useState(null);
  const [history, setHistory] = useState({ points: [], resolution: null, pointCount: 0 });
  const [historyRangeMinutes, setHistoryRangeMinutes] = useState(60);
  const [mapStats, setMapStats] = useState(null);
  const [expandedMaps, setExpandedMaps] = useState(new Set());
  const [operationStats, setOperationStats] = useState(null);
  const [operationSectionOpen, setOperationSectionOpen] = useState(false);

  const [alertPoints, setAlertPoints] = useState([]);
  const [alertHistoryLoading, setAlertHistoryLoading] = useState(true);
  const [alertHistoryError, setAlertHistoryError] = useState(null);
  const [alertHistoryOpen, setAlertHistoryOpen] = useState(false);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [lastUpdated, setLastUpdated] = useState(null);

  const {
    transientWarning,
    consecutiveTransientErrors,
    pollingStopped,
    recordTransientError,
    clearTransientErrors,
    reconnect: handleReconnect,
  } = useTransientErrors();

  useEffect(() => {
    let cancelled = false;

    client
      .post(`/v1/instances/${id}/detail`)
      .then((res) => {
        if (!cancelled) setInstance(res.data);
      })
      .catch((err) => {
        if (!cancelled) {
          setError(toProblem(err, "Instance bilgisi alınamadı."));
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [id]);

  const fetchMetrics = useCallback(async () => {
    const results = await Promise.allSettled([
      client.post(`/v1/instances/${id}/health`, {}),
      client.post(`/v1/instances/${id}/resources`, {}),
      client.post(`/v1/instances/${id}/connections`, {}),
      client.post(`/v1/instances/${id}/data-stats`, {}),
      client.post(`/v1/instances/${id}/efficiency`, {}),
      client.post(`/v1/instances/${id}/cluster`, {}),
      client.post(`/v1/instances/${id}/replication`, {}),
      client.post(`/v1/instances/${id}/persistence`, {}),
      client.post(`/v1/instances/${id}/history`, {
        lastMinutes: historyRangeMinutes,
        maxPoints: HISTORY_MAX_POINTS,
      }),
      client.post(`/v1/instances/${id}/map-stats`, {}),
      client.post(`/v1/instances/${id}/operation-stats`, {}),
    ]);

    const [
      healthR,
      resourcesR,
      connectionsR,
      dataStatsR,
      efficiencyR,
      clusterR,
      replicationR,
      persistenceR,
      historyR,
      mapStatsR,
      operationStatsR,
    ] = results;
    const dataOf = (r) => (r.status === "fulfilled" ? r.value.data : null);

    setHealth(dataOf(healthR));
    setResources(dataOf(resourcesR));
    setConnections(dataOf(connectionsR));
    setDataStats(dataOf(dataStatsR));
    setEfficiency(dataOf(efficiencyR));
    setCluster(dataOf(clusterR));
    setReplication(dataOf(replicationR));
    setPersistence(dataOf(persistenceR));
    setHistory(dataOf(historyR) ?? { points: [], resolution: null, pointCount: 0 });
    setMapStats(dataOf(mapStatsR));
    setOperationStats(dataOf(operationStatsR));
    setLastUpdated(new Date());

    const rejected = results.filter((r) => r.status === "rejected");

    if (rejected.length === 0) {
      setError(null);
      clearTransientErrors();
    } else {
      const allRetryable = rejected.every((r) => r.reason?.problem?.retryable);

      if (allRetryable) {
        recordTransientError(rejected[0].reason);
      } else {
        setError(toProblem(rejected[0].reason, "Metrikler alınamadı."));
      }
    }

    setLoading(false);
  }, [id, historyRangeMinutes, clearTransientErrors, recordTransientError]);

  useEffect(() => {
    if (!instance || pollingStopped) return;

    // eslint-disable-next-line react-hooks/set-state-in-effect -- ilk veri çekimi için kasıtlı
    fetchMetrics();
    const intervalId = setInterval(fetchMetrics, REFRESH_INTERVAL_MS);

    return () => clearInterval(intervalId);
  }, [instance, fetchMetrics, pollingStopped]);

  const fetchAlertHistory = useCallback(async () => {
    setAlertHistoryLoading(true);
    setAlertHistoryError(null);
    try {
      const res = await client.post(`/v1/instances/${id}/history`, {
        lastMinutes: ALERT_HISTORY_LAST_MINUTES,
        maxPoints: ALERT_HISTORY_MAX_POINTS,
      });
      setAlertPoints(res.data?.points ?? []);
    } catch (err) {
      setAlertHistoryError(toProblem(err, "Uyarı geçmişi alınamadı."));
    } finally {
      setAlertHistoryLoading(false);
    }
  }, [id]);

  // Sayfa açılışında bir kez çekilir; grafiklerin 15 sn'lik polling'ine dahil değildir.
  useEffect(() => {
    if (!instance) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- ilk veri çekimi için kasıtlı
    fetchAlertHistory();
  }, [instance, fetchAlertHistory]);

  const maxMemoryBytes = dataStats?.maxMemoryBytes ?? null;
  const alertEvents = useMemo(
    () => extractAlertEvents(alertPoints, { maxMemory: maxMemoryBytes }),
    [alertPoints, maxMemoryBytes]
  );
  const activeAlertEvents = alertEvents.filter((ev) => !ev.resolved);
  const pastAlertEvents = alertEvents.filter((ev) => ev.resolved);

  function toggleMapExpanded(name) {
    setExpandedMaps((prev) => {
      const next = new Set(prev);
      if (next.has(name)) {
        next.delete(name);
      } else {
        next.add(name);
      }
      return next;
    });
  }

  if (loading) {
    return <div className="instance-detail-status">Yükleniyor...</div>;
  }

  if (error) {
    return <ProblemError error={error} className="instance-detail-error" />;
  }

  const isHazelcast = instance?.type === "HAZELCAST";
  // Backend ConnectionStats.getClientUsagePercent() zaten yüzde döner (activeClients/maxClients*100).
  // Burada tekrar 100 ile çarpmak değeri 100 kat şişiriyordu ve eşikleri erken tetikliyordu.
  const clientUsagePercent = connections?.clientUsagePercent ?? null;

  return (
    <div className="instance-detail-page">
      <div className="instance-detail-header">
        <h1 className="instance-detail-title">{instance?.name ?? `Instance #${id}`}</h1>
        <div className="instance-detail-header-right">
          {transientWarning && !pollingStopped && (
            <span className="instance-detail-transient-warning" title={transientWarning.message}>
              ⚠ {transientWarning.message} ({consecutiveTransientErrors}/{MAX_CONSECUTIVE_TRANSIENT_ERRORS})
            </span>
          )}
          {pollingStopped ? (
            <button type="button" className="instance-detail-reconnect-button" onClick={handleReconnect}>
              ⟳ Yeniden bağlan
            </button>
          ) : (
            <RefreshIndicator lastUpdated={lastUpdated} onRefresh={fetchMetrics} />
          )}
          <Link className="instance-detail-link" to={`/instances/${id}/keys`}>
            {isHazelcast ? "IMap Listesi →" : "Key Analizi →"}
          </Link>
        </div>
      </div>

      <div className="instance-detail-section">
        <h2 className="instance-detail-section-title">
          ⚠ Aktif Uyarılar{activeAlertEvents.length > 0 ? ` (${activeAlertEvents.length})` : ""}
        </h2>

        {alertHistoryLoading && (
          <div className="instance-detail-cluster-note">Uyarı geçmişi hesaplanıyor...</div>
        )}

        {!alertHistoryLoading && alertHistoryError && (
          <>
            <ProblemError error={alertHistoryError} className="instance-detail-error" />
            <button type="button" className="instance-detail-reconnect-button" onClick={fetchAlertHistory}>
              Tekrar dene
            </button>
          </>
        )}

        {!alertHistoryLoading && !alertHistoryError && (
          <>
            {activeAlertEvents.length === 0 ? (
              <div className="instance-detail-alert-clear">✓ Aktif uyarı yok</div>
            ) : (
              <ul className="instance-detail-alert-list">
                {activeAlertEvents.map((ev) => (
                  <li key={ev.ruleId} className="instance-detail-alert-item">
                    <span className={`instance-detail-alert-dot instance-detail-alert-dot-${ev.severity}`}>●</span>
                    <span className="instance-detail-alert-label">{ev.label}</span>
                    <span className="instance-detail-alert-time">{elapsedSince(ev.startedAt)} önce başladı</span>
                  </li>
                ))}
              </ul>
            )}

            {pastAlertEvents.length > 0 && (
              <div className="instance-detail-alert-history">
                <button
                  type="button"
                  className="instance-detail-alert-history-toggle"
                  onClick={() => setAlertHistoryOpen((open) => !open)}
                >
                  {alertHistoryOpen ? "▾" : "▸"} Geçmiş uyarılar ({pastAlertEvents.length})
                </button>

                {alertHistoryOpen && (
                  <ul className="instance-detail-alert-list instance-detail-alert-list-past">
                    {pastAlertEvents.map((ev, i) => (
                      <li key={`${ev.ruleId}-${ev.startedAt}-${i}`} className="instance-detail-alert-item">
                        <span className="instance-detail-alert-dot instance-detail-alert-dot-past">○</span>
                        <span className="instance-detail-alert-label instance-detail-alert-label-past">
                          {ev.label}
                        </span>
                        <span className="instance-detail-alert-time">
                          {alertEventRange(ev.startedAt, ev.endedAt)} ·{" "}
                          {formatDurationShort(new Date(ev.endedAt) - new Date(ev.startedAt))}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}

            <div className="instance-detail-alert-footnote">
              Son 7 günün verisinden hesaplanmıştır; kalıcı kayıt tutulmaz, eşik değişirse geçmiş yeniden hesaplanır.
              <button type="button" className="instance-detail-alert-refresh" onClick={fetchAlertHistory}>
                ⟳ Yenile
              </button>
            </div>
          </>
        )}
      </div>

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

      {resources && isSectionVisible(resources.jvmStatus) && (
        <div className="instance-detail-section">
          <h2 className="instance-detail-section-title">JVM &amp; Runtime</h2>
          <div className="instance-detail-grid">
            <MetricCard
              title="System CPU"
              value={formatPercent(resources.systemCpuPercent)}
              status={evaluateStatus("cpuUsagePercent", resources.systemCpuPercent)}
              metricStatus={resources.jvmStatus}
              meta={resources.meta}
            />
            <MetricCard
              title="Load Average"
              value={resources.systemLoadAverage != null ? resources.systemLoadAverage.toFixed(2) : "-"}
              metricStatus={resources.jvmStatus}
              meta={resources.meta}
            />
            <MetricCard
              title="Non-Heap Bellek"
              value={formatBytes(resources.nonHeapUsedBytes)}
              subtitle={
                resources.nonHeapMaxBytes === -1
                  ? "/ Limitsiz"
                  : resources.nonHeapMaxBytes != null
                    ? `/ ${formatBytes(resources.nonHeapMaxBytes)}`
                    : ""
              }
              metricStatus={resources.jvmStatus}
              meta={resources.meta}
            />
            {isApplicable(resources.gcStatus) && (
              <MetricCard
                title="GC Sayısı"
                value={resources.gcCollectionCount ?? "-"}
                metricStatus={resources.gcStatus}
                meta={resources.meta}
              />
            )}
            {isApplicable(resources.gcStatus) && (
              <MetricCard
                title="GC Süresi"
                value={resources.gcCollectionSeconds != null ? `${resources.gcCollectionSeconds.toFixed(2)} sn` : "-"}
                subtitle={
                  resources.gcCollectionSeconds != null && resources.gcCollectionCount
                    ? `Ort: ${((resources.gcCollectionSeconds / resources.gcCollectionCount) * 1000).toFixed(2)} ms/GC`
                    : ""
                }
                metricStatus={resources.gcStatus}
                meta={resources.meta}
              />
            )}
            {isApplicable(resources.threadStatus) && (
              <MetricCard
                title="Thread"
                value={resources.threadCount != null ? `${resources.threadCount} / peak ${resources.peakThreadCount}` : "-"}
                subtitle={resources.daemonThreadCount != null ? `Daemon: ${resources.daemonThreadCount}` : ""}
                metricStatus={resources.threadStatus}
                meta={resources.meta}
              />
            )}
            {isApplicable(resources.threadStatus) && (
              <MetricCard
                title="Deadlock"
                value={resources.deadlockedThreadCount > 0 ? `⚠ ${resources.deadlockedThreadCount} thread deadlock'ta` : "Yok"}
                status={resources.deadlockedThreadCount > 0 ? "critical" : "ok"}
                metricStatus={resources.threadStatus}
                meta={resources.meta}
              />
            )}
            {isApplicable(resources.fileDescriptorStatus) && (
              <MetricCard
                title="File Descriptor"
                value={
                  resources.openFileDescriptors != null
                    ? `${resources.openFileDescriptors} / ${resources.maxFileDescriptors}`
                    : "-"
                }
                subtitle={resources.fileDescriptorUsagePercent != null ? formatPercent(resources.fileDescriptorUsagePercent) : ""}
                status={evaluateStatus("fileDescriptorUsagePercent", resources.fileDescriptorUsagePercent)}
                metricStatus={resources.fileDescriptorStatus}
                meta={resources.meta}
              />
            )}
            <MetricCard
              title="Loaded Classes"
              value={resources.loadedClassCount ?? "-"}
              metricStatus={resources.jvmStatus}
              meta={resources.meta}
            />
            <MetricCard
              title="Member Uptime"
              value={formatUptime(resources.memberUptimeSeconds)}
              metricStatus={resources.jvmStatus}
              meta={resources.meta}
            />
          </div>
        </div>
      )}

      {mapStats && isSectionVisible(mapStats.overallStatus) && (
        <div className="instance-detail-section">
          <h2 className="instance-detail-section-title">Map İstatistikleri</h2>
          <div className="instance-detail-map-summary">
            <MetricCard
              title="Map Sayısı"
              value={mapStats.mapCount ?? "-"}
              metricStatus={mapStats.overallStatus}
              meta={mapStats.meta}
            />
            <MetricCard
              title="Toplam Entry"
              value={mapStats.totalEntryCount ?? "-"}
              metricStatus={mapStats.overallStatus}
              meta={mapStats.meta}
            />
          </div>

          {mapStats.memoryCostStatus === "UNAVAILABLE" && (
            <div className="instance-detail-cluster-note">
              Bellek metrikleri Prometheus scrape'inden alınır; erişilemiyor.
            </div>
          )}

          {mapStats.maps?.length > 0 ? (
            (() => {
              const showMemoryCols = isApplicable(mapStats.memoryCostStatus);
              const showOpCols = isApplicable(mapStats.operationStatus);
              // Sabit kolonlar (expand hariç): Map Adı, Entry, Owned/Backup, Hit Ratio, Eviction, Backup, Son Erişim.
              const detailColSpan = 7 + (showMemoryCols ? 2 : 0) + (showOpCols ? 2 : 0);

              return (
                <div className="instance-detail-table-scroll">
                  <table className="instance-detail-mini-table instance-detail-map-table">
                    <thead>
                      <tr>
                        <th />
                        <th>Map Adı</th>
                        <th>Entry</th>
                        <th>Owned / Backup</th>
                        {showMemoryCols && <th>Bellek</th>}
                        {showMemoryCols && <th>Heap Cost</th>}
                        <th>Hit Ratio</th>
                        {showOpCols && <th>Get / Put / Remove</th>}
                        {showOpCols && <th>Ort. Get Gecikme</th>}
                        <th>Eviction</th>
                        <th>Backup</th>
                        <th>Son Erişim</th>
                      </tr>
                    </thead>
                    <tbody>
                      {mapStats.maps.map((m) => {
                        const memoryUnavailable = mapStats.memoryCostStatus === "UNAVAILABLE";
                        const isExpanded = expandedMaps.has(m.name);

                        return (
                          <Fragment key={m.name}>
                            <tr>
                              <td>
                                <button
                                  type="button"
                                  className="instance-detail-map-expand-button"
                                  onClick={() => toggleMapExpanded(m.name)}
                                >
                                  {isExpanded ? "▾" : "▸"}
                                </button>
                              </td>
                              <td>{m.name}</td>
                              <td>{m.entryCount ?? "-"}</td>
                              <td>{m.ownedEntryCount ?? "-"} / {m.backupEntryCount ?? "-"}</td>
                              {showMemoryCols && (
                                <td>{memoryUnavailable ? "—" : formatBytes(m.ownedEntryMemoryCost)}</td>
                              )}
                              {showMemoryCols && <td>{memoryUnavailable ? "—" : formatBytes(m.heapCost)}</td>}
                              <td>{m.hitRatio != null ? formatPercent(m.hitRatio * 100) : "N/A"}</td>
                              {showOpCols && (
                                <td>{m.getCount ?? "-"} / {m.putCount ?? "-"} / {m.removeCount ?? "-"}</td>
                              )}
                              {showOpCols && (
                                <td>{m.avgGetLatencyMillis != null ? `${m.avgGetLatencyMillis.toFixed(3)} ms` : "—"}</td>
                              )}
                              <td className={m.evictionCount > 0 ? "instance-detail-map-warn" : ""}>
                                {m.evictionCount ?? "-"}
                              </td>
                              <td>{m.backupCount ?? "-"}</td>
                              <td>
                                {m.lastAccessTime === 0 ? "Hiç erişilmedi" : formatEpochMillis(m.lastAccessTime)}
                              </td>
                            </tr>
                            {isExpanded && (
                              <tr className="instance-detail-map-detail-row">
                                <td />
                                <td colSpan={detailColSpan}>
                                  <div className="instance-detail-map-detail-grid">
                                    <span>Locked Entry: {m.lockedEntryCount ?? "-"}</span>
                                    <span>Dirty Entry: {m.dirtyEntryCount ?? "-"}</span>
                                    <span>Expiration: {m.expirationCount ?? "-"}</span>
                                    <span>
                                      Son Güncelleme:{" "}
                                      {m.lastUpdateTime === 0 ? "Hiç güncellenmedi" : formatEpochMillis(m.lastUpdateTime)}
                                    </span>
                                    {showOpCols && (
                                      <span>
                                        Ort. Put Gecikme:{" "}
                                        {m.avgPutLatencyMillis != null ? `${m.avgPutLatencyMillis.toFixed(3)} ms` : "—"}
                                      </span>
                                    )}
                                  </div>
                                </td>
                              </tr>
                            )}
                          </Fragment>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              );
            })()
          ) : (
            <div className="instance-detail-cluster-note">Map bulunamadı.</div>
          )}
        </div>
      )}

      {operationStats && isSectionVisible(operationStats.overallStatus) && (
        <div className="instance-detail-section">
          <button
            type="button"
            className="instance-detail-collapsible-header"
            onClick={() => setOperationSectionOpen((open) => !open)}
          >
            <h2 className="instance-detail-section-title">Operation &amp; Event</h2>
            <span className="instance-detail-collapsible-toggle">{operationSectionOpen ? "▾ Gizle" : "▸ Göster"}</span>
          </button>

          {operationSectionOpen && (
            <>
              {isApplicable(operationStats.operationStatus) && (
                <>
                  <h3 className="instance-detail-persistence-block-title">Operation</h3>
                  <div className="instance-detail-grid">
                    <MetricCard
                      title="Queue Size"
                      value={operationStats.queueSize ?? "-"}
                      status={
                        operationStats.queueSize > 100 ? "critical" : operationStats.queueSize > 0 ? "warning" : "ok"
                      }
                      metricStatus={operationStats.operationStatus}
                      meta={operationStats.meta}
                    />
                    <MetricCard
                      title="Running"
                      value={operationStats.runningCount ?? "-"}
                      metricStatus={operationStats.operationStatus}
                      meta={operationStats.meta}
                    />
                    <MetricCard
                      title="Completed"
                      value={formatCompactNumber(operationStats.completedCount)}
                      metricStatus={operationStats.operationStatus}
                      meta={operationStats.meta}
                    />
                    <MetricCard
                      title="Response Queue"
                      value={operationStats.responseQueueSize ?? "-"}
                      metricStatus={operationStats.operationStatus}
                      meta={operationStats.meta}
                    />
                    <MetricCard
                      title="Thread Havuzu"
                      value={
                        operationStats.partitionThreadCount != null
                          ? `${operationStats.partitionThreadCount} partition / ${operationStats.genericThreadCount} generic`
                          : "-"
                      }
                      metricStatus={operationStats.operationStatus}
                      meta={operationStats.meta}
                    />
                  </div>

                  <h3 className="instance-detail-persistence-block-title" style={{ marginTop: 20 }}>
                    Invocations &amp; Timeouts
                  </h3>
                  <div className="instance-detail-grid">
                    <MetricCard
                      title="Pending"
                      value={operationStats.pendingInvocations ?? "-"}
                      metricStatus={operationStats.operationStatus}
                      meta={operationStats.meta}
                    />
                    <MetricCard
                      title="Backup Timeout"
                      value={operationStats.backupTimeouts ?? "-"}
                      status={operationStats.backupTimeouts > 0 ? "critical" : "ok"}
                      metricStatus={operationStats.operationStatus}
                      meta={operationStats.meta}
                    />
                    <MetricCard
                      title="Normal Timeout"
                      value={operationStats.normalTimeouts ?? "-"}
                      status={operationStats.normalTimeouts > 0 ? "critical" : "ok"}
                      metricStatus={operationStats.operationStatus}
                      meta={operationStats.meta}
                    />
                    <MetricCard
                      title="Response Error"
                      value={operationStats.responseErrorCount ?? "-"}
                      status={operationStats.responseErrorCount > 0 ? "critical" : "ok"}
                      metricStatus={operationStats.operationStatus}
                      meta={operationStats.meta}
                    />
                  </div>
                </>
              )}

              {isApplicable(operationStats.eventStatus) && (
                <>
                  <h3 className="instance-detail-persistence-block-title" style={{ marginTop: 20 }}>
                    Event
                  </h3>
                  <div className="instance-detail-grid">
                    <MetricCard
                      title="Event Queue"
                      value={operationStats.eventQueueSize ?? "-"}
                      status={operationStats.eventQueueSize > 0 ? "warning" : "ok"}
                      metricStatus={operationStats.eventStatus}
                      meta={operationStats.meta}
                    />
                    <MetricCard
                      title="İşlenen"
                      value={formatCompactNumber(operationStats.eventsProcessed)}
                      metricStatus={operationStats.eventStatus}
                      meta={operationStats.meta}
                    />
                    <MetricCard
                      title="Reddedilen"
                      value={operationStats.eventRejectedCount ?? "-"}
                      status={operationStats.eventRejectedCount > 0 ? "critical" : "ok"}
                      metricStatus={operationStats.eventStatus}
                      meta={operationStats.meta}
                    />
                    <MetricCard
                      title="Teslim Hatası"
                      value={operationStats.syncDeliveryFailureCount ?? "-"}
                      status={operationStats.syncDeliveryFailureCount > 0 ? "critical" : "ok"}
                      metricStatus={operationStats.eventStatus}
                      meta={operationStats.meta}
                    />
                  </div>
                </>
              )}

              {isApplicable(operationStats.executorStatus) && operationStats.executors?.length > 0 && (
                <>
                  <h3 className="instance-detail-persistence-block-title" style={{ marginTop: 20 }}>
                    Executor'lar
                  </h3>
                  <table className="instance-detail-mini-table">
                    <thead>
                      <tr>
                        <th>Ad</th>
                        <th>Queue</th>
                        <th>Pool Size</th>
                        <th>Tamamlanan</th>
                        <th>Kalan Kapasite</th>
                      </tr>
                    </thead>
                    <tbody>
                      {operationStats.executors.map((ex) => (
                        <tr key={ex.name}>
                          <td>{ex.name}</td>
                          <td>{ex.queueSize}</td>
                          <td>{ex.poolSize}</td>
                          <td>{ex.completedTaskCount}</td>
                          <td>{ex.remainingQueueCapacity}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </>
              )}
            </>
          )}
        </div>
      )}

      {efficiency && isSectionVisible(efficiency.overallStatus, [
        efficiency.throughputStatus,
        efficiency.networkStatus,
        efficiency.overallStatus,
      ]) && (
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
      )}

      {/* Standalone Redis'te overallStatus NOT_APPLICABLE gelir (cluster kavramı yok) — bölüm tamamen
          gizlenir. Redis Cluster modunda NOT_READY gelir — bu istisnadır, bölüm bir notla görünür kalır.
          cluster null iken (veri henüz gelmedi/hata) mevcut yükleme/hata notu korunur. */}
      {(!cluster || isApplicable(cluster.overallStatus)) && (
        <div className="instance-detail-section">
          <h2 className="instance-detail-section-title">Cluster</h2>
          {!cluster ? (
            <div className="instance-detail-cluster-note">Cluster bilgisi alınamadı.</div>
          ) : cluster.overallStatus === "NOT_READY" ? (
            <div className="instance-detail-cluster-note">Cluster modu aktif, detaylar henüz desteklenmiyor.</div>
          ) : (
            <div className="instance-detail-grid">
              <MetricCard
                title="Node Sayısı"
                value={cluster.nodeCount}
                metricStatus={cluster.overallStatus}
                meta={cluster.meta}
              />
              <MetricCard
                title="Partition Sayısı"
                value={cluster.partitionCount}
                metricStatus={cluster.partitionCountStatus ?? cluster.overallStatus}
                meta={cluster.meta}
              />
              <MetricCard
                title="Durum"
                // balanced null olabilir (partition tablosu henüz gelmemişse).
                // null'ı "Dengesiz" göstermek yanlış iddia olur.
                value={cluster.balanced == null ? "Bilinmiyor" : cluster.balanced ? "Dengeli" : "Dengesiz"}
                subtitle={cluster.clusterName}
                metricStatus={cluster.balancedStatus ?? cluster.overallStatus}
                meta={cluster.meta}
              />
            </div>
          )}
        </div>
      )}

      {/* Hazelcast'te replication kavramı gerçekten yok (ReplicationInfo.notApplicable()) — tip bazlı gizlenir.
          Redis standalone'da overallStatus NOT_APPLICABLE gelir ama kavram var, sadece yapılandırılmamış —
          bu istisnadır, bölüm bir notla görünür kalır (gizlenmez). */}
      {!isHazelcast && replication && (
        <div className="instance-detail-section">
          <h2 className="instance-detail-section-title">Replication</h2>
          {replication.overallStatus === "NOT_APPLICABLE" ? (
            <div className="instance-detail-cluster-note">Replication yapılandırılmamış.</div>
          ) : (
            <>
              <div className="instance-detail-grid">
                <MetricCard
                  title="Rol"
                  value={replication.role === "master" ? "Master" : "Replica"}
                  metricStatus={replication.overallStatus}
                  meta={replication.meta}
                />
                {replication.role === "master" ? (
                  <MetricCard
                    title="Bağlı Replica Sayısı"
                    value={replication.connectedReplicas}
                    metricStatus={replication.overallStatus}
                    meta={replication.meta}
                  />
                ) : (
                  <>
                    <MetricCard
                      title="Master Bağlantısı"
                      value={replication.masterLinkStatus === "up" ? "Bağlı" : "Kopuk"}
                      status={replication.masterLinkStatus === "down" ? "critical" : "ok"}
                      metricStatus={replication.overallStatus}
                      meta={replication.meta}
                    />
                    <MetricCard
                      title="Son I/O"
                      value={replication.masterLastIoSecondsAgo != null ? `${replication.masterLastIoSecondsAgo} sn` : "-"}
                      metricStatus={replication.overallStatus}
                      meta={replication.meta}
                    />
                    <MetricCard
                      title="Replica Gecikmesi"
                      value={replication.replicaLagBytes != null ? formatBytes(replication.replicaLagBytes) : "-"}
                      metricStatus={replication.overallStatus}
                      meta={replication.meta}
                    />
                  </>
                )}
              </div>
              {replication.replicas?.length > 0 && (
                <table className="instance-detail-mini-table">
                  <thead>
                    <tr>
                      <th>Host</th>
                      <th>Port</th>
                      <th>Durum</th>
                      <th>Offset</th>
                      <th>Lag</th>
                    </tr>
                  </thead>
                  <tbody>
                    {replication.replicas.map((r) => (
                      <tr key={`${r.host}:${r.port}`}>
                        <td>{r.host}</td>
                        <td>{r.port}</td>
                        <td>{r.state}</td>
                        <td>{r.offset}</td>
                        <td>{r.lag}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </>
          )}
        </div>
      )}

      {!isHazelcast && persistence && isSectionVisible(persistence.overallStatus) && (
        <div className="instance-detail-section">
          <h2 className="instance-detail-section-title">Persistence</h2>
          <div className="instance-detail-persistence-grid">
            {isApplicable(persistence.rdbStatus) && (
              <div className="instance-detail-persistence-block">
                <h3 className="instance-detail-persistence-block-title">RDB</h3>
                {persistence.rdbEnabled === false ? (
                  <span className="instance-detail-persistence-disabled-badge">RDB devre dışı</span>
                ) : (
                  <>
                    <div className="instance-detail-grid">
                      <MetricCard
                        title="Etkin"
                        value={persistence.rdbEnabled ? "Evet" : "Hayır"}
                        metricStatus={persistence.rdbStatus}
                        meta={persistence.meta}
                      />
                      <MetricCard
                        title="Son Kayıt Durumu"
                        value={persistence.rdbLastBgsaveStatus ?? "-"}
                        status={
                          persistence.rdbLastBgsaveStatus && persistence.rdbLastBgsaveStatus !== "ok"
                            ? "critical"
                            : "ok"
                        }
                        metricStatus={persistence.rdbStatus}
                        meta={persistence.meta}
                      />
                      <MetricCard
                        title="Son Kayıt Zamanı"
                        value={formatEpochSeconds(persistence.rdbLastSaveTime)}
                        metricStatus={persistence.rdbStatus}
                        meta={persistence.meta}
                      />
                      <MetricCard
                        title="Kayıt Sonrası Değişiklik"
                        value={persistence.rdbChangesSinceLastSave ?? "-"}
                        metricStatus={persistence.rdbStatus}
                        meta={persistence.meta}
                      />
                    </div>
                    {persistence.rdbBgsaveInProgress && (
                      <div className="instance-detail-persistence-note">Arka planda kayıt yapılıyor...</div>
                    )}
                  </>
                )}
              </div>
            )}
            {isApplicable(persistence.aofStatus) && (
              <div className="instance-detail-persistence-block">
                <h3 className="instance-detail-persistence-block-title">AOF</h3>
                {persistence.aofEnabled === false ? (
                  <span className="instance-detail-persistence-disabled-badge">AOF devre dışı</span>
                ) : (
                  <div className="instance-detail-grid">
                    <MetricCard
                      title="Etkin"
                      value={persistence.aofEnabled ? "Evet" : "Hayır"}
                      metricStatus={persistence.aofStatus}
                      meta={persistence.meta}
                    />
                    <MetricCard
                      title="Son Rewrite Durumu"
                      value={persistence.aofLastBgrewriteStatus ?? "-"}
                      status={
                        persistence.aofLastBgrewriteStatus && persistence.aofLastBgrewriteStatus !== "ok"
                          ? "critical"
                          : "ok"
                      }
                      metricStatus={persistence.aofStatus}
                      meta={persistence.meta}
                    />
                    <MetricCard
                      title="Son Yazma Durumu"
                      value={persistence.aofLastWriteStatus ?? "-"}
                      status={
                        persistence.aofLastWriteStatus && persistence.aofLastWriteStatus !== "ok"
                          ? "critical"
                          : "ok"
                      }
                      metricStatus={persistence.aofStatus}
                      meta={persistence.meta}
                    />
                  </div>
                )}
              </div>
            )}
          </div>
          {persistence.loading && (
            <div className="instance-detail-persistence-note instance-detail-persistence-warning">
              ⚠ Veri seti yükleniyor
            </div>
          )}
        </div>
      )}

      <div className="instance-detail-timeseries">
        <div className="instance-detail-timeseries-header">
          <h2 className="instance-detail-section-title">
            Zaman Serisi
            {history.resolution === "DOWNSAMPLED" && (
              <span className="instance-detail-timeseries-resolution-note">
                {" "}
                · Örneklenmiş veri ({history.pointCount} nokta)
              </span>
            )}
          </h2>
          <div className="instance-detail-range-toggle">
            {HISTORY_RANGES.map((range) => (
              <button
                key={range.minutes}
                type="button"
                className={`instance-detail-range-button ${
                  historyRangeMinutes === range.minutes ? "active" : ""
                }`}
                onClick={() => setHistoryRangeMinutes(range.minutes)}
              >
                {range.label}
              </button>
            ))}
          </div>
        </div>

        {history.points.length === 0 ? (
          <div className="instance-detail-timeseries-empty">
            Henüz yeterli veri toplanmadı. Birkaç dakika bekleyin.
          </div>
        ) : (
          <div className="instance-detail-chart-grid">
            <TimeSeriesChart
              title="CPU Kullanımı (%)"
              data={history.points}
              dataKey="cpuPercent"
              color="var(--accent)"
              seriesName="CPU"
              valueFormatter={(value) => `${value.toFixed(1)}%`}
            />
            <TimeSeriesChart
              title="Bellek Kullanımı (MB)"
              data={history.points.map((s) => ({
                ...s,
                memoryUsedMB: s.memoryUsed != null ? s.memoryUsed / (1024 * 1024) : null,
              }))}
              dataKey="memoryUsedMB"
              color="var(--accent)"
              seriesName="Bellek"
              valueFormatter={(value) => `${value.toFixed(1)} MB`}
            />
            <TimeSeriesChart
              title="Key Sayısı"
              data={history.points}
              dataKey="keyCount"
              color="var(--green)"
              seriesName="Key Sayısı"
            />
            <TimeSeriesChart
              title="Aktif Bağlantılar"
              data={history.points}
              dataKey="clientCount"
              color="var(--yellow)"
              seriesName="Bağlantı"
            />
            <TimeSeriesChart
              title="Hit Ratio (%)"
              data={history.points.map((s) => ({
                ...s,
                hitRatioPercent: s.hitRatio != null ? s.hitRatio * 100 : null,
              }))}
              dataKey="hitRatioPercent"
              color="var(--green)"
              seriesName="Hit Ratio"
              valueFormatter={(value) => `${value.toFixed(1)}%`}
            />
            <TimeSeriesChart
              title="Ops/sec"
              data={history.points}
              dataKey="opsPerSec"
              color="var(--yellow)"
              seriesName="Ops/sec"
            />
            <TimeSeriesChart
              title="Evicted Keys"
              data={history.points}
              dataKey="evictedKeys"
              color="var(--red)"
              seriesName="Evicted Keys"
            />
          </div>
        )}
      </div>
    </div>
  );
}
