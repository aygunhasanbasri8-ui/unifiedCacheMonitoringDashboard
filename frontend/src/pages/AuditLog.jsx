import { useEffect, useState, useCallback } from "react";
import client from "../api/client";
import { toProblem } from "../api/problemDetails";
import ProblemError from "../components/ProblemError";
import { formatDateTime } from "../utils/format";
import "./AuditLog.css";

const PAGE_SIZE = 50;

const ACTION_TYPES = [
  "LOGIN_SUCCESS",
  "LOGIN_FAILED",
  "LOGOUT",
  "USER_CREATED",
  "USER_DELETED",
  "INSTANCE_CREATED",
  "INSTANCE_UPDATED",
  "INSTANCE_DELETED",
  "KEY_SEARCH",
  "KEY_TOP_N",
  "KEY_TTL_QUERY",
  "METRICS_COLLECTION_FAILED",
  "ALERT_FIRING",
  "ALERT_RESOLVED",
];

export default function AuditLog() {
  const [logs, setLogs] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [isFirst, setIsFirst] = useState(true);
  const [isLast, setIsLast] = useState(true);

  const [selectedAction, setSelectedAction] = useState("");
  const [usernameFilter, setUsernameFilter] = useState("");

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchLogs = useCallback(async (targetPage, action, username) => {
    setLoading(true);
    setError(null);

    try {
      const res = await client.get("/v1/audit", {
        params: {
          page: targetPage,
          size: PAGE_SIZE,
          ...(action ? { action } : {}),
          ...(username ? { username } : {}),
        },
      });
      const data = res.data;
      setLogs(data.content ?? []);
      setTotalPages(data.totalPages ?? 0);
      setTotalElements(data.totalElements ?? 0);
      setIsFirst(data.first ?? true);
      setIsLast(data.last ?? true);
      setPage(data.number ?? targetPage);
    } catch (err) {
      if (err.response?.status === 403) {
        setError({ message: "Bu sayfaya erişim yetkiniz yok.", traceId: err.problem?.traceId ?? null });
      } else {
        setError(toProblem(err, "Audit kayıtları alınamadı."));
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- ilk veri çekimi için kasıtlı
    fetchLogs(0, "", "");
  }, [fetchLogs]);

  function handleActionChange(e) {
    const action = e.target.value;
    setSelectedAction(action);
    fetchLogs(0, action, usernameFilter);
  }

  function handleUsernameSubmit(e) {
    e.preventDefault();
    fetchLogs(0, selectedAction, usernameFilter);
  }

  function goToPage(targetPage) {
    if (targetPage < 0 || targetPage >= totalPages) return;
    fetchLogs(targetPage, selectedAction, usernameFilter);
  }

  return (
    <div className="audit-log-page">
      <h1 className="audit-log-title">Denetim Kayıtları (Audit Log)</h1>

      <div className="audit-log-note">
        Metrik görüntüleme işlemleri denetim kaydı üretmez.
      </div>

      <div className="audit-log-filter">
        <div className="audit-log-filter-field">
          <label className="audit-log-filter-label" htmlFor="audit-action-filter">
            Aksiyon
          </label>
          <select
            id="audit-action-filter"
            className="audit-log-filter-select"
            value={selectedAction}
            onChange={handleActionChange}
          >
            <option value="">Tümü</option>
            {ACTION_TYPES.map((action) => (
              <option key={action} value={action}>
                {action}
              </option>
            ))}
          </select>
        </div>

        <form className="audit-log-filter-field" onSubmit={handleUsernameSubmit}>
          <label className="audit-log-filter-label" htmlFor="audit-username-filter">
            Kullanıcı Adı
          </label>
          <input
            id="audit-username-filter"
            type="text"
            className="audit-log-filter-input"
            value={usernameFilter}
            onChange={(e) => setUsernameFilter(e.target.value)}
            placeholder="kullanıcı adı"
          />
          <button type="submit" className="audit-log-filter-submit">
            Filtrele
          </button>
        </form>
      </div>

      {loading && <div className="audit-log-status">Yükleniyor...</div>}
      {error && <ProblemError error={error} className="audit-log-error" />}

      {!loading && !error && (
        <>
          <table className="audit-log-table">
            <thead>
              <tr>
                <th>Zaman</th>
                <th>Kullanıcı</th>
                <th>Aksiyon</th>
                <th>Detay</th>
                <th>IP Adresi</th>
                <th>Sonuç</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((log) => (
                <tr key={log.id}>
                  <td>{formatDateTime(log.timestamp)}</td>
                  <td>{log.username}</td>
                  <td>{log.action}</td>
                  <td>{log.detail}</td>
                  <td>{log.ipAddress}</td>
                  <td>
                    <span className={`audit-log-result audit-log-result-${log.result?.toLowerCase()}`}>
                      {log.result}
                    </span>
                  </td>
                </tr>
              ))}
              {logs.length === 0 && (
                <tr>
                  <td colSpan={6} className="audit-log-empty">
                    Henüz audit kaydı yok.
                  </td>
                </tr>
              )}
            </tbody>
          </table>

          <div className="audit-log-pagination">
            <button
              type="button"
              className="audit-log-page-button"
              onClick={() => goToPage(page - 1)}
              disabled={isFirst}
            >
              ← Önceki
            </button>
            <span className="audit-log-page-indicator">
              Sayfa {totalPages === 0 ? 0 : page + 1} / {totalPages} · {totalElements} kayıt
            </span>
            <button
              type="button"
              className="audit-log-page-button"
              onClick={() => goToPage(page + 1)}
              disabled={isLast}
            >
              Sonraki →
            </button>
          </div>
        </>
      )}
    </div>
  );
}
