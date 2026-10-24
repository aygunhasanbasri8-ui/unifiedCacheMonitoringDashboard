import { useEffect, useState } from "react";
import client from "../api/client";
import MetricCard from "./MetricCard";
import { formatBytes } from "../utils/format";

// Tahmin son 24 saatlik eğilimden geliyor; dakikalık yenilemenin anlamı yok
const REFRESH_MS = 10 * 60 * 1000;

function describe(forecast) {
  if (!forecast) return { value: "-", subtitle: "" };
  if (forecast.status === "NOT_APPLICABLE") return { value: "Sınırsız", subtitle: "Bellek sınırı tanımlı değil" };
  if (forecast.status !== "AVAILABLE") {
    return { value: "-", subtitle: `Eğilim için veri birikiyor (${forecast.pointCount} ölçüm)` };
  }
  const perDay = `${forecast.growthBytesPerDay >= 0 ? "+" : "-"}${formatBytes(Math.abs(forecast.growthBytesPerDay))}/gün`;
  if (forecast.trend !== "GROWING") {
    return { value: forecast.trend === "STABLE" ? "Sabit" : "Azalıyor", subtitle: `${perDay} · son ${forecast.windowHours} sa` };
  }
  const days = forecast.daysToFull;
  const value = days < 1 ? "1 günden az" : `${Math.round(days)} gün`;
  const fit = forecast.rSquared < 0.5 ? " · dalgalı, tahmin kaba" : "";
  return { value, subtitle: `${perDay}${fit}` };
}

function statusFor(forecast) {
  const days = forecast?.daysToFull;
  if (days == null) return "ok";
  if (days < 3) return "critical";
  if (days < 14) return "warning";
  return "ok";
}

export default function CapacityForecastCard({ instanceId }) {
  const [forecast, setForecast] = useState(null);

  useEffect(() => {
    let cancelled = false;
    const load = () =>
      client
        .post(`/v1/instances/${instanceId}/capacity-forecast`, {})
        .then((res) => !cancelled && setForecast(res.data))
        .catch(() => !cancelled && setForecast(null));
    load();
    const timer = setInterval(load, REFRESH_MS);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [instanceId]);

  const { value, subtitle } = describe(forecast);
  return <MetricCard title="Belleğin Dolmasına" value={value} subtitle={subtitle} status={statusFor(forecast)} />;
}
