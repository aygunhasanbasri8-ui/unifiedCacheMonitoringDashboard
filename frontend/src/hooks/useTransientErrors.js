import { useCallback, useState } from "react";
import { toProblem } from "../api/problemDetails";

export const MAX_CONSECUTIVE_TRANSIENT_ERRORS = 3;

// 429/503/504 gibi geçici hatalarda polling durdurulmaz, uyarı rozeti gösterilir.
// Art arda MAX_CONSECUTIVE_TRANSIENT_ERRORS hatadan sonra polling durur ve
// kullanıcının "Yeniden bağlan" demesi beklenir; arka planda boşuna istek atılmasın.
export function useTransientErrors() {
  const [transientWarning, setTransientWarning] = useState(null);
  const [consecutiveTransientErrors, setConsecutiveTransientErrors] = useState(0);
  const [pollingStopped, setPollingStopped] = useState(false);

  const recordTransientError = useCallback((err) => {
    setTransientWarning(toProblem(err, "Geçici bir sorun oluştu, tekrar deneniyor."));
    setConsecutiveTransientErrors((prev) => {
      const next = prev + 1;
      if (next >= MAX_CONSECUTIVE_TRANSIENT_ERRORS) {
        setPollingStopped(true);
      }
      return next;
    });
  }, []);

  const clearTransientErrors = useCallback(() => {
    setTransientWarning(null);
    setConsecutiveTransientErrors(0);
  }, []);

  const reconnect = useCallback(() => {
    setConsecutiveTransientErrors(0);
    setPollingStopped(false);
    setTransientWarning(null);
  }, []);

  return {
    transientWarning,
    consecutiveTransientErrors,
    pollingStopped,
    recordTransientError,
    clearTransientErrors,
    reconnect,
  };
}
