import { useCallback, useEffect, useState } from "react";
import client from "../api/client";
import { toProblem } from "../api/problemDetails";
import ProblemError from "../components/ProblemError";
import { formatDateTime } from "../utils/format";
import "./Alerts.css";

// Alarmlar 30 sn'lik toplama turunda değerlendiriliyor; daha sık sormak boşa
const REFRESH_MS = 30000;

function formatValue(value) {
  if (value === null || value === undefined) return "-";
  return Number.isInteger(value) ? String(value) : value.toFixed(2);
}

export default function Alerts() {
  const [active, setActive] = useState([]);
  const [history, setHistory] = useState([]);
  const [rules, setRules] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      const [activeRes, historyRes, rulesRes] = await Promise.all([
        client.post("/v1/alerts/active", {}),
        client.post("/v1/alerts/history", { limit: 100 }),
        client.post("/v1/alerts/rules", {}),
      ]);
      setActive(activeRes.data ?? []);
      setHistory(historyRes.data ?? []);
      setRules(rulesRes.data ?? []);
      setError(null);
    } catch (err) {
      setError(toProblem(err, "Alarmlar alınamadı."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- ilk veri çekimi için kasıtlı
    load();
    const timer = setInterval(load, REFRESH_MS);
    return () => clearInterval(timer);
  }, [load]);

  return (
    <div className="alerts-page">
      <h1 className="alerts-title">Alarmlar</h1>

      {loading && <div className="alerts-status">Yükleniyor...</div>}
      {error && <ProblemError error={error} />}

      {!loading && !error && (
        <>
          <section className="alerts-section">
            <h2 className="alerts-subtitle">Şu an süren ({active.length})</h2>
            {active.length === 0 ? (
              <div className="alerts-empty">Süren alarm yok.</div>
            ) : (
              <div className="alerts-active">
                {active.map((a) => (
                  <div key={`${a.ruleName}-${a.instanceId}`}
                       className={`alerts-card alerts-card-${a.severity?.toLowerCase()}`}>
                    <div className="alerts-card-head">
                      <span className="alerts-severity">{a.severity}</span>
                      <span className="alerts-since">{formatDateTime(a.since)} itibarıyla</span>
                    </div>
                    <div className="alerts-card-message">{a.message}</div>
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className="alerts-section">
            <h2 className="alerts-subtitle">Geçmiş</h2>
            <table className="alerts-table">
              <thead>
                <tr>
                  <th>Zaman</th>
                  <th>Durum</th>
                  <th>Kural</th>
                  <th>Instance</th>
                  <th>Değer</th>
                  <th>Mesaj</th>
                </tr>
              </thead>
              <tbody>
                {history.map((e) => (
                  <tr key={e.id}>
                    <td>{formatDateTime(e.occurredAt)}</td>
                    <td>
                      <span className={`alerts-state alerts-state-${e.state?.toLowerCase()}`}>
                        {e.state === "FIRING" ? "Başladı" : "Düzeldi"}
                      </span>
                    </td>
                    <td>{e.ruleName}</td>
                    <td>{e.instanceName}</td>
                    <td>{formatValue(e.observedValue)}</td>
                    <td>{e.message}</td>
                  </tr>
                ))}
                {history.length === 0 && (
                  <tr>
                    <td colSpan={6} className="alerts-empty">Henüz alarm olmadı.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </section>

          <section className="alerts-section">
            <h2 className="alerts-subtitle">Kurallar</h2>
            <div className="alerts-note">
              Kurallar <code>ucmd.alerts.rules</code> altında tanımlanıyor; bildirim kanalları
              (Telegram, Slack, webhook, e-posta) ortam değişkenleriyle açılıyor.
            </div>
            <table className="alerts-table">
              <thead>
                <tr>
                  <th>Ad</th>
                  <th>Metrik</th>
                  <th>Koşul</th>
                  <th>Süre</th>
                  <th>Önem</th>
                  <th>Instance</th>
                </tr>
              </thead>
              <tbody>
                {rules.map((r) => (
                  <tr key={r.name}>
                    <td>{r.name}</td>
                    <td>{r.metric}</td>
                    <td>{r.metric === "INSTANCE_DOWN" ? "-" : `${r.operator === "LT" ? "<" : ">"} ${r.threshold}`}</td>
                    <td>{r.forMinutes ? `${r.forMinutes} dk` : "anında"}</td>
                    <td>{r.severity}</td>
                    <td>{r.instances?.length ? r.instances.join(", ") : "tümü"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        </>
      )}
    </div>
  );
}
