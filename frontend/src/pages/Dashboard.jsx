import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Change from '../components/Change'
import { MASK, useReveal } from '../components/Reveal'
import Stat from '../components/Stat'
import { BoxIcon, ReceiptIcon, TrendUpIcon, TruckIcon } from '../components/icons'
import { describeRange, previousRange, resolveRange, toDateStr } from '../utils/dateRange'
import { STATUS_LABEL, dateTime, money } from '../utils/format'

const TABS = [
  { value: 'this_month', label: 'This month' },
  { value: 'last_month', label: 'Last month' },
  { value: 'this_year', label: 'This year' },
  { value: 'all', label: 'All time' },
]

const greeting = () => {
  const h = new Date().getHours()
  return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening'
}

const barLabel = (s, granularity) => {
  const d = new Date(`${s}T00:00:00`)
  return granularity === 'month' ? d.toLocaleDateString('en-GB', { month: 'short' }) : String(d.getDate())
}

/** The order status split as a ring; counts only, so it needs no hiding. */
function StatusDonut({ parts }) {
  const total = parts.reduce((n, p) => n + p.count, 0)
  let at = 0
  const stops = parts.map((p) => {
    const from = at
    at += total ? (p.count / total) * 100 : 0
    return `${p.color} ${from}% ${at}%`
  })
  return (
    <div className="dash-donut-wrap">
      <div className="dash-donut" style={{ background: total ? `conic-gradient(${stops.join(', ')})` : 'var(--line-soft)' }}>
        <div><b>{total}</b><span>orders</span></div>
      </div>
      <ul className="dash-legend">
        {parts.map((p) => <li key={p.label}><i style={{ background: p.color }} />{p.label} · {p.count}</li>)}
      </ul>
    </div>
  )
}

/** Horizontal bars measured in counts (units, orders), not money, so they can stay visible while figures are hidden. */
function RankBars({ rows, empty }) {
  const max = Math.max(1, ...rows.map((r) => r.value))
  if (rows.length === 0) return <p className="muted">{empty}</p>
  return (
    <div className="dash-rank">
      {rows.map((r) => (
        <div key={r.key}>
          <span className="dash-rank-name" title={r.name}>{r.name}</span>
          <span className="dash-rank-bar"><i style={{ width: `${(r.value / max) * 100}%` }} /></span>
          <b>{r.value}</b>
        </div>
      ))}
    </div>
  )
}

