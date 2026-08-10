export function formatBytes(bytes) {
  if (bytes === null || bytes === undefined || Number.isNaN(bytes)) return "-";
  if (bytes === 0) return "0 B";

  const units = ["B", "KB", "MB", "GB", "TB"];
  const exponent = Math.min(
    Math.floor(Math.log(Math.abs(bytes)) / Math.log(1024)),
    units.length - 1
  );
  const value = bytes / 1024 ** exponent;

  return `${value.toFixed(exponent === 0 ? 0 : 2)} ${units[exponent]}`;
}

export function formatUptime(seconds) {
  if (seconds === null || seconds === undefined || Number.isNaN(seconds)) return "-";

  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);

  const parts = [];
  if (days > 0) parts.push(`${days} gün`);
  if (hours > 0) parts.push(`${hours} sa`);
  if (minutes > 0 || parts.length === 0) parts.push(`${minutes} dk`);

  return parts.join(" ");
}

export function formatPercent(value, digits = 1) {
  if (value === null || value === undefined || Number.isNaN(value)) return "-";
  return `%${value.toFixed(digits)}`;
}

// ttlStatus enumu: EXPIRING · PERSISTENT · NOT_FOUND · UNAVAILABLE
export function formatTtlByStatus(ttlMillis, ttlStatus) {
  switch (ttlStatus) {
    case "PERSISTENT":
      return "Süresiz";
    case "NOT_FOUND":
      return "Key bulunamadı";
    case "UNAVAILABLE":
      return "Sorgulanamadı";
    case "EXPIRING":
      return formatDurationShort(ttlMillis);
    default:
      return formatDurationShort(ttlMillis);
  }
}

// meta.ageMillis -> tam saniye (yuvarlanmış)
export function formatAgeSeconds(ageMillis) {
  if (ageMillis === null || ageMillis === undefined || Number.isNaN(ageMillis)) return null;
  return Math.max(0, Math.round(ageMillis / 1000));
}

// Milisaniye süreyi insan okunur biçime çevirir: "25 dk", "3 sa 12 dk", "45 sn"
export function formatDurationShort(millis) {
  if (millis === null || millis === undefined || Number.isNaN(millis)) return "-";

  const totalSeconds = Math.round(millis / 1000);
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  if (days > 0) return `${days} gün ${hours} sa`;
  if (hours > 0) return `${hours} sa ${minutes} dk`;
  if (minutes > 0) return `${minutes} dk`;
  return `${seconds} sn`;
}

// Saniye hassasiyetli epoch zaman damgasını (ör. rdbLastSaveTime) tarih/saate çevirir.
export function formatEpochSeconds(epochSeconds) {
  if (epochSeconds === null || epochSeconds === undefined || Number.isNaN(epochSeconds)) return "-";
  return formatDateTime(new Date(epochSeconds * 1000).toISOString());
}

// Milisaniye hassasiyetli epoch zaman damgasını (ör. Hazelcast lastAccessTime) tarih/saate çevirir.
export function formatEpochMillis(epochMillis) {
  if (epochMillis === null || epochMillis === undefined || Number.isNaN(epochMillis)) return "-";
  return formatDateTime(new Date(epochMillis).toISOString());
}

// Büyük sayıları kısaltılmış biçime çevirir: 184232 -> "184.2K"
export function formatCompactNumber(value) {
  if (value === null || value === undefined || Number.isNaN(value)) return "-";

  const abs = Math.abs(value);
  const trim = (v) => (v.endsWith(".0") ? v.slice(0, -2) : v);

  if (abs >= 1_000_000_000) return `${trim((value / 1_000_000_000).toFixed(1))}B`;
  if (abs >= 1_000_000) return `${trim((value / 1_000_000).toFixed(1))}M`;
  if (abs >= 1_000) return `${trim((value / 1_000).toFixed(1))}K`;
  return String(value);
}

export function formatTime(isoString) {
  if (!isoString) return "-";
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return "-";

  const hh = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  const ss = String(date.getSeconds()).padStart(2, "0");
  return `${hh}:${mm}:${ss}`;
}

export function formatDateTime(isoString) {
  if (!isoString) return "-";
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return "-";

  const dd = String(date.getDate()).padStart(2, "0");
  const mo = String(date.getMonth() + 1).padStart(2, "0");
  const yyyy = date.getFullYear();
  return `${dd}.${mo}.${yyyy} ${formatTime(isoString)}`;
}

// Uyarı geçmişi satırları için kısa biçim: yıl ve saniye olmadan "DD.MM HH:mm".
export function formatShortDateTime(isoString) {
  if (!isoString) return "-";
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return "-";

  const dd = String(date.getDate()).padStart(2, "0");
  const mo = String(date.getMonth() + 1).padStart(2, "0");
  return `${dd}.${mo} ${formatTime(isoString).slice(0, 5)}`;
}
