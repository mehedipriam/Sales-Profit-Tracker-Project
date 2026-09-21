import { useEffect, useMemo, useState } from 'react'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { BestSellers, LowestMargin } from '../components/charts/ProductLists'
import ExportButtons from '../components/ExportButtons'
import ShareBars from '../components/charts/ShareBars'
import TrendChart from '../components/charts/TrendChart'
import RangePicker from '../components/RangePicker'
import Stat from '../components/Stat'
import { describeRange, resolveRange } from '../utils/dateRange'
import { EXPENSE_LABEL, money } from '../utils/format'

const pct = (part, whole) => (Number(whole) > 0 ? `${((Number(part) / Number(whole)) * 100).toFixed(1)}%` : '—')

export default function Reports() {
  const { user } = useAuth()
  const [range, setRange] = useState({ preset: 'this_month', from: '', to: '' })
  const [platformId, setPlatformId] = useState('')
  const [platforms, setPlatforms] = useState([])
  const [data, setData] = useState(null) // last good { key, summary, trend, products }
  const [failure, setFailure] = useState(null) // { key, message }

  const resolved = useMemo(() => resolveRange(range), [range])
  const key = `${resolved.from ?? ''}|${resolved.to ?? ''}|${platformId}`
  // Loading is derived: the slice on screen is not yet the slice that was asked for. The previous render stays put.
  const loading = !resolved.error && data?.key !== key && failure?.key !== key
  const error = failure?.key === key ? failure.message : ''

  useEffect(() => {
    api.get('/platforms').then((res) => setPlatforms(res.data)).catch(() => {})
  }, [])

  // One filter row scopes everything below it: all three requests use the same slice.
  useEffect(() => {
    if (resolved.error) return undefined
    let live = true
    const params = { from: resolved.from, to: resolved.to, platformId: platformId || undefined }
    Promise.all([
      api.get('/reports/summary', { params }),
      api.get('/reports/trend', { params }),
      api.get('/reports/products', { params }),
    ])
      .then(([summary, trend, products]) => {
        if (live) setData({ key, summary: summary.data, trend: trend.data, products: products.data })
      })
      .catch((err) => live && setFailure({ key, message: errorMessage(err) }))
    return () => { live = false }
  }, [key, resolved.from, resolved.to, resolved.error, platformId])

  const tone = (n) => (n < 0 ? 'neg' : 'pos')
  const summary = data?.summary
  const realized = summary?.realized

  const soldRows = summary?.byPlatform.filter((r) => r.orders > 0) ?? []
  const platformName = platforms.find((p) => String(p.id) === platformId)?.name
  const periodLabel = describeRange(resolved)

  return (
    <section>
      <div className="print-only print-header">
        <h1>{user.businessName} · Sales report</h1>
        <p>{periodLabel}{platformName ? ` · ${platformName}` : ' · all platforms'} · paid orders</p>
        <p className="muted">Generated {new Date().toLocaleDateString('en-GB', { dateStyle: 'long' })}</p>
      </div>

      <div className="page-head no-print">
        <h1>Reports</h1>
        {!resolved.error && (
          <ExportButtons range={resolved} platformId={platformId} platformName={platformName}
                         printTitle={`Sales report - ${periodLabel}${platformName ? ` - ${platformName}` : ''}`} />
        )}
      </div>

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
        <div className={loading ? 'stale' : undefined} aria-busy={loading}>
          <p className="muted period no-print">
            {periodLabel}
            {platformName && ` · ${platformName}`}
            {' · paid orders, less expenses'}
          </p>

          <div className="stats">
            <Stat label="Revenue" value={money(realized.revenue)} hint={`${realized.orders} paid orders`} />
            <Stat label="Cost of goods" value={money(realized.cost)} />
            <Stat
              label={realized.profit < 0 ? 'Gross loss' : 'Gross profit'}
              value={money(realized.profit)}
              tone={tone(realized.profit)}
              hint={`revenue − cost · ${pct(realized.profit, realized.revenue)} margin`}
            />
            <Stat label="Expenses" value={money(summary.expenses.total)} hint="delivery, commission, ads…" />
            <Stat
              label={summary.netProfit < 0 ? 'Net loss' : 'Net profit'}
              value={money(summary.netProfit)}
              tone={tone(summary.netProfit)}
              hint={`gross − expenses · ${pct(summary.netProfit, realized.revenue)} margin`}
            />
          </div>

          {summary.expenses.byType.length > 0 && (
            <p className="muted expense-line">
              Expenses:{' '}
              {summary.expenses.byType.map((t) => `${EXPENSE_LABEL[t.type]} ${money(t.total)}`).join(' · ')}
              {Number(summary.expenses.unallocated) > 0 && ` · of which ${money(summary.expenses.unallocated)} is not tied to a platform`}
            </p>
          )}
          {platformId && (
            <p className="hint">
              Expenses not tied to an order (ads, overhead) belong to no platform, so they are left out while a platform is selected.
            </p>
          )}

          <div className="stats secondary-stats">
            <Stat
              label="Pending (expected)"
              value={money(summary.pending.profit)}
              tone={tone(summary.pending.profit)}
              hint={`${summary.pending.orders} unpaid orders · ${money(summary.pending.revenue)} revenue · before expenses`}
            />
            <Stat label="Returned / refunded" value={summary.returnedOrders} hint="excluded from totals" />
            <Stat label="Cancelled" value={summary.cancelledOrders} hint="excluded from totals" />
          </div>

          <TrendChart trend={data.trend} />

          <ShareBars
            rows={soldRows}
            platforms={platforms}
            revenueTotal={realized.revenue}
            profitTotal={soldRows.filter((r) => r.profit > 0).reduce((s, r) => s + Number(r.profit), 0)}
          />

          <h2 className="section-title">By platform <span className="muted">(paid orders; expenses tied to the platform's orders)</span></h2>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Platform</th><th className="num">Orders</th><th className="num">Revenue</th>
                  <th className="num">Cost</th><th className="num">Gross profit</th>
                  <th className="num">Expenses</th><th className="num">Net profit</th>
                  <th className="num">Net margin</th><th className="num">Revenue share</th>
                </tr>
              </thead>
              <tbody>
                {summary.byPlatform.map((r) => (
                  <tr key={r.platformId}>
                    <td>{r.platformName}</td>
                    <td className="num">{r.orders}</td>
                    <td className="num">{money(r.revenue)}</td>
                    <td className="num">{money(r.cost)}</td>
                    <td className={`num ${tone(r.profit)}`}>{money(r.profit)}</td>
                    <td className="num">{money(r.expenses)}</td>
                    <td className={`num ${tone(r.netProfit)}`}>{money(r.netProfit)}</td>
                    <td className="num">{pct(r.netProfit, r.revenue)}</td>
                    <td className="num">{pct(r.revenue, realized.revenue)}</td>
                  </tr>
                ))}
                {summary.byPlatform.length === 0 && (
                  <tr><td colSpan={9} className="empty">No paid orders in this period.</td></tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="viz-grid">
            <BestSellers rows={data.products.topSellers} />
            <LowestMargin rows={data.products.lowestMargin} />
          </div>
        </div>
      )}
    </section>
  )
}
