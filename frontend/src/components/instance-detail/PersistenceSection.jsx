import MetricCard from "../MetricCard";
import { isApplicable, isSectionVisible } from "../../config/MetricStatus";
import { formatEpochSeconds } from "../../utils/format";

export default function PersistenceSection({ persistence, isHazelcast }) {
  if (!(!isHazelcast && persistence && isSectionVisible(persistence.overallStatus))) return null;

  return (
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
  );
}
