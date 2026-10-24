import { Fragment, useState } from "react";
import MetricCard from "../MetricCard";
import { isApplicable, isSectionVisible } from "../../config/MetricStatus";
import { formatBytes, formatPercent, formatEpochMillis } from "../../utils/format";

export default function MapStatsSection({ mapStats }) {
  // Açık satırlar bileşende tutuluyor; bölüm gizlense de bileşen yerinde kaldığı için
  // tek bir başarısız yoklama açılmış satırları kapatmıyor.
  const [expandedMaps, setExpandedMaps] = useState(new Set());

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

  if (!(mapStats && isSectionVisible(mapStats.overallStatus))) return null;

  return (
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
  );
}
