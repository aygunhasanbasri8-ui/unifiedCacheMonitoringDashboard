import { useCallback, useEffect, useMemo, useState } from "react";
import client from "../api/client";
import { toProblem } from "../api/problemDetails";
import { extractAlertEvents } from "../utils/alertEvents";

// Uyarı geçmişi snapshot retention sınırına (7 gün) kadar, ayrı ve düşük öncelikli
// bir çağrıyla hesaplanır: grafiklerin seçilebilir aralığından bağımsızdır ve
// 15 sn'lik polling'e dahil edilmez.
const ALERT_HISTORY_LAST_MINUTES = 10080;
const ALERT_HISTORY_MAX_POINTS = 2000;

export function useAlertHistory(instanceId, enabled, maxMemoryBytes) {
  const [points, setPoints] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await client.post(`/v1/instances/${instanceId}/history`, {
        lastMinutes: ALERT_HISTORY_LAST_MINUTES,
        maxPoints: ALERT_HISTORY_MAX_POINTS,
      });
      setPoints(res.data?.points ?? []);
    } catch (err) {
      setError(toProblem(err, "Uyarı geçmişi alınamadı."));
    } finally {
      setLoading(false);
    }
  }, [instanceId]);

  // Sayfa açılışında bir kez çekilir
  useEffect(() => {
    if (!enabled) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- ilk veri çekimi için kasıtlı
    reload();
  }, [enabled, reload]);

  const events = useMemo(
    () => extractAlertEvents(points, { maxMemory: maxMemoryBytes }),
    [points, maxMemoryBytes]
  );

  return {
    activeAlertEvents: events.filter((ev) => !ev.resolved),
    pastAlertEvents: events.filter((ev) => ev.resolved),
    loading,
    error,
    reload,
  };
}
