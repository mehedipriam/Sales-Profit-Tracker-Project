import { money } from '../../utils/format'
import useTip from '../../hooks/useTip'
import { TipBody, TipLayer } from './ChartTip'
import { platformColorMap, textOn } from './tokens'

const MIN_LABEL_PCT = 14 // only print a % inside a segment when it comfortably fits

function StackedBar({ title, total, segments, colors, handlers }) {
  const sum = segments.reduce((s, r) => s + r.value, 0)
  return (
    <div className="stack-row">
      <div className="stack-label">
        <span>{title}</span>
        <span className="muted">{money(total)}</span>
      </div>
      <div className="stack-bar">
        {segments.map((s) => {
          const pct = (s.value / sum) * 100
          const color = colors.get(s.platformId)
          const content = (
            <TipBody
              title={s.name}
              rows={[
                { color, label: title, value: money(s.value) },
                { color, label: `Share of ${title.toLowerCase()}`, value: `${pct.toFixed(1)}%` },
              ]}
            />
          )
          return (
            <div
              key={s.platformId}
              className="seg"
              style={{ flexGrow: s.value, background: color, color: textOn(color) }}
              tabIndex={0}
              role="img"
              aria-label={`${s.name}: ${pct.toFixed(1)}% of ${title.toLowerCase()}, ${money(s.value)}`}
              {...handlers(content)}
            >
              {pct >= MIN_LABEL_PCT ? `${pct.toFixed(0)}%` : ''}
            </div>
          )
        })}
      </div>
    </div>
  )
}

/** Each platform's share of paid revenue and of paid profit. Loss-making platforms have no positive share. */
export default function ShareBars({ rows, platforms, revenueTotal, profitTotal }) {
  const { tip, handlers } = useTip()
  const colors = platformColorMap(platforms)

  const revenue = rows.filter((r) => Number(r.revenue) > 0)
    .map((r) => ({ platformId: r.platformId, name: r.platformName, value: Number(r.revenue) }))
  const profit = rows.filter((r) => Number(r.profit) > 0)
    .map((r) => ({ platformId: r.platformId, name: r.platformName, value: Number(r.profit) }))
  const losers = rows.filter((r) => Number(r.profit) < 0)

  return (
    <div className="viz-card">
      <div className="viz-head">
        <div>
          <h2 className="viz-title">Share by platform</h2>
          <p className="muted viz-sub">Paid orders · each platform's part of the whole</p>
        </div>
      </div>

      {revenue.length === 0 ? (
        <p className="empty-note">No paid orders in this period.</p>
      ) : (
        <>
          <ul className="legend" aria-label="Legend">
            {revenue.map((r) => (
              <li key={r.platformId}><span className="key-box" style={{ background: colors.get(r.platformId) }} />{r.name}</li>
            ))}
          </ul>
          <StackedBar title="Revenue" total={revenueTotal} segments={revenue} colors={colors} handlers={handlers} />
          {profit.length > 0 ? (
            <StackedBar title="Profit" total={profitTotal} segments={profit} colors={colors} handlers={handlers} />
          ) : (
            <p className="muted">No platform made a profit in this period.</p>
          )}
          {losers.length > 0 && (
            <p className="muted note">
              Not in the profit bar (running at a loss):{' '}
              {losers.map((r) => `${r.platformName} ${money(r.profit)}`).join(', ')}
            </p>
          )}
        </>
      )}
      <TipLayer tip={tip} />
    </div>
  )
}
