import { useState } from "react";
import MetricCard from "../MetricCard";
import { isApplicable, isSectionVisible } from "../../config/MetricStatus";
import { formatCompactNumber } from "../../utils/format";

export default function OperationSection({ operationStats }) {
  const [operationSectionOpen, setOperationSectionOpen] = useState(false);

  if (!(operationStats && isSectionVisible(operationStats.overallStatus))) return null;

  return (
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
  );
}
