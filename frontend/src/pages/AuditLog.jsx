import { useEffect, useState, useCallback } from "react";
import client from "../api/client";
import { formatDateTime } from "../utils/format";
import "./AuditLog.css";

export default function AuditLog() {
  const [logs, setLogs] = useState([]);
  const [availableActions, setAvailableActions] = useState([]);
  const [selectedAction, setSelectedAction] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const fetchLogs = useCallback(async (action) => {
    setLoading(true);
    setError("");

    try {
      const res = await client.get("/v1/audit", {
        params: action ? { action } : {},
      });
      const sorted = [...res.data].sort(
        (a, b) => new Date(b.timestamp) - new Date(a.timestamp)
      );
      setLogs(sorted);

      if (!action) {
        const unique = Array.from(new Set(sorted.map((log) => log.action))).sort();
        setAvailableActions(unique);
      }
    } catch (err) {
      if (err.response?.status === 403) {
        setError("Bu sayfaya erişim yetkiniz yok.");
      } else {
        setError("Audit kayıtları alınamadı.");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- ilk veri çekimi için kasıtlı
    fetchLogs("");
  }, [fetchLogs]);

  function handleFilterChange(e) {
    const action = e.target.value;
    setSelectedAction(action);
    fetchLogs(action);
  }

  return (
    <div className="audit-log-page">
      <h1 className="audit-log-title">Denetim Kayıtları (Audit Log)</h1>

      <div className="audit-log-filter">
        <label className="audit-log-filter-label" htmlFor="audit-action-filter">
          Aksiyon
        </label>
        <select
          id="audit-action-filter"
          className="audit-log-filter-select"
          value={selectedAction}
          onChange={handleFilterChange}
        >
          <option value="">Tümü</option>
          {availableActions.map((action) => (
            <option key={action} value={action}>
              {action}
            </option>
          ))}
        </select>
      </div>

      {loading && <div className="audit-log-status">Yükleniyor...</div>}
      {error && <div className="audit-log-error">{error}</div>}

      {!loading && !error && (
        <table className="audit-log-table">
          <thead>
            <tr>
              <th>Zaman</th>
              <th>Kullanıcı</th>
              <th>Aksiyon</th>
              <th>Detay</th>
              <th>Sonuç</th>
            </tr>
          </thead>
          <tbody>
            {logs.map((log, index) => (
              <tr key={`${log.username}-${log.timestamp}-${index}`}>
                <td>{formatDateTime(log.timestamp)}</td>
                <td>{log.username}</td>
                <td>{log.action}</td>
                <td>{log.detail}</td>
                <td>
                  <span className={`audit-log-result audit-log-result-${log.result?.toLowerCase()}`}>
                    {log.result}
                  </span>
                </td>
              </tr>
            ))}
            {logs.length === 0 && (
              <tr>
                <td colSpan={5} className="audit-log-empty">
                  Henüz audit kaydı yok.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </div>
  );
}
