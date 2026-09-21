import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { STATUS_LABEL, dateTime, money } from '../utils/format'

function Stat({ label, value, tone, hint }) {
  return (
    <div className="stat">
      <span className="stat-label">{label}</span>
      <span className={`stat-value ${tone ?? ''}`}>{value}</span>
      {hint && <span className="stat-hint">{hint}</span>}
    </div>
  )
}

export default function Dashboard() {
  const { user } = useAuth()
  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.get('/dashboard').then((res) => setData(res.data)).catch((err) => setError(errorMessage(err)))
  }, [])

  if (error) return <p className="error" role="alert">{error}</p>
  if (!data) return <p className="center-note">Loading…</p>

  const { realized, pending, returnedOrders, cancelledOrders, recentOrders } = data
  const tone = (n) => (n < 0 ? 'neg' : 'pos')
  const noOrders = recentOrders.length === 0

  return (
    <section>
      <h1>Welcome, {user.fullName}</h1>

      <h2 className="section-title">All-time results <span className="muted">(paid orders)</span></h2>
      <div className="stats">
        <Stat label="Revenue" value={money(realized.revenue)} hint={`${realized.orders} paid orders`} />
        <Stat label="Cost of goods" value={money(realized.cost)} />
        <Stat
          label={realized.profit < 0 ? 'Loss' : 'Profit'}
          value={money(realized.profit)}
          tone={tone(realized.profit)}
          hint="revenue − cost"
        />
      </div>

      <div className="stats secondary-stats">
        <Stat
          label="Pending (expected)"
          value={money(pending.profit)}
          tone={tone(pending.profit)}
          hint={`${pending.orders} unpaid orders · ${money(pending.revenue)} revenue, e.g. cash on delivery`}
        />
        <Stat label="Returned / refunded" value={returnedOrders} hint="excluded from totals" />
        <Stat label="Cancelled" value={cancelledOrders} hint="excluded from totals" />
      </div>

      <div className="page-head">
        <h2 className="section-title">Recent orders</h2>
        <Link className="btn" to="/orders/new">+ New sale</Link>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>#</th><th>Date</th><th>Customer</th><th>Platform</th>
              <th className="num">Revenue</th><th className="num">Profit</th><th>Status</th>
            </tr>
          </thead>
          <tbody>
            {recentOrders.map((o) => (
              <tr key={o.id}>
                <td>{o.id}</td>
                <td>{dateTime(o.orderedAt)}</td>
                <td>{o.customerName}</td>
                <td>{o.platformName}</td>
                <td className="num">{money(o.revenue)}</td>
                <td className={`num ${tone(o.profit)}`}>{money(o.profit)}</td>
                <td><span className={`badge ${o.status}`}>{STATUS_LABEL[o.status]}</span></td>
              </tr>
            ))}
            {noOrders && (
              <tr><td colSpan={7} className="empty">No sales yet. <Link to="/orders/new">Record your first sale</Link>.</td></tr>
            )}
          </tbody>
        </table>
      </div>
      {!noOrders && <p><Link to="/orders">View all orders →</Link></p>}
    </section>
  )
}
