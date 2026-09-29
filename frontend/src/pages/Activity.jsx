import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import { ROLE_LABELS } from '../auth/roles'
import Pager from '../components/Pager'
import RangePicker from '../components/RangePicker'
import { resolveRange } from '../utils/dateRange'
import { STATUS_LABEL, dateTime, money } from '../utils/format'

const ACTIONS = ['CREATED', 'EDITED', 'STATUS_CHANGED', 'DELETED', 'PAID_OUT', 'PAYOUT_UNDONE']
const ACTION_LABEL = {
  CREATED: 'Created',
  EDITED: 'Edited',
  STATUS_CHANGED: 'Status changed',
  DELETED: 'Deleted',
  PAID_OUT: 'Courier paid out',
  PAYOUT_UNDONE: 'Payout undone',
}
const CHANGE_LABEL = {
  PLATFORM: 'platform',
  CUSTOMER: 'customer',
  STATUS: 'status',
  ITEMS: 'items',
  DELIVERY_CHARGE: 'delivery charge',
  COURIER: 'courier details',
  DATE: 'date',
  NOTES: 'notes',
}

/** The "what happened" line under the action: which parts an edit touched, and how the status moved. */
function detail(a) {
  const status = a.fromStatus && a.toStatus && `${STATUS_LABEL[a.fromStatus]} → ${STATUS_LABEL[a.toStatus]}`
  switch (a.action) {
    case 'CREATED':
      return STATUS_LABEL[a.toStatus]
    case 'EDITED': {
      const parts = a.changes.filter((c) => c !== 'STATUS').map((c) => CHANGE_LABEL[c])
      return [parts.length > 0 && `Changed ${parts.join(', ')}`, status].filter(Boolean).join(' · ')
    }
    case 'STATUS_CHANGED':
      return status
    case 'DELETED':
      return `Was ${STATUS_LABEL[a.fromStatus]}`
    default:
      return 'Cash the courier collected'
  }
}

export default function Activity() {
  // ?orderId= comes from an order's History link: that order's entries only.
  const [params, setParams] = useSearchParams()
  const orderId = params.get('orderId') ?? ''
  const [userId, setUserId] = useState('')
  const [action, setAction] = useState('')
  const [range, setRange] = useState({ preset: 'all', from: '', to: '' })
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [team, setTeam] = useState([])
  const [error, setError] = useState('')

  useEffect(() => {
    api.get('/users').then((res) => setTeam(res.data)).catch(() => {})
  }, [])

  const resolved = useMemo(() => resolveRange(range), [range])

  const load = useCallback(() => {
    if (resolved.error) return
    api
      .get('/activity', {
        params: {
          orderId: orderId || undefined, userId: userId || undefined, action: action || undefined,
          from: resolved.from, to: resolved.to, page,
        },
      })
      .then((res) => { setData(res.data); setError('') })
      .catch((err) => setError(errorMessage(err)))
  }, [orderId, userId, action, resolved, page])
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { load() }, [load])

  return (
    <section>
      <div className="page-head">
        <h1>Activity</h1>
      </div>
      <p className="muted">Who created, edited or deleted each order, changed its status or recorded a courier payout.</p>

      <div className="filters">
        <select aria-label="Person" value={userId} onChange={(e) => { setUserId(e.target.value); setPage(0) }}>
          <option value="">Everyone</option>
          {team.map((u) => <option key={u.id} value={u.id}>{u.fullName}{u.active ? '' : ' (removed)'}</option>)}
        </select>
        <select aria-label="Action" value={action} onChange={(e) => { setAction(e.target.value); setPage(0) }}>
          <option value="">All actions</option>
          {ACTIONS.map((a) => <option key={a} value={a}>{ACTION_LABEL[a]}</option>)}
        </select>
        <RangePicker value={range} onChange={(r) => { setRange(r); setPage(0) }} />
        {orderId && (
          <button type="button" className="btn secondary small" title="Show every order's activity"
                  onClick={() => { setParams({}); setPage(0) }}>
            Order #{orderId} ✕
          </button>
        )}
      </div>

      {resolved.error && <p className="error" role="alert">{resolved.error}</p>}
      {error && <p className="error" role="alert">{error}</p>}

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>When</th><th>Who</th><th>What</th><th>Order</th><th>Customer</th><th className="num">Amount</th>
            </tr>
          </thead>
          <tbody>
            {data?.content.map((a) => (
              <tr key={a.id}>
                <td>{dateTime(a.at)}</td>
                <td>
                  {a.userName ?? '—'}
                  {a.userRole && <span className="via">{ROLE_LABELS[a.userRole]}</span>}
                </td>
                <td>
                  <span className={`badge act ${a.action}`}>{ACTION_LABEL[a.action]}</span>
                  <span className="via">{detail(a)}</span>
                </td>
                <td>
                  {a.orderExists
                    ? <Link to={`/orders/${a.orderId}`}>#{a.orderId}</Link>
                    : <span className="muted" title="This order has been deleted">#{a.orderId} (deleted)</span>}
                </td>
                <td>{a.customerName}</td>
                <td className="num">
                  {a.amountBefore != null && <span className="muted">{money(a.amountBefore)} → </span>}
                  {money(a.amount)}
                </td>
              </tr>
            ))}
            {data && data.content.length === 0 && (
              <tr><td colSpan={6} className="empty">No activity found.</td></tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager data={data} onPage={setPage} />
    </section>
  )
}
