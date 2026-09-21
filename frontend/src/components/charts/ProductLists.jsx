import { money } from '../../utils/format'
import useTip from '../../hooks/useTip'
import { TipBody, TipLayer } from './ChartTip'
import { LOSS, MUTED, REVENUE } from './tokens'

const pct = (n) => `${Number(n).toFixed(1)}%`

function Card({ title, subtitle, empty, children }) {
  return (
    <div className="viz-card">
      <div className="viz-head">
        <div>
          <h2 className="viz-title">{title}</h2>
          <p className="muted viz-sub">{subtitle}</p>
        </div>
      </div>
      {empty ? <p className="empty-note">No paid orders in this period.</p> : <div className="table-wrap">{children}</div>}
    </div>
  )
}

/** Top sellers by units. The bar is the units column, so the table is its own text alternative. */
export function BestSellers({ rows }) {
  const { tip, handlers } = useTip()
  const max = Math.max(1, ...rows.map((r) => r.quantity))
  return (
    <Card title="Top 10 best sellers" subtitle="By units sold · paid orders" empty={rows.length === 0}>
      <table className="viz-table">
        <thead>
          <tr><th>#</th><th>Product</th><th>Units sold</th><th className="num">Revenue</th><th className="num">Profit</th></tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={r.productId}>
              <td className="rank">{i + 1}</td>
              <td className="name">{r.name}</td>
              <td>
                <div
                  className="barcell"
                  tabIndex={0}
                  {...handlers(<TipBody title={r.name} rows={[
                    { color: REVENUE, label: 'Units sold', value: r.quantity.toLocaleString() },
                    { color: MUTED, label: 'Revenue', value: money(r.revenue) },
                    { color: MUTED, label: 'Profit', value: money(r.profit) },
                  ]} />)}
                >
                  <span className="hbar"><span className="hbar-fill" style={{ width: `${(r.quantity / max) * 100}%`, background: REVENUE }} /></span>
                  <span className="hbar-value">{r.quantity.toLocaleString()}</span>
                </div>
              </td>
              <td className="num">{money(r.revenue)}</td>
              <td className={`num ${r.profit < 0 ? 'neg' : 'pos'}`}>{money(r.profit)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <TipLayer tip={tip} />
    </Card>
  )
}

/** Lowest margin first. Bars grow from a zero line: left in red for a loss, right in blue for a profit. */
export function LowestMargin({ rows }) {
  const { tip, handlers } = useTip()
  const max = Math.max(1, ...rows.filter((r) => r.marginPct != null).map((r) => Math.abs(r.marginPct)))
  return (
    <Card title="Top 10 lowest margins" subtitle="Profit ÷ revenue · loss-making first · paid orders" empty={rows.length === 0}>
      <table className="viz-table">
        <thead>
          <tr><th>#</th><th>Product</th><th>Margin</th><th className="num">Revenue</th><th className="num">Profit</th></tr>
        </thead>
        <tbody>
          {rows.map((r, i) => {
            const loss = r.marginPct == null || r.marginPct < 0
            const width = r.marginPct == null ? 100 : (Math.abs(r.marginPct) / max) * 100
            return (
              <tr key={r.productId}>
                <td className="rank">{i + 1}</td>
                <td className="name">{r.name}</td>
                <td>
                  <div
                    className="barcell"
                    tabIndex={0}
                    {...handlers(<TipBody title={r.name} rows={[
                      { color: loss ? LOSS : REVENUE, label: 'Margin', value: r.marginPct == null ? 'no revenue' : pct(r.marginPct) },
                      { color: MUTED, label: `Revenue · ${r.quantity} units`, value: money(r.revenue) },
                      { color: MUTED, label: 'Profit', value: money(r.profit) },
                    ]} />)}
                  >
                    <span className="divbar" aria-hidden="true">
                      <span className="divbar-axis" />
                      <span
                        className="divbar-fill"
                        style={loss
                          ? { right: '50%', width: `${width / 2}%`, background: LOSS }
                          : { left: '50%', width: `${width / 2}%`, background: REVENUE }}
                      />
                    </span>
                    <span className="hbar-value">{r.marginPct == null ? 'no revenue' : pct(r.marginPct)}</span>
                  </div>
                </td>
                <td className="num">{money(r.revenue)}</td>
                <td className={`num ${r.profit < 0 ? 'neg' : 'pos'}`}>{money(r.profit)}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
      <TipLayer tip={tip} />
    </Card>
  )
}
