import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Change from '../components/Change'
import ExportButtons from '../components/ExportButtons'
import { MASK, useReveal } from '../components/Reveal'
import { ClockIcon, ReturnIcon } from '../components/icons'
import { logoUrl } from '../components/logo'
import { toDateStr } from '../utils/dateRange'
import { EXPENSE_LABEL, currencySymbol, money as fmt } from '../utils/format'

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']
const pad = (n) => String(n).padStart(2, '0')
const MONTH_PARAM = /^(\d{4})-(0[1-9]|1[0-2])$/

function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}`
}

/** Reads ?month=yyyy-MM, ignoring anything malformed. */
function parseMonth(value) {
  const m = MONTH_PARAM.exec(value ?? '')
  return m ? { year: Number(m[1]), month: Number(m[2]) } : null
}

const pctOf = (part, whole) => (Number(whole) > 0 ? `${((Number(part) / Number(whole)) * 100).toFixed(1)}%` : '—')

export default function Statement() {
  const { user } = useAuth()
  const [params, setParams] = useSearchParams()
  const { year, month } = parseMonth(params.get('month')) ?? parseMonth(currentMonth())
  const [result, setResult] = useState(null) // { key, summary } | { key, error }
  // One eye for the whole page: every amount and margin stays masked until it is shown. Counts are left visible.
  const { shown, eye, prompt } = useReveal()
  const show = !!shown.all

  const key = `${year}-${pad(month)}`
  const [yearTrend, setYearTrend] = useState(null) // { year, months: Set of month numbers that have sales or expenses }
  const [before, setBefore] = useState(null) // { key, summary } for the month before the one shown
  const range = useMemo(
    () => ({ from: toDateStr(new Date(year, month - 1, 1)), to: toDateStr(new Date(year, month, 0)) }),
    [year, month],
  )

  useEffect(() => {
    let live = true
    api
      .get('/reports/summary', { params: range })
      .then((res) => live && setResult({ key, summary: res.data }))
      .catch((err) => live && setResult({ key, error: errorMessage(err) }))
    return () => { live = false }
  }, [key, range])

  useEffect(() => {
    let live = true
    api
      .get('/reports/trend', { params: { from: `${year}-01-01`, to: `${year}-12-31` } })
      .then((res) => {
        if (!live) return
        const months = new Set(res.data.points
          .filter((p) => Number(p.orders) > 0 || Number(p.revenue) > 0 || Number(p.expenses) > 0)
          .map((p) => new Date(`${p.period}T00:00:00`).getMonth() + 1))
        setYearTrend({ year, months })
      })
      .catch(() => live && setYearTrend(null))
    return () => { live = false }
  }, [year, key])

  useEffect(() => {
    let live = true
    const d = new Date(year, month - 2, 1)
    const prev = { from: toDateStr(d), to: toDateStr(new Date(d.getFullYear(), d.getMonth() + 1, 0)) }
    api
      .get('/reports/summary', { params: prev })
      .then((res) => live && setBefore({ key, summary: res.data }))
      .catch(() => live && setBefore(null))
    return () => { live = false }
  }, [key, year, month])

  const go = (y, m) => {
    const d = new Date(y, m - 1, 1) // Date rolls month 0 / 13 into the neighbouring year for prev/next
    setParams({ month: `${d.getFullYear()}-${pad(d.getMonth() + 1)}` })
  }

  const active = yearTrend?.year === year ? yearTrend.months : new Set()
  const prevSummary = before?.key === key ? before.summary : null
  const prevName = MONTHS[(month + 10) % 12].slice(0, 3)
  const compare = { label: `vs ${prevName}`, period: MONTHS[(month + 10) % 12] }
  const change = (now, was, better) =>
    prevSummary && show && <Change now={now} before={was} compare={compare} better={better} />
  const loading = result?.key !== key
  const summary = result?.key === key ? result.summary : null
  const period = `${MONTHS[month - 1]} ${year}`
  const paid = summary?.realized
  // While hidden: dots instead of amounts, and no green, red or "loss" wording, since those would give them away.
  const money = (n) => (show ? fmt(n) : MASK)
  const pct = (part, whole) => (show ? pctOf(part, whole) : MASK)
  const tone = (n) => (!show ? 'masked' : n < 0 ? 'neg' : 'pos')
  const neg = (n) => show && n < 0
  const flowRows = summary && paid ? [
    { label: 'Total sold', value: Number(paid.revenue), color: 'var(--primary)', signed: '' },
    { label: 'Cost of goods', value: Number(paid.cost), color: 'var(--muted)', signed: '−' },
    { label: neg(paid.profit) ? 'Gross loss' : 'Gross profit', value: Math.abs(Number(paid.profit)), color: 'var(--success)', signed: '', tone: tone(paid.profit) },
    ...(Number(paid.delivery) > 0 ? [{ label: 'plus delivery charged to customers', value: Number(paid.delivery), color: '#2dd4bf', signed: '+', sub: true }] : []),
    ...summary.expenses.byType.map((t) => ({ label: `less ${EXPENSE_LABEL[t.type].toLowerCase()}`, value: Number(t.total), color: '#a78bfa', signed: '−', sub: true })),
    { label: neg(summary.netProfit) ? 'Net loss' : 'Net profit', value: Math.abs(Number(summary.netProfit)), signed: '', total: true, tone: tone(summary.netProfit) },
  ] : []
  const flowMax = Math.max(1, ...flowRows.map((r) => r.value))

  return (
    <section>
      <div className="page-head no-print">
        <h1>Monthly statement</h1>
        <span className="page-eye">{show ? 'Hide' : 'Show'} figures{eye('all', 'figures')}</span>
        <ExportButtons range={range} platformId="" printTitle={`Statement - ${period}`} />
      </div>

      <div className="month-strip no-print" role="group" aria-label="Choose month">
        <div className="month-year">
          <button type="button" onClick={() => go(year - 1, month)} aria-label="Previous year">‹</button>
          <span>{year}</span>
          <button type="button" onClick={() => go(year + 1, month)} aria-label="Next year">›</button>
        </div>
        {MONTHS.map((name, i) => (
          <button key={name} type="button" aria-current={i + 1 === month ? 'date' : undefined}
                  className={`${i + 1 === month ? 'on' : ''}${active.has(i + 1) ? ' has' : ''}`}
                  title={active.has(i + 1) ? `${name} has sales or expenses` : `${name}: nothing recorded`}
                  onClick={() => go(year, i + 1)}>{name.slice(0, 3)}</button>
        ))}
        {key !== currentMonth() && (
          <button type="button" className="month-today" onClick={() => setParams({})}>This month</button>
        )}
      </div>

      {result?.key === key && result.error && <p className="error" role="alert">{result.error}</p>}

      <article className={`statement${loading ? ' stale' : ''}`} aria-busy={loading}>
        <header className="statement-head">
          <div className="statement-brand">
            <img src={logoUrl(currencySymbol())} alt="" />
            <div>
              <h2>{user.businessName}</h2>
              <p className="doc-title">Monthly summary statement</p>
            </div>
          </div>
          <div className="statement-period">
            <strong>{period}</strong>
            <span>{range.from} to {range.to}</span>
            <span>Generated {new Date().toLocaleDateString('en-GB', { dateStyle: 'long' })}</span>
          </div>
        </header>

        {paid && (
          <div className="statement-body">
            <div className="statement-tiles">
              <div className="st-tile accent-blue"><span>Total sold</span><b>{money(paid.revenue)}</b>
                <small>{paid.orders} paid order{paid.orders === 1 ? '' : 's'}</small>{change(paid.revenue, prevSummary?.realized.revenue, 'up')}</div>
              <div className={`st-tile ${!show ? 'accent-slate' : paid.profit < 0 ? 'accent-red' : 'accent-green'}`}>
                <span>{neg(paid.profit) ? 'Gross loss' : 'Gross profit'}</span><b>{money(paid.profit)}</b>
                <small>{pct(paid.profit, paid.revenue)} of sales</small>{change(paid.profit, prevSummary?.realized.profit, 'up')}</div>
              <div className="st-tile accent-violet"><span>Expenses</span><b>{money(summary.expenses.total)}</b>
                <small>delivery, commission, ads…</small>{change(summary.expenses.total, prevSummary?.expenses.total, 'down')}</div>
              <div className={`st-tile hero ${neg(summary.netProfit) ? 'loss' : ''}`}>
                <span>{neg(summary.netProfit) ? 'Net loss' : 'Net profit'}</span><b>{money(summary.netProfit)}</b>
                <small>{pct(summary.netProfit, paid.revenue)} net margin</small>{change(summary.netProfit, prevSummary?.netProfit, 'up')}</div>
            </div>

            <h3 className="ledger-title">How it adds up</h3>
            <ol className="flow-list" aria-label="How net profit is worked out">
              {flowRows.map((r) => (
                <li key={r.label} className={`flow-row${r.sub ? ' sub' : ''}${r.total ? ' total' : ''}`}>
                  <span className="flow-label">{r.label}</span>
                  <span className="flow-bar" aria-hidden="true">
                    {show && !r.total && r.value > 0 && (
                      <i style={{ width: `${Math.max(1.5, (r.value / flowMax) * 100)}%`, background: r.color }} />
                    )}
                  </span>
                  <b className={`flow-amt ${r.tone ?? ''}`}>{r.signed}{money(r.value)}</b>
                </li>
              ))}
              <li className="flow-row muted"><span className="flow-label">Net margin</span><span className="flow-bar" /><b className="flow-amt">{pct(summary.netProfit, paid.revenue)}</b></li>
            </ol>

            <h3 className="ledger-title">By platform</h3>
            <div className="ledger-scroll">
            <table className="ledger wide">
              <thead>
                <tr><th>Platform</th><th className="num">Orders</th><th className="num">Total sold</th>
                  <th className="num">Cost of goods</th><th className="num">Gross profit</th>
                  <th className="num">Delivery charged</th><th className="num">Expenses</th>
                  <th className="num">Net profit / loss</th></tr>
              </thead>
              <tbody>
                {summary.byPlatform.map((r) => (
                  <tr key={r.platformId}>
                    <td><span className="rep-pill">{r.platformName}</span></td>
                    <td className="num">{r.orders}</td>
                    <td className="num">{money(r.revenue)}</td>
                    <td className="num">{money(r.cost)}</td>
                    <td className={`num ${tone(r.profit)}`}>{money(r.profit)}</td>
                    <td className="num">{money(r.delivery)}</td>
                    <td className="num">{money(r.expenses)}</td>
                    <td className={`num ${tone(r.netProfit)}`}>{money(r.netProfit)}</td>
                  </tr>
                ))}
                {Number(summary.expenses.unallocated) > 0 && (
                  <tr>
                    <td>Not tied to a platform <span className="muted">(ads, overhead)</span></td>
                    <td className="num">—</td><td className="num">—</td><td className="num">—</td><td className="num">—</td>
                    <td className="num">—</td>
                    <td className="num">{money(summary.expenses.unallocated)}</td>
                    <td className={`num ${tone(-1)}`}>{money(-summary.expenses.unallocated)}</td>
                  </tr>
                )}
                {summary.byPlatform.length === 0 && Number(summary.expenses.unallocated) === 0 && (
                  <tr><td colSpan={8} className="empty">No paid orders in {period}.</td></tr>
                )}
              </tbody>
              {(summary.byPlatform.length > 0 || Number(summary.expenses.unallocated) > 0) && (
                <tfoot>
                  <tr>
                    <th>Total</th>
                    <td className="num">{paid.orders}</td>
                    <td className="num">{money(paid.revenue)}</td>
                    <td className="num">{money(paid.cost)}</td>
                    <td className={`num ${tone(paid.profit)}`}>{money(paid.profit)}</td>
                    <td className="num">{money(paid.delivery)}</td>
                    <td className="num">{money(summary.expenses.total)}</td>
                    <td className={`num ${tone(summary.netProfit)}`}>{money(summary.netProfit)}</td>
                  </tr>
                </tfoot>
              )}
            </table>
            </div>

            <div className="statement-notes">
              <h3 className="ledger-title">Not included in the figures above</h3>
              <div className="note-cards">
                <div className="note-card accent-amber">
                  <span className="stat-icon" aria-hidden="true"><ClockIcon /></span>
                  <div>
                    <b>{summary.pending.orders} pending order{summary.pending.orders === 1 ? '' : 's'}</b>
                    <p>{summary.pending.orders > 0
                      ? `Expected ${money(summary.pending.revenue)} sold, ${money(summary.pending.profit)} profit once paid`
                      : 'Nothing waiting on payment'}</p>
                  </div>
                </div>
                <div className="note-card accent-red">
                  <span className="stat-icon" aria-hidden="true"><ReturnIcon /></span>
                  <div>
                    <b>{summary.returnedOrders} returned · {summary.cancelledOrders} cancelled</b>
                    <p>Left out of sales and profit</p>
                  </div>
                </div>
              </div>
              <p className="muted statement-fineprint">
                Sales figures cover paid orders only. Net profit is gross profit plus the delivery charges customers paid,
                minus the period's expenses (delivery, packaging, platform commission, ads and the like); expenses on
                orders that are still pending count once the order is paid.
              </p>
            </div>
          </div>
        )}
      </article>
      {prompt}
    </section>
  )
}
