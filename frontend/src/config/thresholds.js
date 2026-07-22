// Metrik eşikleri: değere göre "ok" (yeşil), "warning" (sarı), "critical" (kırmızı) durumu döndürür.
// Her eşik { warning, critical, direction } ile tanımlı.
// direction: "high" = yüksek değer kötü (bellek%, fragmentasyon). "low" = düşük değer kötü (hit ratio).

export const THRESHOLDS = {
  memoryUsagePercent: { warning: 70, critical: 90, direction: "high" },
  hitRatio: { warning: 0.8, critical: 0.5, direction: "low" }, // oran (0-1)
  fragmentationRatio: { warning: 2.5, critical: 5.0, direction: "high" },
  connectionErrors: { warning: 1, critical: 10, direction: "high" },
};

// Bir değeri eşiğe göre değerlendir. "ok" | "warning" | "critical" döndür.
export function evaluateStatus(metricKey, value) {
  const t = THRESHOLDS[metricKey];
  if (!t || value == null) return "ok";
  if (t.direction === "high") {
    if (value >= t.critical) return "critical";
    if (value >= t.warning) return "warning";
    return "ok";
  } else {
    // "low" — düşük değer kötü
    if (value <= t.critical) return "critical";
    if (value <= t.warning) return "warning";
    return "ok";
  }
}

// Health status ("UP"/"DOWN") için ayrı basit yardımcı
export function healthStatus(status) {
  return status === "UP" ? "ok" : "critical";
}

// Durum → renk (index.css değişkenleri)
export function statusColor(status) {
  if (status === "critical") return "var(--red)";
  if (status === "warning") return "var(--yellow)";
  return "var(--green)";
}
