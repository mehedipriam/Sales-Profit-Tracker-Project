import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { seesFinancials } from '../auth/roles'
import OrderDetails from '../components/OrderDetails'
import Pager from '../components/Pager'
import { EyeHead, MASK, useReveal } from '../components/Reveal'
import RangePicker from '../components/RangePicker'
import Stat from '../components/Stat'
import Tracking from '../components/Tracking'
import { ClockIcon, PencilIcon, ReceiptIcon, ReturnIcon, TrashIcon, TrendUpIcon, WalletIcon } from '../components/icons'
import { resolveRange } from '../utils/dateRange'
import { STATUSES, STATUS_LABEL, dateTime, money } from '../utils/format'
import { tint } from '../utils/tint'

const PAGE_SIZE = 20 // the API's default page size

export default function Orders() {
  const { user } = useAuth()
  const showMoney = seesFinancials(user)
  // Cost and profit each have their own eye, like on Products.
  const { shown, eye, prompt } = useReveal()
  // Set by the order count on the Customers page: that customer's orders only.
  const [params, setParams] = useSearchParams()
  const customerId = params.get('customerId') ?? ''
  const customerName = params.get('customer') || `customer #${customerId}`
  const [platformId, setPlatformId] = useState('')
  const [status, setStatus] = useState('')
  const [range, setRange] = useState({ preset: 'all', from: '', to: '' })
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('') // what is typed
  const [q, setQ] = useState('') // what is sent, a moment after typing stops
  const [data, setData] = useState(null)
  const [totals, setTotals] = useState(null) // { PENDING: { orders, revenue }, ... } for the cards and chips
  const [platforms, setPlatforms] = useState([])
  const [error, setError] = useState('')
  const [viewing, setViewing] = useState(null) // id of the order whose details are open

  useEffect(() => {
    api.get('/platforms').then((res) => setPlatforms(res.data)).catch(() => {})
  }, [])

  useEffect(() => {
    const timer = setTimeout(() => { setQ(search.trim()); setPage(0) }, 300)
    return () => clearTimeout(timer)
  }, [search])

  const resolved = useMemo(() => resolveRange(range), [range])

  const load = useCallback(() => {
    if (resolved.error) return
    api
      .get('/orders', {
        params: {
          platformId: platformId || undefined, customerId: customerId || undefined, status: status || undefined, q: q || undefined,
          from: resolved.from, to: resolved.to, page,
        },
      })
      .then((res) => { setData(res.data); setError('') })
      .catch((err) => setError(errorMessage(err)))
    // Counts per status ignore the status chip itself, so every chip keeps showing its own number.
    api
      .get('/orders/summary', {
        params: { platformId: platformId || undefined, customerId: customerId || undefined, q: q || undefined, from: resolved.from, to: resolved.to },
      })
      .then((res) => setTotals(Object.fromEntries(res.data.map((t) => [t.status, t]))))
      .catch(() => {})
  }, [platformId, customerId, status, q, resolved, page])
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { load() }, [load])

  const count = (which) => (which
    ? totals?.[which]?.orders ?? 0
    : Object.values(totals ?? {}).reduce((n, t) => n + t.orders, 0))

  const changeStatus = async (o, next) => {
    try {
      await api.patch(`/orders/${o.id}/status`, { status: next })
      load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const remove = async (o) => {
    if (!window.confirm(`Delete order ${o.orderNo} for ${o.customerName}? This cannot be undone.`)) return
    try {
      await api.delete(`/orders/${o.id}`)
      load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Orders</h1>
          <p className="muted prod-sub">Every sale, its status and what it earned</p>
        </div>
        <Link className="btn" to="/orders/new">+ New sale</Link>
      </div>

      {totals && (
        <div className="stats kpis">
          <Stat label="Orders" value={count()} hint={customerId ? `for ${customerName}` : 'in this view'} icon={<ReceiptIcon />} accent="blue" />
          <Stat label="Pending" value={count('PENDING')} tone={count('PENDING') ? 'warn' : ''}
                hint={`${money(totals.PENDING?.revenue ?? 0)} on the way`} icon={<ClockIcon />} accent="amber" />
          <Stat label="Paid" value={count('PAID')} tone={count('PAID') ? 'pos' : ''}
                hint={`${money(totals.PAID?.revenue ?? 0)} revenue`} icon={<TrendUpIcon />} accent="green" />
          <Stat label="Returned / cancelled" value={count('RETURNED') + count('CANCELLED')}
                hint="not counted in profit" icon={<ReturnIcon />} accent="red" />
        </div>
      )}

      <div className="rep-bar">
        <input type="search" className="prod-search" value={search} onChange={(e) => setSearch(e.target.value)}
               placeholder="Search customer name or order no." aria-label="Search orders by customer name or order number" />
        <div className="dash-tabs" role="tablist" aria-label="Order status">
          {[['', 'All'], ...STATUSES.map((s) => [s, STATUS_LABEL[s]])].map(([value, label]) => (
            <button key={value} type="button" role="tab" aria-selected={status === value}
                    className={status === value ? 'on' : ''} onClick={() => { setStatus(value); setPage(0) }}>
              {label}{totals && <em className="tab-count">{count(value)}</em>}
            </button>
          ))}
        </div>
        <select value={platformId} onChange={(e) => { setPlatformId(e.target.value); setPage(0) }} aria-label="Platform">
          <option value="">All platforms</option>
          {platforms.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
        <RangePicker value={range} onChange={(r) => { setRange(r); setPage(0) }} />
        {customerId && (
          <button type="button" className="btn secondary small" title="Show every customer's orders"
                  onClick={() => { setParams({}); setPage(0) }}>
            {customerName} ✕
          </button>
        )}
      </div>

      {resolved.error && <p className="error" role="alert">{resolved.error}</p>}
      {error && <p className="error" role="alert">{error}</p>}

      <div className="dash-card prod-table">
        <table className="orders-table">
          <thead>
            <tr>
              <th>Order</th><th>Customer</th><th>Platform</th><th className="num hide-md">Items</th>
              <th className="num">Revenue</th>
              {showMoney && <>
                <th className="num hide-col"><EyeHead eye={eye('cost', 'costs')}>Cost</EyeHead></th>
                <th className="num hide-col"><EyeHead eye={eye('profit', 'profits')}>Profit</EyeHead></th>
              </>}
              <th className="status-col">Status</th>
            </tr>
          </thead>
          <tbody>
            {data?.content.map((o) => (
              <tr key={o.id} className="clickable" title="Click for the order's details"
                  onClick={(e) => { if (!e.target.closest('a, button, select, input, label')) setViewing(o.id) }}>
                <td className="order-id">
                  <button type="button" className="order-link" onClick={() => setViewing(o.id)}>{o.orderNo}</button>
                  <small className="muted when">{dateTime(o.orderedAt)}</small>
                </td>
                <td>
                  <div className="ord-who">
                    <span className="ord-av" style={tint(o.customerName)} aria-hidden="true">{o.customerName.trim().charAt(0).toUpperCase()}</span>
                    <span className="cust-name">{o.customerName}</span>
                  </div>
                </td>
                <td className="platform-cell">
                  <span className="rep-pill">{o.platformName}</span>
                  {o.courier && (
                    <span className="via">
                      via {o.courier.courierName}
                      {o.courier.consignmentId && <> · {o.courier.consignmentId}</>}
                      {o.courier.trackingNumber
                        ? <> · <Tracking number={o.courier.trackingNumber} link={o.courier.trackingLink} /></>
                        : o.courier.trackingLink && <> · <a href={o.courier.trackingLink} target="_blank" rel="noopener noreferrer">Track ↗</a></>}
                    </span>
                  )}
                </td>
                <td className="num hide-md">{o.itemCount}</td>
                <td className="num">{money(o.revenue)}</td>
                {showMoney && <>
                  {shown.cost ? <td className="num">{money(o.cost)}</td> : <td className="num masked hide-col">{MASK}</td>}
                  {shown.profit
                    ? <td className="num"><span className={`profit-chip ${o.profit < 0 ? 'neg' : 'pos'}`}>{money(o.profit)}</span></td>
                    : <td className="num masked hide-col">{MASK}</td>}
                </>}
                <td className="status-col">
                  <select className={`status ${o.status}`} value={o.status} aria-label={`Status of order ${o.id}`}
                          onChange={(e) => changeStatus(o, e.target.value)}>
                    {STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
                  </select>
                  <div className="ord-acts">
                    <Link className="ord-act" to={`/orders/${o.id}`} title="Edit" aria-label="Edit"><PencilIcon /></Link>
                    <Link className="ord-act" to={`/orders/${o.id}/invoice`} title="Invoice" aria-label="Invoice"><ReceiptIcon /></Link>
                    {showMoney && <Link className="ord-act" to={`/expenses?newFor=${o.id}`} title="Add expense" aria-label="Add expense"><WalletIcon /></Link>}
                    {showMoney && <Link className="ord-act" to={`/activity?orderId=${o.id}`} title="History" aria-label="History"><ClockIcon /></Link>}
                    <button type="button" className="ord-act danger" title="Delete" aria-label="Delete" onClick={() => remove(o)}><TrashIcon /></button>
                  </div>
                </td>
              </tr>
            ))}
            {data && data.content.length === 0 && (
              <tr><td colSpan={showMoney ? 8 : 6} className="empty">{q || platformId || status || customerId || range.preset !== 'all'
                ? 'No orders match these filters.' : 'No orders yet. Record your first sale.'}</td></tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager data={data} onPage={setPage} />
      {prompt}
      {viewing && <OrderDetails id={viewing} onClose={() => setViewing(null)} />}
    </section>
  )
}
