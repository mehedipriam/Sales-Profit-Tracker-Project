import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import ExportButtons from '../components/ExportButtons'
import { toDateStr } from '../utils/dateRange'
import { EXPENSE_LABEL, money } from '../utils/format'

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

const pct = (part, whole) => (Number(whole) > 0 ? `${((Number(part) / Number(whole)) * 100).toFixed(1)}%` : '—')

export default function Statement() {
  const { user } = useAuth()
  const [params, setParams] = useSearchParams()
  const { year, month } = parseMonth(params.get('month')) ?? parseMonth(currentMonth())
  const [result, setResult] = useState(null) // { key, summary } | { key, error }

  const key = `${year}-${pad(month)}`
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

  const go = (y, m) => {
    const d = new Date(y, m - 1, 1) // Date rolls month 0 / 13 into the neighbouring year for prev/next
    setParams({ month: `${d.getFullYear()}-${pad(d.getMonth() + 1)}` })
  }

  const loading = result?.key !== key
  const summary = result?.key === key ? result.summary : null
  const period = `${MONTHS[month - 1]} ${year}`
  const paid = summary?.realized
  const tone = (n) => (n < 0 ? 'neg' : 'pos')

  return (
    <section>
      <div className="page-head no-print">
        <h1>Monthly statement</h1>
        <ExportButtons range={range} platformId="" printTitle={`Statement - ${period}`} />
      </div>

      <div className="filters no-print" role="group" aria-label="Choose month">
        <button type="button" className="btn secondary" onClick={() => go(year, month - 1)} aria-label="Previous month">‹</button>
        <select aria-label="Month" value={month} onChange={(e) => go(year, Number(e.target.value))}>
          {MONTHS.map((name, i) => <option key={name} value={i + 1}>{name}</option>)}
        </select>
        <input aria-label="Year" type="number" min="2000" max="2100" value={year}
               onChange={(e) => e.target.value.length === 4 && go(Number(e.target.value), month)} />
        <button type="button" className="btn secondary" onClick={() => go(year, month + 1)} aria-label="Next month">›</button>
      </div>

      {result?.key === key && result.error && <p className="error" role="alert">{result.error}</p>}

      <article className={`statement${loading ? ' stale' : ''}`} aria-busy={loading}>
        <header className="statement-head">
          <h2>{user.businessName}</h2>
          <p className="doc-title">Monthly summary statement</p>
          <p>{period} <span className="muted">({range.from} to {range.to})</span></p>
          <p className="muted">Generated {new Date().toLocaleDateString('en-GB', { dateStyle: 'long' })}</p>
        </header>

        {paid && (
          <>
            <table className="ledger">
              <tbody>
                <tr><th scope="row">Total sold</th><td className="num">{money(paid.revenue)}</td></tr>
                <tr><th scope="row">Total cost of goods</th><td className="num">{money(paid.cost)}</td></tr>
                <tr>
                  <th scope="row">{paid.profit < 0 ? 'Gross loss' : 'Gross profit'}</th>
                  <td className={`num ${tone(paid.profit)}`}>{money(paid.profit)}</td>
                </tr>
                {summary.expenses.byType.map((t) => (
                  <tr key={t.type}><th scope="row" className="sub">less {EXPENSE_LABEL[t.type].toLowerCase()}</th>
                    <td className="num">{money(t.total)}</td></tr>
                ))}
                <tr className="ledger-total">
                  <th scope="row">{summary.netProfit < 0 ? 'Net loss' : 'Net profit'}</th>
                  <td className={`num ${tone(summary.netProfit)}`}>{money(summary.netProfit)}</td>
                </tr>
                <tr><th scope="row" className="muted">Net margin</th><td className="num muted">{pct(summary.netProfit, paid.revenue)}</td></tr>
              </tbody>
            </table>

            <h3 className="ledger-title">By platform</h3>
            <table className="ledger wide">
              <thead>
                <tr><th>Platform</th><th className="num">Orders</th><th className="num">Total sold</th>
                  <th className="num">Cost of goods</th><th className="num">Gross profit</th>
                  <th className="num">Expenses</th><th className="num">Net profit / loss</th></tr>
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
                  </tr>
                ))}
                {Number(summary.expenses.unallocated) > 0 && (
                  <tr>
                    <td>Not tied to a platform <span className="muted">(ads, overhead)</span></td>
                    <td className="num">—</td><td className="num">—</td><td className="num">—</td><td className="num">—</td>
                    <td className="num">{money(summary.expenses.unallocated)}</td>
                    <td className="num neg">{money(-summary.expenses.unallocated)}</td>
                  </tr>
                )}
                {summary.byPlatform.length === 0 && Number(summary.expenses.unallocated) === 0 && (
                  <tr><td colSpan={7} className="empty">No paid orders in {period}.</td></tr>
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
                    <td className="num">{money(summary.expenses.total)}</td>
                    <td className={`num ${tone(summary.netProfit)}`}>{money(summary.netProfit)}</td>
                  </tr>
                </tfoot>
              )}
            </table>

            <div className="statement-notes">
              <p><b>Not included in the figures above</b></p>
              <ul>
                <li>{summary.pending.orders} pending order{summary.pending.orders === 1 ? '' : 's'}
                  {summary.pending.orders > 0 && ` - expected ${money(summary.pending.revenue)} sold, ${money(summary.pending.profit)} profit once paid`}</li>
                <li>{summary.returnedOrders} returned / refunded, {summary.cancelledOrders} cancelled</li>
              </ul>
              <p className="muted">
                Sales figures cover paid orders only. Net profit is gross profit minus the period's expenses (delivery,
                packaging, platform commission, ads and the like); expenses on orders that are still pending count once
                the order is paid.
              </p>
            </div>
          </>
        )}
      </article>
    </section>
  )
}
