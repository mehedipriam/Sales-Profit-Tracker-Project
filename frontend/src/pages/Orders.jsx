import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { seesFinancials } from '../auth/roles'
import Pager from '../components/Pager'
import { EyeHead, MASK, useReveal } from '../components/Reveal'
import RangePicker from '../components/RangePicker'
import Tracking from '../components/Tracking'
import { resolveRange } from '../utils/dateRange'
import { STATUSES, STATUS_LABEL, dateTime, money } from '../utils/format'

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
  const [platforms, setPlatforms] = useState([])
  const [error, setError] = useState('')

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
  }, [platformId, customerId, status, q, resolved, page])
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { load() }, [load])

  const changeStatus = async (o, next) => {
    try {
      await api.patch(`/orders/${o.id}/status`, { status: next })
      load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const remove = async (o) => {
    if (!window.confirm(`Delete order #${o.id} for ${o.customerName}? This cannot be undone.`)) return
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
          {data && <p className="page-sub">{data.totalElements} {data.totalElements === 1 ? 'order' : 'orders'}</p>}
        </div>
        <Link className="btn" to="/orders/new">+ New sale</Link>
      </div>

      <div className="filters orders-filters">
        <input type="search" className="orders-search" value={search} onChange={(e) => setSearch(e.target.value)}
               placeholder="Search customer name or order no." aria-label="Search orders by customer name or order number" />
        <select value={platformId} onChange={(e) => { setPlatformId(e.target.value); setPage(0) }}>
          <option value="">All platforms</option>
          {platforms.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
        <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }}>
          <option value="">All statuses</option>
          {STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
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
      {error &&<p className="error" role="alert">{error}</p>}

      <div className="table-wrap">
        <table className="orders-table striped">
          <thead>
            <tr>
              <th className="serial">#</th><th className="order-id">Order no.</th><th>Date</th><th>Customer</th><th>Platform / courier</th><th className="num hide-md">Items</th>
              <th className="num">Revenue</th>
              {showMoney && <>
                <th className="num hide-col"><EyeHead eye={eye('cost', 'costs')}>Cost</EyeHead></th>
                <th className="num hide-col"><EyeHead eye={eye('profit', 'profits')}>Profit</EyeHead></th>
              </>}
              <th className="status-col">Status</th>
            </tr>
          </thead>
          <tbody>
            {data?.content.map((o, i) => (
              <tr key={o.id}>
                <td className="serial" title="Position in this list, newest first">{data.totalElements - data.page * PAGE_SIZE - i}</td>
                <td className="order-id">{o.orderNo}</td>
                <td className="when">{dateTime(o.orderedAt)}</td>
                <td className="cust-name">{o.customerName}</td>
                <td className="platform-cell">
                  <span className="platform-name">{o.platformName}</span>
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
                  <div className="row-actions">
                    <Link className="link" to={`/orders/${o.id}`}>Edit</Link>
                    <Link className="link" to={`/orders/${o.id}/invoice`}>Invoice</Link>
                    {showMoney && <Link className="link" to={`/expenses?newFor=${o.id}`}>Expense</Link>}
                    {showMoney && <Link className="link" to={`/activity?orderId=${o.id}`}>History</Link>}
                    <button className="link danger" onClick={() => remove(o)}>Delete</button>
                  </div>
                </td>
              </tr>
            ))}
            {data && data.content.length === 0 && (
              <tr><td colSpan={showMoney ? 10 : 8} className="empty">{q || platformId || status || customerId || range.preset !== 'all'
                ? 'No orders match these filters.' : 'No orders yet. Record your first sale.'}</td></tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager data={data} onPage={setPage} />
      {prompt}
    </section>
  )
}
