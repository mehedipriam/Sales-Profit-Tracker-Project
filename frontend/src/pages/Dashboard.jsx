import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import NetHero from '../components/NetHero'
import { MASK, useReveal } from '../components/Reveal'
import Stat from '../components/Stat'
import {
  BoxIcon, CancelIcon, ChartIcon, ClockIcon, ReceiptIcon, ReturnIcon, TrendUpIcon, TruckIcon, WalletIcon,
} from '../components/icons'
import { STATUS_LABEL, dateTime, money } from '../utils/format'

export default function Dashboard() {
  const { user } = useAuth()
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  // Guided first-run flow: platforms already exist (seeded at registration), so the one real gap for a
  // brand new workspace is having no products yet to actually sell.
  const [productCount, setProductCount] = useState(null)
  // One eye for the whole page: every money figure stays masked until it is shown. Counts are left visible.
  const { shown, eye, prompt } = useReveal()
  const show = !!shown.all

  useEffect(() => {
    api.get('/dashboard').then((res) => setData(res.data)).catch((err) => setError(errorMessage(err)))
    api.get('/products', { params: { size: 1 } }).then((res) => setProductCount(res.data.totalElements)).catch(() => {})
  }, [])

  if (error) return <p className="error" role="alert">{error}</p>
  if (!data) return <p className="center-note">Loading…</p>

  const { realized, pending, returnedOrders, cancelledOrders, withCouriers, recentOrders, lowStock, expenses, netProfit } = data
  // While hidden, no green or red and no "loss" wording either, since those would give the figures away.
  const tone = (n) => (!show ? 'masked' : n < 0 ? 'neg' : 'pos')
  const plain = show ? '' : 'masked' // figures that are never red or green
  const m = (n) => (show ? money(n) : MASK)
  const loss = show && realized.profit < 0
  const noOrders = recentOrders.length === 0

  return (
    <section>
      <div className="page-head">
        <h1>Welcome, {user.fullName}</h1>
        <span className="page-eye">{show ? 'Hide' : 'Show'} figures{eye('all', 'figures')}</span>
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

      <h2 className="section-title">All-time results <span className="muted">(paid orders)</span></h2>
      <NetHero realized={realized} expenses={expenses} netProfit={netProfit} hidden={!show} />

      <div className="stats kpis">
        <Stat label="Revenue" value={m(realized.revenue)} tone={plain} hint={`${realized.orders} paid orders`}
              icon={<TrendUpIcon />} accent="blue" />
        <Stat label="Cost of goods" value={m(realized.cost)} tone={plain} hint="what the stock cost you"
              icon={<BoxIcon />} accent="slate" />
        <Stat
          label={loss ? 'Gross loss' : 'Gross profit'}
          value={m(realized.profit)}
          tone={tone(realized.profit)}
          hint="revenue − cost"
          icon={<ChartIcon />}
          accent={!show ? 'slate' : loss ? 'red' : 'green'}
        />
        <Stat label="Delivery charged" value={m(realized.delivery)} tone={plain} hint="paid by customers"
              icon={<TruckIcon />} accent="teal" />
        <Stat label="Expenses" value={m(expenses)} tone={plain} hint="delivery, commission, ads…"
              icon={<ReceiptIcon />} accent="violet" />
      </div>

      <h2 className="section-title">Order status</h2>
      <div className="stats kpis">
        <Stat
          label="Pending (expected)"
          value={m(pending.profit)}
          tone={tone(pending.profit)}
          hint={`${pending.orders} unpaid orders · ${m(pending.revenue)} revenue, e.g. cash on delivery`}
          icon={<ClockIcon />}
          accent="amber"
        />
        <Stat
          label="Cash with couriers"
          value={m(withCouriers.amount)}
          tone={plain}
          hint={<>{withCouriers.orders} delivered order{withCouriers.orders === 1 ? '' : 's'} not paid out yet · <Link to="/couriers">see payouts</Link></>}
          icon={<WalletIcon />}
          accent="blue"
        />
        <Stat label="Returned / refunded" value={returnedOrders} hint="excluded from totals"
              icon={<ReturnIcon />} accent="red" />
        <Stat label="Cancelled" value={cancelledOrders} hint="excluded from totals"
              icon={<CancelIcon />} accent="slate" />
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
                <td className={`num ${plain}`}>{m(o.revenue)}</td>
                <td className={`num ${tone(o.profit)}`}>{m(o.profit)}</td>
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
      {prompt}
    </section>
  )
}
