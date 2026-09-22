import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Stat from '../components/Stat'
import { STATUS_LABEL, dateTime, money } from '../utils/format'

export default function Dashboard() {
  const { user } = useAuth()
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  // Guided first-run flow: platforms already exist (seeded at registration), so the one real gap for a
  // brand new workspace is having no products yet to actually sell.
  const [productCount, setProductCount] = useState(null)

  useEffect(() => {
    api.get('/dashboard').then((res) => setData(res.data)).catch((err) => setError(errorMessage(err)))
    api.get('/products', { params: { size: 1 } }).then((res) => setProductCount(res.data.totalElements)).catch(() => {})
  }, [])

  if (error) return <p className="error" role="alert">{error}</p>
  if (!data) return <p className="center-note">Loading…</p>

  const { realized, pending, returnedOrders, cancelledOrders, recentOrders, lowStock, expenses, netProfit } = data
  const tone = (n) => (n < 0 ? 'neg' : 'pos')
  const noOrders = recentOrders.length === 0

  return (
    <section>
      <h1>Welcome, {user.fullName}</h1>

      {productCount === 0 && (
        <div className="alert-card getting-started">
          <h2>Get your workspace ready</h2>
          <ol>
            <li>Platforms — Facebook Page and Daraz are already set up; <Link to="/platforms">add more or rename them</Link>.</li>
            <li><Link to="/products">Add your first product</Link> so there's something to sell.</li>
          </ol>
        </div>
      )}

      <h2 className="section-title">All-time results <span className="muted">(paid orders)</span></h2>
      <div className="stats">
        <Stat label="Revenue" value={money(realized.revenue)} hint={`${realized.orders} paid orders`} />
        <Stat label="Cost of goods" value={money(realized.cost)} />
        <Stat
          label={realized.profit < 0 ? 'Gross loss' : 'Gross profit'}
          value={money(realized.profit)}
          tone={tone(realized.profit)}
          hint="revenue − cost"
        />
        <Stat label="Expenses" value={money(expenses)} hint="delivery, commission, ads…" />
        <Stat
          label={netProfit < 0 ? 'Net loss' : 'Net profit'}
          value={money(netProfit)}
          tone={tone(netProfit)}
          hint="gross − expenses"
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

      {lowStock.length > 0 && (
        <div className="alert-card" role="alert">
          <h2>Low stock · {lowStock.length} product{lowStock.length === 1 ? '' : 's'}</h2>
          <ul>
            {lowStock.map((p) => (
              <li key={p.productId}>
                <Link to={`/stock?productId=${p.productId}`}>{p.name}</Link>
                <span>{p.stockQty} left (alert at {p.threshold})</span>
              </li>
            ))}
          </ul>
        </div>
      )}

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
