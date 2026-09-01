// RFC 7807 (application/problem+json) hata gövdelerini yorumlayıp kullanıcıya
// gösterilecek Türkçe mesaja çevirir.

const MESSAGE_BY_TYPE = {
  "instance-not-found": () => "Instance bulunamadı",
  "instance-unreachable": () => "Instance'a erişilemiyor",
  "invalid-prefix": (detail) => detail,
  "bad-request": (detail) => detail,
  "malformed-body": () => "Geçersiz istek",
  "type-mismatch": () => "Geçersiz parametre",
  "missing-parameter": (detail) => detail,
  "method-not-allowed": () => "Desteklenmeyen işlem",
  "unsupported-media-type": () => "Desteklenmeyen içerik tipi",
  "endpoint-not-found": () => "Uç nokta bulunamadı",
  "rate-limit-exceeded": () => "Sorgu limiti aşıldı, lütfen biraz bekleyin",
  "instance-busy": () => "Instance meşgul, tekrar deneyin",
  "circuit-open": () => "Instance geçici olarak devre dışı",
  "monitor-timeout": () => "Sorgu zaman aşımına uğradı",
  "internal-error": () => "Beklenmeyen hata",
  "map-not-allowed": () => "Bu map'e erişim izni yok",
};

// Otomatik yenilenen (polling) bileşenlerde bu tipler alındığında polling durdurulmaz,
// bir sonraki turda tekrar denenir; kullanıcıya sadece uyarı rozeti gösterilir.
export const RETRYABLE_PROBLEM_TYPES = new Set([
  "rate-limit-exceeded",
  "instance-busy",
  "circuit-open",
  "monitor-timeout",
]);

function lastTypeSegment(type) {
  if (!type || typeof type !== "string") return null;
  const parts = type.split("/").filter(Boolean);
  return parts.length > 0 ? parts[parts.length - 1] : null;
}

// axios error -> { message, detail, traceId, status, typeSegment, retryable }
export function parseProblem(error) {
  const data = error?.response?.data;
  const looksLikeProblem = data && typeof data === "object" && (data.type || data.title);

  if (!looksLikeProblem) {
    return {
      message: null,
      detail: null,
      traceId: null,
      status: error?.response?.status ?? null,
      typeSegment: null,
      retryable: false,
    };
  }

  const segment = lastTypeSegment(data.type);
  const resolver = segment ? MESSAGE_BY_TYPE[segment] : null;
  const message = resolver ? resolver(data.detail) : data.detail || data.title || "Beklenmeyen hata";

  return {
    message,
    detail: data.detail ?? null,
    traceId: data.traceId ?? null,
    status: data.status ?? error?.response?.status ?? null,
    typeSegment: segment,
    retryable: segment ? RETRYABLE_PROBLEM_TYPES.has(segment) : false,
  };
}

// Yakalanan bir axios hatasını sayfa state'inde tutulacak { message, traceId } şekline çevirir.
export function toProblem(err, fallbackMessage) {
  const problem = err?.problem;
  return {
    message: problem?.message ?? fallbackMessage,
    traceId: problem?.traceId ?? null,
  };
}
