import MetricCard from "../MetricCard";
import { evaluateStatus } from "../../config/thresholds";
import { isApplicable, isSectionVisible } from "../../config/MetricStatus";
import { formatBytes, formatUptime, formatPercent } from "../../utils/format";

export default function JvmSection({ resources }) {
  if (!(resources && isSectionVisible(resources.jvmStatus))) return null;

  return (
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
  );
}
