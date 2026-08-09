import { THRESHOLDS } from "../config/thresholds";

// Ardışık iki ölçüm arasında bir olayı "çözülmüş" saymak için asgari süre.
// Snapshot aralığı 30 sn — tek bir dalgalanma olay üretmemeli.
const MIN_DURATION_MS = 60_000;

/**
 * Bir snapshot noktasında aktif olan uyarı kurallarını döner.
 * @returns Map<ruleId, { label, severity }>
 */
export function evaluatePoint(point, prevPoint, context = {}) {
  const active = new Map();

  if (point.status !== "UP") {
    active.set("instance-down", { label: "Instance erişilemez", severity: "critical" });
  }

  const maxMemory = context.maxMemory;
  if (maxMemory != null && maxMemory > 0 && point.memoryUsed != null) {
    const ratio = point.memoryUsed / maxMemory;
    const t = THRESHOLDS.alertMemoryUsageRatio;
    if (ratio > t.warning) {
      active.set("memory-high", {
        label: `Bellek kullanımı %${Math.round(ratio * 100)}`,
        severity: ratio > t.critical ? "critical" : "warning",
      });
    }
  }

  if (point.cpuPercent != null) {
    const t = THRESHOLDS.cpuUsagePercent;
    if (point.cpuPercent > t.warning) {
      active.set("cpu-high", {
        label: `CPU %${Math.round(point.cpuPercent)}`,
        severity: point.cpuPercent > t.critical ? "critical" : "warning",
      });
    }
  }

  if (point.hitRatio != null) {
    const t = THRESHOLDS.alertHitRatioLow;
    if (point.hitRatio < t.warning) {
      active.set("hit-ratio-low", {
        label: `İsabet oranı %${Math.round(point.hitRatio * 100)}`,
        severity: "warning",
      });
    }
  }

  if (prevPoint != null && point.evictedKeys != null && prevPoint.evictedKeys != null) {
    if (point.evictedKeys > prevPoint.evictedKeys) {
      active.set("eviction-active", { label: "Eviction aktif", severity: "warning" });
    }
  }

  return active;
}

/**
 * Ardışık eşik aşımlarını tek olaya birleştirir.
 * @returns Array<{ ruleId, label, severity, startedAt, endedAt, resolved }>
 */
export function extractAlertEvents(points, context = {}) {
  const events = [];
  const open = new Map();
  let prev = null;

  for (const p of points) {
    const active = evaluatePoint(p, prev, context);

    for (const [ruleId, info] of active) {
      if (!open.has(ruleId)) {
        open.set(ruleId, { ruleId, ...info, startedAt: p.timestamp });
      }
    }
    for (const [ruleId, ev] of [...open]) {
      if (!active.has(ruleId)) {
        events.push({ ...ev, endedAt: p.timestamp, resolved: true });
        open.delete(ruleId);
      }
    }
    prev = p;
  }

  for (const ev of open.values()) {
    events.push({ ...ev, endedAt: null, resolved: false });
  }

  // Gürültü filtresi: 60 sn'den kısa süren çözülmüş olaylar listelenmez.
  // Aktif (çözülmemiş) olaylar bu filtreye tabi değildir.
  const filtered = events.filter(
    (ev) => !ev.resolved || new Date(ev.endedAt) - new Date(ev.startedAt) >= MIN_DURATION_MS
  );

  return filtered.sort((a, b) => new Date(b.startedAt) - new Date(a.startedAt));
}
