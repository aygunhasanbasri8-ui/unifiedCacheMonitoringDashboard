import MetricCard from "../MetricCard";
import { isApplicable } from "../../config/MetricStatus";

// Standalone Redis'te overallStatus NOT_APPLICABLE gelir (cluster kavramı yok), bölüm tamamen
// gizlenir. Redis Cluster modunda NOT_READY gelir: bu istisnadır, bölüm bir notla görünür kalır.
// cluster null iken (veri henüz gelmedi/hata) yükleme/hata notu gösterilir.
export default function ClusterSection({ cluster }) {
  if (!(!cluster || isApplicable(cluster.overallStatus))) return null;

  return (
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
  );
}
