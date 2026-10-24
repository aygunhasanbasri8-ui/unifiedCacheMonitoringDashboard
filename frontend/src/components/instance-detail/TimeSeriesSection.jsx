import { ResponsiveContainer, LineChart, Line, CartesianGrid, XAxis, YAxis, Tooltip } from "recharts";
import { formatTime } from "../../utils/format";

function TimeSeriesChart({ title, data, dataKey, color, valueFormatter, seriesName }) {
  const tickInterval = Math.max(0, Math.ceil(data.length / 6) - 1);

  return (
    <div className="instance-detail-chart-card">
      <div className="instance-detail-chart-title">{title}</div>
      <ResponsiveContainer width="100%" height={220}>
        <LineChart data={data} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
          <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
          <XAxis
            dataKey="timestamp"
            tickFormatter={formatTime}
            interval={tickInterval}
            stroke="var(--text-dim)"
            fontSize={11}
          />
          <YAxis stroke="var(--text-dim)" fontSize={11} width={48} />
          <Tooltip
            contentStyle={{
              background: "var(--bg-card)",
              border: "1px solid var(--border)",
              borderRadius: 8,
              color: "var(--text)",
            }}
            labelFormatter={formatTime}
            // Seri değeri null olabilir (ör. hitRatio hiç sorgu yapılmamışsa) —
            // formatter'a null geçmek toFixed çağrısında crash'e yol açar.
            formatter={(value) => [
              value == null ? "-" : valueFormatter ? valueFormatter(value) : value,
              seriesName,
            ]}
          />
          <Line
            type="monotone"
            dataKey={dataKey}
            name={seriesName}
            stroke={color}
            strokeWidth={2}
            dot={data.length <= 30}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

export default function TimeSeriesSection({ history, ranges, rangeMinutes, onRangeChange }) {
  return (
    <div className="instance-detail-timeseries">
      <div className="instance-detail-timeseries-header">
        <h2 className="instance-detail-section-title">
          Zaman Serisi
          {history.resolution === "DOWNSAMPLED" && (
            <span className="instance-detail-timeseries-resolution-note">
              {" "}
              · Örneklenmiş veri ({history.pointCount} nokta)
            </span>
          )}
        </h2>
        <div className="instance-detail-range-toggle">
          {ranges.map((range) => (
            <button
              key={range.minutes}
              type="button"
              className={`instance-detail-range-button ${
                rangeMinutes === range.minutes ? "active" : ""
              }`}
              onClick={() => onRangeChange(range.minutes)}
            >
              {range.label}
            </button>
          ))}
        </div>
      </div>

      {history.points.length === 0 ? (
        <div className="instance-detail-timeseries-empty">
          Henüz yeterli veri toplanmadı. Birkaç dakika bekleyin.
        </div>
      ) : (
        <div className="instance-detail-chart-grid">
          <TimeSeriesChart
            title="CPU Kullanımı (%)"
            data={history.points}
            dataKey="cpuPercent"
            color="var(--accent)"
            seriesName="CPU"
            valueFormatter={(value) => `${value.toFixed(1)}%`}
          />
          <TimeSeriesChart
            title="Bellek Kullanımı (MB)"
            data={history.points.map((s) => ({
              ...s,
              memoryUsedMB: s.memoryUsed != null ? s.memoryUsed / (1024 * 1024) : null,
            }))}
            dataKey="memoryUsedMB"
            color="var(--accent)"
            seriesName="Bellek"
            valueFormatter={(value) => `${value.toFixed(1)} MB`}
          />
          <TimeSeriesChart
            title="Key Sayısı"
            data={history.points}
            dataKey="keyCount"
            color="var(--green)"
            seriesName="Key Sayısı"
          />
          <TimeSeriesChart
            title="Aktif Bağlantılar"
            data={history.points}
            dataKey="clientCount"
            color="var(--yellow)"
            seriesName="Bağlantı"
          />
          <TimeSeriesChart
            title="Hit Ratio (%)"
            data={history.points.map((s) => ({
              ...s,
              hitRatioPercent: s.hitRatio != null ? s.hitRatio * 100 : null,
            }))}
            dataKey="hitRatioPercent"
            color="var(--green)"
            seriesName="Hit Ratio"
            valueFormatter={(value) => `${value.toFixed(1)}%`}
          />
          <TimeSeriesChart
            title="Ops/sec"
            data={history.points}
            dataKey="opsPerSec"
            color="var(--yellow)"
            seriesName="Ops/sec"
          />
          <TimeSeriesChart
            title="Evicted Keys"
            data={history.points}
            dataKey="evictedKeys"
            color="var(--red)"
            seriesName="Evicted Keys"
          />
        </div>
      )}
    </div>
  );
}
