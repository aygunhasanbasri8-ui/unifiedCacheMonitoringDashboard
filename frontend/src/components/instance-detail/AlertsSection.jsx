import { useState } from "react";
import ProblemError from "../ProblemError";
import { formatUptime, formatTime, formatDurationShort, formatShortDateTime } from "../../utils/format";

// Uyarı başlangıcından bu yana geçen süreyi "2 sa 14 dk" biçiminde döner.
function elapsedSince(isoString) {
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(isoString).getTime()) / 1000));
  return formatUptime(seconds);
}

// Çözülmüş bir uyarı olayı için "DD.MM HH:mm → HH:mm" (aynı gün) ya da
// "DD.MM HH:mm → DD.MM HH:mm" (farklı gün) aralığını döner.
function alertEventRange(startedAt, endedAt) {
  if (!endedAt) return formatShortDateTime(startedAt);
  const sameDay = new Date(startedAt).toDateString() === new Date(endedAt).toDateString();
  const endText = sameDay ? formatTime(endedAt).slice(0, 5) : formatShortDateTime(endedAt);
  return `${formatShortDateTime(startedAt)} → ${endText}`;
}

export default function AlertsSection({ activeAlertEvents, pastAlertEvents, loading, error, onReload }) {
  const [alertHistoryOpen, setAlertHistoryOpen] = useState(false);

  return (
    <div className="instance-detail-section">
      <h2 className="instance-detail-section-title">
        ⚠ Aktif Uyarılar{activeAlertEvents.length > 0 ? ` (${activeAlertEvents.length})` : ""}
      </h2>

      {loading && (
        <div className="instance-detail-cluster-note">Uyarı geçmişi hesaplanıyor...</div>
      )}

      {!loading && error && (
        <>
          <ProblemError error={error} className="instance-detail-error" />
          <button type="button" className="instance-detail-reconnect-button" onClick={onReload}>
            Tekrar dene
          </button>
        </>
      )}

      {!loading && !error && (
        <>
          {activeAlertEvents.length === 0 ? (
            <div className="instance-detail-alert-clear">✓ Aktif uyarı yok</div>
          ) : (
            <ul className="instance-detail-alert-list">
              {activeAlertEvents.map((ev) => (
                <li key={ev.ruleId} className="instance-detail-alert-item">
                  <span className={`instance-detail-alert-dot instance-detail-alert-dot-${ev.severity}`}>●</span>
                  <span className="instance-detail-alert-label">{ev.label}</span>
                  <span className="instance-detail-alert-time">{elapsedSince(ev.startedAt)} önce başladı</span>
                </li>
              ))}
            </ul>
          )}

          {pastAlertEvents.length > 0 && (
            <div className="instance-detail-alert-history">
              <button
                type="button"
                className="instance-detail-alert-history-toggle"
                onClick={() => setAlertHistoryOpen((open) => !open)}
              >
                {alertHistoryOpen ? "▾" : "▸"} Geçmiş uyarılar ({pastAlertEvents.length})
              </button>

              {alertHistoryOpen && (
                <ul className="instance-detail-alert-list instance-detail-alert-list-past">
                  {pastAlertEvents.map((ev, i) => (
                    <li key={`${ev.ruleId}-${ev.startedAt}-${i}`} className="instance-detail-alert-item">
                      <span className="instance-detail-alert-dot instance-detail-alert-dot-past">○</span>
                      <span className="instance-detail-alert-label instance-detail-alert-label-past">
                        {ev.label}
                      </span>
                      <span className="instance-detail-alert-time">
                        {alertEventRange(ev.startedAt, ev.endedAt)} ·{" "}
                        {formatDurationShort(new Date(ev.endedAt) - new Date(ev.startedAt))}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}

          <div className="instance-detail-alert-footnote">
            Son 7 günün verisinden hesaplanmıştır; kalıcı kayıt tutulmaz, eşik değişirse geçmiş yeniden hesaplanır.
            <button type="button" className="instance-detail-alert-refresh" onClick={onReload}>
              ⟳ Yenile
            </button>
          </div>
        </>
      )}
    </div>
  );
}
