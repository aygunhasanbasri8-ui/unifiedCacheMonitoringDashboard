import { PieChart, Pie, Cell, ResponsiveContainer } from "recharts";
import { statusColor } from "../config/thresholds";
import { interpretStatus } from "../config/metricStatus";
import { formatPercent } from "../utils/format";
import "./DonutMetric.css";

export default function DonutMetric({ title, percent, status = "ok", subtitle, size = 130, metricStatus }) {
  const si = interpretStatus(metricStatus);

  if (si.hidden) return null;

  const forcedEmpty = si.placeholder != null;
  const hasValue =
    !forcedEmpty &&
    percent !== null &&
    percent !== undefined &&
    !Number.isNaN(percent) &&
    percent !== 0;
  const clamped = hasValue ? Math.min(100, Math.max(0, percent)) : 0;
  const effectiveStatus = si.usable ? status : "ok";
  const color = statusColor(effectiveStatus);
  const data = [
    { key: "value", value: clamped },
    { key: "rest", value: 100 - clamped },
  ];

  const effectiveSubtitle = si.note || subtitle;

  return (
    <div className="donut-metric" style={si.dim ? { opacity: 0.6 } : undefined}>
      <div className="donut-metric-title">{title}</div>
      {!hasValue ? (
        <div className="donut-metric-empty" style={{ width: size, height: size }}>
          {si.placeholder != null ? si.placeholder : "—"}
        </div>
      ) : (
        <div className="donut-metric-chart" style={{ width: size, height: size }}>
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={data}
                dataKey="value"
                nameKey="key"
                innerRadius="72%"
                outerRadius="100%"
                startAngle={90}
                endAngle={-270}
                stroke="none"
                isAnimationActive={false}
              >
                <Cell fill={color} />
                <Cell fill="var(--border)" />
              </Pie>
            </PieChart>
          </ResponsiveContainer>
          <div className="donut-metric-center">
            {formatPercent(percent)}
            {si.badge && <span title="yaklaşık değer"> {si.badge}</span>}
          </div>
        </div>
      )}
      {effectiveSubtitle && <div className="donut-metric-subtitle">{effectiveSubtitle}</div>}
    </div>
  );
}