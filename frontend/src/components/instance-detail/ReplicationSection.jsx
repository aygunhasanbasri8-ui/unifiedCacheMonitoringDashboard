import MetricCard from "../MetricCard";
import { formatBytes } from "../../utils/format";

// Hazelcast'te replication kavramı gerçekten yok (ReplicationInfo.notApplicable()), tip bazlı gizlenir.
// Redis standalone'da overallStatus NOT_APPLICABLE gelir ama kavram var, sadece yapılandırılmamış:
// bu istisnadır, bölüm bir notla görünür kalır.
export default function ReplicationSection({ replication, isHazelcast }) {
  if (!(!isHazelcast && replication)) return null;

  return (
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
  );
}
