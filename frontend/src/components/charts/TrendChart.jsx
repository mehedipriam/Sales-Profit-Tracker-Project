import { useState } from 'react'
import { CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { money } from '../../utils/format'
import { BASELINE, GRID, MUTED, NET, PROFIT, REVENUE, SURFACE, TEXT_SECONDARY, compact } from './tokens'
import { TipBody } from './ChartTip'

const parse = (s) => new Date(`${s}T00:00:00`)
const shortLabel = (s, g) =>
  parse(s).toLocaleDateString('en-GB', g === 'month' ? { month: 'short', year: '2-digit' } : { day: 'numeric', month: 'short' })
const longLabel = (s, g) =>
  parse(s).toLocaleDateString('en-GB', g === 'month' ? { month: 'long', year: 'numeric' } : { dateStyle: 'medium' })

/** Direct label at the last point of a line. Text wears the secondary text color; the line carries the hue. */
function EndLabel({ x, y, index, last, text }) {
  if (index !== last) return null
  return <text x={x + 10} y={y} dy={4} fontSize={12} fill={TEXT_SECONDARY}>{text}</text>
}

function TrendTip({ active, payload, granularity }) {
  if (!active || !payload?.length) return null
  const p = payload[0].payload
  return (
    <div className="chart-tip">
      <TipBody
        title={longLabel(p.period, granularity)}
        rows={[
          { color: REVENUE, label: 'Revenue', value: money(p.revenue) },
          { color: PROFIT, label: 'Gross profit', value: money(p.profit) },
          { color: NET, label: 'Net profit', value: money(p.netProfit) },
          { color: MUTED, label: 'Cost of goods', value: money(p.cost) },
          { color: MUTED, label: `Expenses · ${p.orders} paid orders`, value: money(p.expenses) },
        ]}
      />
    </div>
  )
}

export default function TrendChart({ trend }) {
  const [view, setView] = useState('chart')
  const { granularity, points } = trend
  const empty = points.length === 0 || points.every((p) => Number(p.revenue) === 0 && Number(p.cost) === 0 && Number(p.expenses) === 0)
  const title = granularity === 'month' ? 'Revenue, gross and net profit by month' : 'Revenue, gross and net profit by day'

  // End labels are skipped when the two lines finish too close together for the labels not to collide.
  const lastPoint = points[points.length - 1]
  const values = points.flatMap((p) => [Number(p.revenue), Number(p.profit), 0])
  const spread = Math.max(...values) - Math.min(...values)
  const labelsFit = lastPoint && spread > 0 && (Number(lastPoint.revenue) - Number(lastPoint.profit)) / spread > 0.09
  const last = points.length - 1
  const showDots = points.length <= 24

  const line = (dataKey, color, text, dashed = false) => (
    <Line
      type="linear"
      dataKey={dataKey}
      stroke={color}
      strokeWidth={2}
      strokeDasharray={dashed ? '6 4' : undefined}
      strokeLinecap="round"
      strokeLinejoin="round"
      dot={showDots ? { r: 4, fill: color, stroke: SURFACE, strokeWidth: 2 } : false}
      activeDot={{ r: 5, fill: color, stroke: SURFACE, strokeWidth: 2 }}
      label={labelsFit && text ? (props) => <EndLabel {...props} last={last} text={text} /> : false}
      isAnimationActive={false}
    />
  )

  return (
    <div className="viz-card">
      <div className="viz-head">
        <div>
          <h2 className="viz-title">{title}</h2>
          <p className="muted viz-sub">Paid orders · net profit also subtracts expenses</p>
        </div>
        <div className="segmented" role="group" aria-label="Chart or table view">
          <button type="button" className={view === 'chart' ? 'on' : ''} aria-pressed={view === 'chart'} onClick={() => setView('chart')}>Chart</button>
          <button type="button" className={view === 'table' ? 'on' : ''} aria-pressed={view === 'table'} onClick={() => setView('table')}>Table</button>
        </div>
      </div>

      {empty ? (
        <p className="empty-note">No paid orders in this period.</p>
      ) : view === 'chart' ? (
        <>
          <ul className="legend" aria-label="Legend">
            <li><span className="key-line" style={{ background: REVENUE }} />Revenue</li>
            <li><span className="key-line" style={{ background: PROFIT }} />Gross profit</li>
            <li><span className="key-line dashed" style={{ color: NET }} />Net profit</li>
          </ul>
          <div role="img" aria-label={`${title}: ${points.length} ${granularity === 'month' ? 'months' : 'days'}; see the table view for every value`}>
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={points} margin={{ top: 8, right: labelsFit ? 64 : 16, bottom: 4, left: 0 }}>
                <CartesianGrid vertical={false} stroke={GRID} strokeWidth={1} />
                <XAxis
                  dataKey="period"
                  tickFormatter={(s) => shortLabel(s, granularity)}
                  tick={{ fill: MUTED, fontSize: 12 }}
                  tickLine={false}
                  axisLine={{ stroke: BASELINE }}
                  minTickGap={28}
                  tickMargin={8}
                />
                <YAxis
                  tickFormatter={(v) => compact(v)}
                  tick={{ fill: MUTED, fontSize: 12 }}
                  tickLine={false}
                  axisLine={false}
                  width={48}
                />
                <ReferenceLine y={0} stroke={BASELINE} strokeWidth={1} />
                <Tooltip content={<TrendTip granularity={granularity} />} cursor={{ stroke: BASELINE, strokeWidth: 1 }} isAnimationActive={false} />
                {line('revenue', REVENUE, 'Revenue')}
                {line('profit', PROFIT, 'Profit')}
                {line('netProfit', NET, null, true)}
              </LineChart>
            </ResponsiveContainer>
          </div>
        </>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>{granularity === 'month' ? 'Month' : 'Day'}</th><th className="num">Paid orders</th>
                <th className="num">Revenue</th><th className="num">Cost</th><th className="num">Gross profit</th>
                <th className="num">Expenses</th><th className="num">Net profit</th></tr>
            </thead>
            <tbody>
              {points.map((p) => (
                <tr key={p.period}>
                  <td>{longLabel(p.period, granularity)}</td>
                  <td className="num">{p.orders}</td>
                  <td className="num">{money(p.revenue)}</td>
                  <td className="num">{money(p.cost)}</td>
                  <td className={`num ${p.profit < 0 ? 'neg' : 'pos'}`}>{money(p.profit)}</td>
                  <td className="num">{money(p.expenses)}</td>
                  <td className={`num ${p.netProfit < 0 ? 'neg' : 'pos'}`}>{money(p.netProfit)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
