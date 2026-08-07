export const MetricStatus = {
  AVAILABLE: "AVAILABLE",
  UNAVAILABLE: "UNAVAILABLE",
  NOT_APPLICABLE: "NOT_APPLICABLE",
  PERMISSION_DENIED: "PERMISSION_DENIED",
  TIMEOUT: "TIMEOUT",
  STALE: "STALE",
  ESTIMATED: "ESTIMATED",
  NOT_READY: "NOT_READY",
};

// status: metrik durum enum'u. ageSeconds: yalnızca STALE için "X sn önce" notunu oluşturmak amacıyla opsiyonel verilir.
export function interpretStatus(status, ageSeconds) {
  switch (status) {
    case MetricStatus.NOT_APPLICABLE:
      return {
        placeholder: "N/A",
        badge: "",
        dim: true,
        warn: false,
        note: "Bu instance türü için geçerli değil",
        usable: false,
      };
    case MetricStatus.UNAVAILABLE:
      return {
        placeholder: "—",
        badge: "",
        dim: true,
        warn: false,
        note: "Veri alınamadı",
        usable: false,
      };
    case MetricStatus.PERMISSION_DENIED:
      return {
        placeholder: "🔒",
        badge: "",
        dim: false,
        warn: false,
        note: "Yetki yok",
        usable: false,
      };
    case MetricStatus.TIMEOUT:
      return {
        placeholder: "⏱",
        badge: "",
        dim: false,
        warn: false,
        note: "Sorgu zaman aşımına uğradı, sistem yoğun olabilir",
        usable: false,
      };
    case MetricStatus.NOT_READY:
      return {
        placeholder: "Hesaplanıyor…",
        badge: "",
        dim: false,
        warn: false,
        note: "İlk ölçüm bekleniyor",
        usable: false,
      };
    case MetricStatus.ESTIMATED:
      return {
        placeholder: null,
        badge: "~",
        dim: false,
        warn: false,
        note: "Örnekleme ile hesaplandı",
        usable: true,
      };
    case MetricStatus.STALE:
      return {
        placeholder: null,
        badge: "",
        dim: false,
        warn: true,
        note: ageSeconds != null ? `${ageSeconds} sn önce · bayat veri` : "Bayat veri",
        usable: true,
      };
    case MetricStatus.AVAILABLE:
      return { placeholder: null, badge: "", dim: false, warn: false, note: "", usable: true };
    default:
      return { placeholder: null, badge: "", dim: false, warn: false, note: "", usable: true };
  }
}