export default function Dashboard() {
  const { user } = useAuth()
  const [preset, setPreset] = useState('this_month')
  const [overview, setOverview] = useState(null) // recent orders, low stock, cash with couriers: not tied to the period
  const [period, setPeriod] = useState(null) // { key, summary, previous, trend, products } for the chosen period
  const [error, setError] = useState('')
  // Guided first-run flow: platforms already exist (seeded at registration), so the one real gap for a
  // brand new workspace is having no products yet to actually sell.
  const [productCount, setProductCount] = useState(null)
  // One eye for the whole page: every money figure stays masked until it is shown. Counts are left visible.
  const { shown, eye, prompt } = useReveal()
  const show = !!shown.all

  const range = useMemo(() => resolveRange({ preset }), [preset])
  const prior = useMemo(() => previousRange({ preset }), [preset])

  useEffect(() => {
    api.get('/dashboard').then((res) => setOverview(res.data)).catch((err) => setError(errorMessage(err)))
    api.get('/products', { params: { size: 1 } }).then((res) => setProductCount(res.data.totalElements)).catch(() => {})
  }, [])

  useEffect(() => {
    let live = true
    Promise.all([
      api.get('/reports/summary', { params: range }),
      api.get('/reports/trend', { params: range }),
      api.get('/reports/products', { params: range }),
      prior && api.get('/reports/summary', { params: { from: prior.from, to: prior.to } }),
    ])
      .then(([summary, trend, products, previous]) => {
        if (!live) return
        setPeriod({
          key: preset, summary: summary.data, trend: trend.data, products: products.data,
          previous: previous && { ...previous.data, label: prior.label },
        })
      })
      .catch((err) => live && setError(errorMessage(err)))
    return () => { live = false }
  }, [preset, range, prior])

  if (error) return <p className="error" role="alert">{error}</p>
  if (!overview || !period) return <p className="center-note">Loading…</p>

  const { recentOrders, lowStock, withCouriers } = overview
  const { summary, previous, trend, products } = period
  const { realized } = summary
  const loading = period.key !== preset
  const expenses = summary.expenses.total

  // While hidden, no green or red and no "loss" wording either, since those would give the figures away.
  const tone = (n) => (!show ? 'masked' : n < 0 ? 'neg' : 'pos')
  const plain = show ? '' : 'masked' // figures that are never red or green
  const m = (n) => (show ? money(n) : MASK)
  const loss = show && summary.netProfit < 0
  const noOrders = recentOrders.length === 0
  const revenue = Number(realized.revenue)
  const margin = revenue > 0 ? (Number(summary.netProfit) / revenue) * 100 : null
  const compare = previous && { label: previous.label, period: describeRange(previous) }
  const change = (now, before, better) =>
    previous && show && <Change now={now} before={before} compare={compare} better={better} />

  // Days still to come in this month have no sales yet; leaving them out keeps the bars where the orders are.
  const today = toDateStr(new Date())
  const points = trend.points.filter((p) => p.period <= today).slice(-31)
  const peak = Math.max(1, ...points.map((p) => Number(p.revenue)))
  const topSellers = products.topSellers.slice(0, 4).map((p) => ({ key: p.productId, name: p.name, value: p.quantity }))
  const platformRows = summary.byPlatform.filter((r) => r.orders > 0).sort((a, b) => b.orders - a.orders).slice(0, 4)
    .map((r) => ({ key: r.platformId, name: r.platformName, value: r.orders }))

  return (
    <section className="dash">
      <div className="dash-head">
        <div>
          <h1>{greeting()}, {user.fullName.split(' ')[0]}</h1>
          <p className="muted">
            {user.businessName} · {new Date().toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'short', year: 'numeric' })}
          </p>
        </div>
        <div className="dash-actions">
          <span className="page-eye">{show ? 'Hide' : 'Show'} figures{eye('all', 'figures')}</span>
          <Link className="btn" to="/orders/new">+ New sale</Link>
        </div>
      </div>

      {productCount === 0 && (
        <div className="alert-card getting-started">
          <h2>Get your workspace ready</h2>
          <ol>
            <li>Platforms — Facebook Page and Daraz are already set up; <Link to="/platforms">add more or rename them</Link>.</li>
            <li><Link to="/products">Add your first product</Link> so there's something to sell.</li>
          </ol>
        </div>
      )}

      <div className="dash-tabs" role="tablist" aria-label="Period">
        {TABS.map((t) => (
          <button key={t.value} type="button" role="tab" aria-selected={preset === t.value}
                  className={preset === t.value ? 'on' : ''} onClick={() => setPreset(t.value)}>{t.label}</button>
        ))}
      </div>

      <div className={`dash-body${loading ? ' stale' : ''}`} aria-busy={loading}>
        <div className="dash-hero">
          <div className={`net-hero dash-net${loss ? ' loss' : ''}`}>
            <span className="net-hero-label">{loss ? 'Net loss' : 'Net profit'} · after expenses</span>
            <div className="dash-net-top">
              <div className="dash-net-main">
                <span className={`net-hero-value${show ? '' : ' masked'}`}>{m(summary.netProfit)}</span>
                {change(summary.netProfit, previous?.netProfit, 'up')}
              </div>
              <div className="dash-gross">
                <span>{show && realized.profit < 0 ? 'Gross loss' : 'Gross profit'}</span>
                <b>{m(realized.profit)}</b>
                <small>revenue − cost</small>
              </div>
            </div>
            {/* revenue − cost + delivery charged − expenses, as on Reports */}
            <ol className="dash-split" aria-label="How net profit is worked out">
              <li><span>Revenue</span><b>{m(realized.revenue)}</b></li>
              <li className="op" aria-hidden="true">−</li>
              <li><span>Cost of goods</span><b>{m(realized.cost)}</b></li>
              <li className="op" aria-hidden="true">+</li>
              <li><span>Delivery</span><b>{m(realized.delivery)}</b></li>
              <li className="op" aria-hidden="true">−</li>
              <li><span>Expenses</span><b>{m(expenses)}</b></li>
            </ol>
            <div className="dash-margin" aria-hidden="true">
              <i style={{ width: `${show && margin ? Math.min(100, Math.max(0, margin)) : 0}%` }} />
            </div>
            <span className="net-hero-sub">
              {margin === null ? 'No paid sales in this period' : `${show ? `${margin.toFixed(1)}%` : MASK} of revenue`}
              {' · '}{realized.orders} paid order{realized.orders === 1 ? '' : 's'}
            </span>
          </div>

          <div className="dash-card">
            <h3>Revenue <span className="muted">{describeRange(range)}</span></h3>
            {!show ? <p className="veiled-note">Hidden with the figures. Use the eye at the top to show it.</p>
              : points.every((p) => Number(p.revenue) === 0) ? <p className="muted">No paid orders in this period.</p> : (
                <>
                  <div className="dash-bars">
                    {points.map((p, i) => (
                      <i key={p.period} className={i === points.length - 1 ? 'last' : ''}
                         style={{ height: `${Math.max(3, (Number(p.revenue) / peak) * 100)}%` }}
                         title={`${barLabel(p.period, trend.granularity)}: ${money(p.revenue)}`} />
                    ))}
                  </div>
                  <div className="dash-bar-labels">
                    {points.map((p) => <span key={p.period}>{barLabel(p.period, trend.granularity)}</span>)}
                  </div>
                </>
              )}
          </div>
        </div>

        {lowStock.length > 0 && (
          <div className="dash-lowstock" role="alert">
            <span>⚠</span>
            <span>
              <b>{lowStock.length} product{lowStock.length === 1 ? '' : 's'} low on stock:</b>{' '}
              {lowStock.map((p, i) => (
                <span key={p.productId}>{i > 0 && ', '}<Link to={`/stock?productId=${p.productId}`}>{p.name}</Link> ({p.stockQty} left)</span>
              ))}
            </span>
            <Link to="/stock">Restock →</Link>
          </div>
        )}

        <div className="stats kpis">
          <Stat label="Revenue" value={m(realized.revenue)} tone={plain} hint={`${realized.orders} paid orders`}
                change={change(realized.revenue, previous?.realized.revenue, 'up')} icon={<TrendUpIcon />} accent="blue" />
          <Stat label="Cost of goods" value={m(realized.cost)} tone={plain}
                hint={revenue > 0 ? (show ? `${((Number(realized.cost) / revenue) * 100).toFixed(1)}% of revenue` : `${MASK} of revenue`) : 'what the stock cost you'}
                icon={<BoxIcon />} accent="slate" />
          <Stat label="Cash with couriers" value={m(withCouriers.amount)} tone={plain}
                hint={<>{withCouriers.orders} delivered order{withCouriers.orders === 1 ? '' : 's'} not paid out · <Link to="/couriers">payouts</Link></>}
                icon={<TruckIcon />} accent="teal" />
          <Stat label="Expenses" value={m(expenses)} tone={plain} hint="delivery, commission, ads…"
                change={change(expenses, previous?.expenses.total, 'down')} icon={<ReceiptIcon />} accent="violet" />
        </div>

        <div className="dash-three">
          <div className="dash-card">
            <h3>Order status</h3>
            <StatusDonut parts={[
              { label: 'Paid', count: realized.orders, color: 'var(--success)' },
              { label: 'Pending', count: summary.pending.orders, color: 'var(--warning)' },
              { label: 'Returned', count: summary.returnedOrders, color: 'var(--danger)' },
              { label: 'Cancelled', count: summary.cancelledOrders, color: 'var(--muted)' },
            ]} />
            <p className="hint dash-pending">
              Pending profit expected: <b className={tone(summary.pending.profit)}>{m(summary.pending.profit)}</b>
            </p>
          </div>
          <div className="dash-card">
            <h3>Top products <span className="muted">units sold</span></h3>
            <RankBars rows={topSellers} empty="No paid orders in this period." />
          </div>
          <div className="dash-card">
            <h3>Best platforms <span className="muted">paid orders</span></h3>
            <RankBars rows={platformRows} empty="No paid orders in this period." />
          </div>
        </div>
      </div>

      <h2 className="dash-title">Recent orders <Link to="/orders">View all orders →</Link></h2>
      <div className="dash-card dash-table">
        <table>
          <thead>
            <tr>
              <th>Order</th><th>Customer</th><th>Platform</th>
              <th className="num">Revenue</th><th className="num">Profit</th><th>Status</th>
            </tr>
          </thead>
          <tbody>
            {recentOrders.map((o) => (
              <tr key={o.id}>
                <td><b>{o.orderNo}</b><br /><span className="muted">{dateTime(o.orderedAt)}</span></td>
                <td><span className="dash-av" aria-hidden="true">{(o.customerName || '?').trim().charAt(0).toUpperCase()}</span>{o.customerName}</td>
                <td>{o.platformName}</td>
                <td className={`num ${plain}`}>{m(o.revenue)}</td>
                <td className={`num ${tone(o.profit)}`}>{m(o.profit)}</td>
                <td><span className={`badge ${o.status}`}>{STATUS_LABEL[o.status]}</span></td>
              </tr>
            ))}
            {noOrders && (
              <tr><td colSpan={6} className="empty">No sales yet. <Link to="/orders/new">Record your first sale</Link>.</td></tr>
            )}
          </tbody>
        </table>
      </div>
      {prompt}
    </section>
  )
}
