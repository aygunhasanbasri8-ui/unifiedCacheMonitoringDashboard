import { useEffect, useState, useCallback } from "react";
import { useParams, Link } from "react-router-dom";
import client from "../api/client";
import { toProblem } from "../api/problemDetails";
import RefreshIndicator from "../components/RefreshIndicator";
import ProblemError from "../components/ProblemError";
import AlertsSection from "../components/instance-detail/AlertsSection";
import SummaryMetrics from "../components/instance-detail/SummaryMetrics";
import JvmSection from "../components/instance-detail/JvmSection";
import MapStatsSection from "../components/instance-detail/MapStatsSection";
import OperationSection from "../components/instance-detail/OperationSection";
import EfficiencySection from "../components/instance-detail/EfficiencySection";
import ClusterSection from "../components/instance-detail/ClusterSection";
import ReplicationSection from "../components/instance-detail/ReplicationSection";
import PersistenceSection from "../components/instance-detail/PersistenceSection";
import TimeSeriesSection from "../components/instance-detail/TimeSeriesSection";
import { useTransientErrors, MAX_CONSECUTIVE_TRANSIENT_ERRORS } from "../hooks/useTransientErrors";
import { useAlertHistory } from "../hooks/useAlertHistory";
import "./InstanceDetail.css";

const REFRESH_INTERVAL_MS = 15000;
const HISTORY_MAX_POINTS = 500;
const HISTORY_RANGES = [
  { label: "1 saat", minutes: 60 },
  { label: "6 saat", minutes: 360 },
  { label: "24 saat", minutes: 1440 },
  { label: "7 gün", minutes: 10080 },
];

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
  const [operationStats, setOperationStats] = useState(null);

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

  const alerts = useAlertHistory(id, Boolean(instance), dataStats?.maxMemoryBytes ?? null);

  if (loading) {
    return <div className="instance-detail-status">Yükleniyor...</div>;
  }

  if (error) {
    return <ProblemError error={error} className="instance-detail-error" />;
  }

  const isHazelcast = instance?.type === "HAZELCAST";

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

      <AlertsSection
        activeAlertEvents={alerts.activeAlertEvents}
        pastAlertEvents={alerts.pastAlertEvents}
        loading={alerts.loading}
        error={alerts.error}
        onReload={alerts.reload}
      />
      <SummaryMetrics
        instanceId={id}
        isHazelcast={isHazelcast}
        health={health}
        resources={resources}
        connections={connections}
        dataStats={dataStats}
        efficiency={efficiency}
      />
      <JvmSection resources={resources} />
      <MapStatsSection mapStats={mapStats} />
      <OperationSection operationStats={operationStats} />
      <EfficiencySection efficiency={efficiency} />
      <ClusterSection cluster={cluster} />
      <ReplicationSection replication={replication} isHazelcast={isHazelcast} />
      <PersistenceSection persistence={persistence} isHazelcast={isHazelcast} />
      <TimeSeriesSection
        history={history}
        ranges={HISTORY_RANGES}
        rangeMinutes={historyRangeMinutes}
        onRangeChange={setHistoryRangeMinutes}
      />
    </div>
  );
}
