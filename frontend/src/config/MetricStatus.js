export const MetricStatus = {
  AVAILABLE: "AVAILABLE",
  UNAVAILABLE: "UNAVAILABLE",
  NOT_APPLICABLE: "NOT_APPLICABLE",
  ESTIMATED: "ESTIMATED",
  STALE: "STALE",
  NOT_READY: "NOT_READY",
};

export function interpretStatus(status) {
  switch (status) {
    case MetricStatus.NOT_APPLICABLE:
      return { hidden: true, placeholder: null, badge: "", dim: false, note: "", usable: false };
    case MetricStatus.UNAVAILABLE:
      return { hidden: false, placeholder: "—", badge: "", dim: false, note: "veri yok", usable: false };
    case MetricStatus.NOT_READY:
      return { hidden: false, placeholder: "—", badge: "", dim: false, note: "hesaplanıyor", usable: false };
    case MetricStatus.ESTIMATED:
      return { hidden: false, placeholder: null, badge: "~", dim: false, note: "yaklaşık", usable: true };
    case MetricStatus.STALE:
      return { hidden: false, placeholder: null, badge: "", dim: true, note: "eski veri", usable: true };
    case MetricStatus.AVAILABLE:
      return { hidden: false, placeholder: null, badge: "", dim: false, note: "", usable: true };
    default:
      return { hidden: false, placeholder: null, badge: "", dim: false, note: "", usable: true };
  }
}