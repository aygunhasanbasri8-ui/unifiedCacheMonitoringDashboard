import { PieChart, Pie, Cell, ResponsiveContainer } from "recharts";
import { statusColor } from "../config/thresholds";
import { formatPercent } from "../utils/format";
import "./DonutMetric.css";

export default function DonutMetric({ title, percent, status = "ok", subtitle, size = 130 }) {
  const hasValue = percent !== null && percent !== undefined && !Number.isNaN(percent) && percent !== 0;
  const clamped = hasValue ? Math.min(100, Math.max(0, percent)) : 0;
  const color = statusColor(status);
  const data = [
    { key: "value", value: clamped },
    { key: "rest", value: 100 - clamped },
  ];

  return (
    <div className="donut-metric">
      <div className="donut-metric-title">{title}</div>
      {!hasValue ? (
        <div className="donut-metric-empty" style={{ width: size, height: size }}>
          —
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
          <div className="donut-metric-center">{formatPercent(percent)}</div>
        </div>
      )}
      {subtitle && <div className="donut-metric-subtitle">{subtitle}</div>}
    </div>
  );
}
