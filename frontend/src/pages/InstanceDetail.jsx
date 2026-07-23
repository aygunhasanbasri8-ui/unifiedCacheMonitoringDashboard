import { useEffect, useState, useCallback } from "react";
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
import MetricCard from "../components/MetricCard";
import DonutMetric from "../components/DonutMetric";
import RefreshIndicator from "../components/RefreshIndicator";
import { evaluateStatus, healthStatus } from "../config/thresholds";
import { formatBytes, formatUptime, formatPercent, formatTime } from "../utils/format";
import "./InstanceDetail.css";

const REFRESH_INTERVAL_MS = 15000;
const SNAPSHOT_LAST_MINUTES = 60;

function TimeSeriesChart({ title, data, dataKey, color, valueFormatter, seriesName }) {
  const tickInterval = Math.max(0, Math.ceil(data.length / 6) - 1);

  return (
    <div className="instance-detail-chart-card">
      <div className="instance-detail-chart-title">{title}</div>
      <ResponsiveContainer width="100%" height={220}>
        <LineChart data={data} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
          <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
          <XAxis
            dataKey="collectedAt"
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
            formatter={(value) => [valueFormatter ? valueFormatter(value) : value, seriesName]}
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
  const [clusterNote, setClusterNote] = useState("");
  const [snapshots, setSnapshots] = useState([]);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [lastUpdated, setLastUpdated] = useState(null);

  useEffect(() => {
    let cancelled = false;

    client
      .get(`/v1/instances/${id}`)
      .then((res) => {
        if (!cancelled) setInstance(res.data);
      })
      .catch(() => {
        if (!cancelled) {
          setError("Instance bilgisi alınamadı.");
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [id]);

  const fetchMetrics = useCallback(
    async (instanceType) => {
      try {
        const [healthRes, resourcesRes, connectionsRes, dataStatsRes, efficiencyRes, snapshotsRes] =
          await Promise.all([
            client.get(`/v1/instances/${id}/health`),
            client.get(`/v1/instances/${id}/resources`),
            client.get(`/v1/instances/${id}/connections`),
            client.get(`/v1/instances/${id}/data-stats`),
            client.get(`/v1/instances/${id}/efficiency`),
            client.get(`/v1/instances/${id}/snapshots`, {
              params: { lastMinutes: SNAPSHOT_LAST_MINUTES },
            }),
          ]);

        setHealth(healthRes.data);
        setResources(resourcesRes.data);
        setConnections(connectionsRes.data);
        setDataStats(dataStatsRes.data);
        setEfficiency(efficiencyRes.data);
        setSnapshots(snapshotsRes.data);
        setLastUpdated(new Date());

        if (instanceType === "HAZELCAST") {
          try {
            const clusterRes = await client.get(`/v1/instances/${id}/cluster`);
            setCluster(clusterRes.data);
            setClusterNote("");
          } catch {
            setCluster(null);
            setClusterNote("Cluster bilgisi alınamadı.");
          }
        } else {
          setCluster(null);
          setClusterNote("Cluster bilgisi yok (Redis)");
        }

        setError("");
      } catch {
        setError("Metrikler alınamadı.");
      } finally {
        setLoading(false);
      }
    },
    [id]
  );

  useEffect(() => {
    if (!instance) return;

    // eslint-disable-next-line react-hooks/set-state-in-effect -- ilk veri çekimi için kasıtlı
    fetchMetrics(instance.type);
    const intervalId = setInterval(() => fetchMetrics(instance.type), REFRESH_INTERVAL_MS);

    return () => clearInterval(intervalId);
  }, [instance, fetchMetrics]);

  if (loading) {
    return <div className="instance-detail-status">Yükleniyor...</div>;
  }

  if (error) {
    return <div className="instance-detail-error">{error}</div>;
  }

  const isHazelcast = instance?.type === "HAZELCAST";

  return (
    <div className="instance-detail-page">
      <div className="instance-detail-header">
        <h1 className="instance-detail-title">{instance?.name ?? `Instance #${id}`}</h1>
        <div className="instance-detail-header-right">
          <RefreshIndicator
            lastUpdated={lastUpdated}
            onRefresh={() => fetchMetrics(instance?.type)}
          />
          <Link className="instance-detail-link" to={`/instances/${id}/keys`}>
            {isHazelcast ? "IMap Listesi →" : "Key Analizi →"}
          </Link>
        </div>
      </div>

      <div className="instance-detail-donuts">
        <DonutMetric
          title="CPU Kullanımı"
          percent={resources?.cpuUsagePercent}
          status={resources ? evaluateStatus("cpuUsagePercent", resources.cpuUsagePercent) : "ok"}
        />
        <DonutMetric
          title="Bellek Kullanım Yüzdesi"
          percent={resources?.memoryUsagePercent}
          status={resources ? evaluateStatus("memoryUsagePercent", resources.memoryUsagePercent) : "ok"}
        />
        {!isHazelcast && (
          <DonutMetric
            title="Hit Ratio"
            percent={efficiency?.hitRatio != null ? efficiency.hitRatio * 100 : null}
            status={efficiency ? evaluateStatus("hitRatio", efficiency.hitRatio) : "ok"}
            subtitle={efficiency?.missRatio != null ? `Miss: ${formatPercent(efficiency.missRatio * 100)}` : ""}
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
        <MetricCard
          title="Bellek Kullanımı"
          value={resources ? formatBytes(resources.memoryUsedBytes) : "-"}
          subtitle={
            resources
              ? `/ ${formatBytes(resources.memoryMaxBytes)} · ${formatPercent(resources.memoryUsagePercent)}`
              : ""
          }
          status={resources ? evaluateStatus("memoryUsagePercent", resources.memoryUsagePercent) : "ok"}
        />
        <MetricCard
          title="Aktif Bağlantılar"
          value={connections?.activeClients ?? "-"}
          subtitle={
            connections && !isHazelcast
              ? `Hata: ${connections.totalConnectionErrors} · Reddedilen: ${connections.rejectedConnections}`
              : ""
          }
          status={
            connections && !isHazelcast
              ? evaluateStatus("connectionErrors", connections.totalConnectionErrors)
              : "ok"
          }
        />
        <MetricCard
          title="Toplam Key Sayısı"
          value={dataStats?.totalKeyCount ?? "-"}
          subtitle={dataStats && !isHazelcast ? `Fragmentasyon: ${dataStats.fragmentationRatio}` : ""}
        />
        {!isHazelcast && (
          <MetricCard
            title="Fragmentasyon Oranı"
            value={resources ? resources.fragmentationRatio.toFixed(2) : "-"}
            status={resources ? evaluateStatus("fragmentationRatio", resources.fragmentationRatio) : "ok"}
          />
        )}
      </div>

      <div className="instance-detail-cluster">
        <h2 className="instance-detail-section-title">Cluster</h2>
        {cluster ? (
          <div className="instance-detail-grid">
            <MetricCard title="Node Sayısı" value={cluster.nodeCount} />
            <MetricCard title="Partition Sayısı" value={cluster.partitionCount} />
            <MetricCard
              title="Durum"
              value={cluster.balanced ? "Dengeli" : "Dengesiz"}
              subtitle={cluster.clusterName}
            />
          </div>
        ) : (
          <div className="instance-detail-cluster-note">{clusterNote}</div>
        )}
      </div>

      <div className="instance-detail-timeseries">
        <h2 className="instance-detail-section-title">Zaman Serisi</h2>
        {snapshots.length === 0 ? (
          <div className="instance-detail-timeseries-empty">
            Henüz yeterli veri toplanmadı. Birkaç dakika bekleyin.
          </div>
        ) : (
          <div className="instance-detail-chart-grid">
            <TimeSeriesChart
              title="Bellek Kullanımı (MB)"
              data={snapshots.map((s) => ({
                ...s,
                memoryUsedMB: s.memoryUsed / (1024 * 1024),
              }))}
              dataKey="memoryUsedMB"
              color="var(--accent)"
              seriesName="Bellek"
              valueFormatter={(value) => `${value.toFixed(1)} MB`}
            />
            <TimeSeriesChart
              title="Key Sayısı"
              data={snapshots}
              dataKey="keyCount"
              color="var(--green)"
              seriesName="Key Sayısı"
            />
            <TimeSeriesChart
              title="Aktif Bağlantılar"
              data={snapshots}
              dataKey="clientCount"
              color="var(--yellow)"
              seriesName="Bağlantı"
            />
            {!isHazelcast && (
              <TimeSeriesChart
                title="Bellek Fragmentasyon Oranı"
                data={snapshots}
                dataKey="fragmentationRatio"
                color="var(--red)"
                seriesName="Fragmentasyon"
              />
            )}
          </div>
        )}
      </div>
    </div>
  );
}
