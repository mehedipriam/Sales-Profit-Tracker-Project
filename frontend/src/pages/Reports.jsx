import { useEffect, useMemo, useState } from 'react'
import api, { errorMessage } from '../api/client'
import RangePicker from '../components/RangePicker'
import Stat from '../components/Stat'
import { describeRange, resolveRange } from '../utils/dateRange'
import { money } from '../utils/format'

const pct = (part, whole) => (Number(whole) > 0 ? `${((Number(part) / Number(whole)) * 100).toFixed(1)}%` : '—')

export default function Reports() {
  const [range, setRange] = useState({ preset: 'this_month', from: '', to: '' })
  const [platformId, setPlatformId] = useState('')
  const [platforms, setPlatforms] = useState([])
  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  const resolved = useMemo(() => resolveRange(range), [range])

  useEffect(() => {
    api.get('/platforms').then((res) => setPlatforms(res.data)).catch(() => {})
  }, [])

  useEffect(() => {
    if (resolved.error) return undefined
    let live = true
    api
      .get('/reports/summary', { params: { from: resolved.from, to: resolved.to, platformId: platformId || undefined } })
      .then((res) => { if (live) { setData(res.data); setError('') } })
      .catch((err) => { if (live) setError(errorMessage(err)) })
    return () => { live = false }
  }, [resolved.from, resolved.to, resolved.error, platformId])

  const tone = (n) => (n < 0 ? 'neg' : 'pos')
  const realized = data?.realized

  return (
    <section>
      <div className="page-head"><h1>Reports</h1></div>

      <div className="filters">
        <RangePicker value={range} onChange={setRange} />
        <select aria-label="Platform" value={platformId} onChange={(e) => setPlatformId(e.target.value)}>
          <option value="">All platforms</option>
          {platforms.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
      </div>

      {resolved.error && <p className="error" role="alert">{resolved.error}</p>}
      {error && <p className="error" role="alert">{error}</p>}

      {data && !resolved.error && (
        <>
          <p className="muted period">
            {describeRange(resolved)}
            {platformId && ` · ${platforms.find((p) => String(p.id) === platformId)?.name ?? ''}`}
            {' · paid orders'}
          </p>

          <div className="stats">
            <Stat label="Revenue" value={money(realized.revenue)} hint={`${realized.orders} paid orders`} />
            <Stat label="Cost of goods" value={money(realized.cost)} />
            <Stat
              label={realized.profit < 0 ? 'Loss' : 'Profit'}
              value={money(realized.profit)}
              tone={tone(realized.profit)}
              hint={`${pct(realized.profit, realized.revenue)} margin`}
            />
          </div>

          <div className="stats secondary-stats">
            <Stat
              label="Pending (expected)"
              value={money(data.pending.profit)}
              tone={tone(data.pending.profit)}
              hint={`${data.pending.orders} unpaid orders · ${money(data.pending.revenue)} revenue`}
            />
            <Stat label="Returned / refunded" value={data.returnedOrders} hint="excluded from totals" />
            <Stat label="Cancelled" value={data.cancelledOrders} hint="excluded from totals" />
          </div>

          <h2 className="section-title">By platform <span className="muted">(paid orders)</span></h2>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Platform</th><th className="num">Orders</th><th className="num">Revenue</th>
                  <th className="num">Cost</th><th className="num">Profit</th>
                  <th className="num">Margin</th><th className="num">Revenue share</th>
                </tr>
              </thead>
              <tbody>
                {data.byPlatform.map((r) => (
                  <tr key={r.platformId}>
                    <td>{r.platformName}</td>
                    <td className="num">{r.orders}</td>
                    <td className="num">{money(r.revenue)}</td>
                    <td className="num">{money(r.cost)}</td>
                    <td className={`num ${tone(r.profit)}`}>{money(r.profit)}</td>
                    <td className="num">{pct(r.profit, r.revenue)}</td>
                    <td className="num">{pct(r.revenue, realized.revenue)}</td>
                  </tr>
                ))}
                {data.byPlatform.length === 0 && (
                  <tr><td colSpan={7} className="empty">No paid orders in this period.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  )
}
