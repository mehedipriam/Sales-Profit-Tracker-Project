import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import { ROLE_LABELS } from '../auth/roles'
import Pager from '../components/Pager'
import { PencilIcon, PlusIcon, ReturnIcon, TrashIcon, WalletIcon } from '../components/icons'
import { resolveRange, toDateStr } from '../utils/dateRange'
import { STATUS_LABEL, money } from '../utils/format'
import { tint } from '../utils/tint'

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
      return [STATUS_LABEL[a.toStatus], a.viaAgent && 'via the order assistant'].filter(Boolean).join(' · ')
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

const ACTION_TAB = {
  CREATED: 'Created', EDITED: 'Edited', STATUS_CHANGED: 'Status', DELETED: 'Deleted', PAID_OUT: 'Paid out', PAYOUT_UNDONE: 'Undone',
}
const ACTION_ICON = {
  CREATED: <PlusIcon />, EDITED: <PencilIcon />, STATUS_CHANGED: <ReturnIcon />,
  DELETED: <TrashIcon />, PAID_OUT: <WalletIcon />, PAYOUT_UNDONE: <WalletIcon />,
}
// What the person did, in a sentence that follows their name.
const ACTION_VERB = {
  CREATED: 'created', EDITED: 'edited', STATUS_CHANGED: 'changed the status of', DELETED: 'deleted',
  PAID_OUT: 'recorded a courier payout for', PAYOUT_UNDONE: 'undid the courier payout for',
}
const PERIODS = [['today', 'Today'], ['week', 'This week'], ['month', 'This month'], ['all', 'All time']]

const dayKey = (iso) => iso.slice(0, 10)
const time = (iso) => new Date(iso).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })

/** "Today", "Yesterday", or the date, for the heading above each day's entries. */
function dayLabel(key) {
  const today = toDateStr(new Date())
  const yesterday = toDateStr(new Date(Date.now() - 86400000))
  if (key === today) return 'Today'
  if (key === yesterday) return 'Yesterday'
  return new Date(`${key}T00:00:00`).toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
}

/** The date range for a period tab; Monday starts the week. */
function periodRange(period) {
  const now = new Date()
  if (period === 'today') return { from: toDateStr(now), to: toDateStr(now) }
  if (period === 'week') {
    const monday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - ((now.getDay() + 6) % 7))
    return { from: toDateStr(monday), to: toDateStr(now) }
  }
  return resolveRange({ preset: period === 'month' ? 'this_month' : 'all' })
}

export default function Activity() {
  // ?orderId= comes from an order's History link: that order's entries only.
  const [params, setParams] = useSearchParams()
  const orderId = params.get('orderId') ?? ''
  const [userId, setUserId] = useState('')
  const [action, setAction] = useState('')
  const [period, setPeriod] = useState('all')
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [team, setTeam] = useState([])
  const [error, setError] = useState('')

  useEffect(() => {
    api.get('/users').then((res) => setTeam(res.data)).catch(() => {})
  }, [])

  const load = useCallback(() => {
    const range = periodRange(period)
    api
      .get('/activity', {
        params: {
          orderId: orderId || undefined, userId: userId || undefined, action: action || undefined,
          from: range.from, to: range.to, page,
        },
      })
      .then((res) => { setData(res.data); setError('') })
      .catch((err) => setError(errorMessage(err)))
  }, [orderId, userId, action, period, page])
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { load() }, [load])

  // The page's entries, newest first, grouped under a heading for each day.
  const days = []
  data?.content.forEach((a) => {
    const key = dayKey(a.at)
    if (days.at(-1)?.key !== key) days.push({ key, entries: [] })
    days.at(-1).entries.push(a)
  })

  return (
    <section className="act-page">
      <div className="page-head">
        <div>
          <h1>Activity</h1>
          <p className="muted prod-sub">Who created, edited or deleted each order, changed its status or recorded a courier payout.</p>
        </div>
      </div>

      <div className="rep-bar">
        <div className="dash-tabs" role="tablist" aria-label="Action">
          {[['', 'All'], ...ACTIONS.map((a) => [a, ACTION_TAB[a]])].map(([value, label]) => (
            <button key={value} type="button" role="tab" aria-selected={action === value}
                    className={action === value ? 'on' : ''} onClick={() => { setAction(value); setPage(0) }}>{label}</button>
          ))}
        </div>
        <select aria-label="Person" value={userId} onChange={(e) => { setUserId(e.target.value); setPage(0) }}>
          <option value="">Everyone</option>
          {team.map((u) => <option key={u.id} value={u.id}>{u.fullName}{u.active ? '' : ' (removed)'}</option>)}
        </select>
        <div className="dash-tabs" role="tablist" aria-label="Period">
          {PERIODS.map(([value, label]) => (
            <button key={value} type="button" role="tab" aria-selected={period === value}
                    className={period === value ? 'on' : ''} onClick={() => { setPeriod(value); setPage(0) }}>{label}</button>
          ))}
        </div>
        {orderId && (
          <button type="button" className="btn secondary small" title="Show every order's activity"
                  onClick={() => { setParams({}); setPage(0) }}>
            Order {data?.content?.[0]?.orderNo ?? `#${orderId}`} ✕
          </button>
        )}
      </div>

      {error && <p className="error" role="alert">{error}</p>}

      {days.map((day) => (
        <div key={day.key}>
          <h2 className="act-day">{dayLabel(day.key)}</h2>
          <ul className="act-feed">
            {day.entries.map((a) => (
              <li key={a.id} className="act-item">
                <span className={`act-dot ${a.action}`} aria-hidden="true">{ACTION_ICON[a.action]}</span>
                <div className="act-card">
                  <div className="act-main">
                    <div className="act-line">
                      <span className="act-who">
                        <span className="act-av" style={tint(a.userName ?? '?')} aria-hidden="true">{(a.userName ?? '?').trim().charAt(0).toUpperCase()}</span>
                        <b>{a.userName ?? '—'}</b>
                        {a.userRole && <span className="act-role">{ROLE_LABELS[a.userRole]}</span>}
                      </span>{' '}
                      {ACTION_VERB[a.action]}{' '}
                      {a.orderExists
                        ? <Link to={`/orders/${a.orderId}`}>{a.orderNo ?? `#${a.orderId}`}</Link>
                        : <span className="muted" title="This order has been deleted">{a.orderNo ?? `#${a.orderId}`} (deleted)</span>}
                      {a.action === 'CREATED' && <> for {a.customerName}</>}
                    </div>
                    <div className="act-sub">
                      <span className={`badge act ${a.action}`}>{ACTION_LABEL[a.action]}</span>
                      {[detail(a), a.action !== 'CREATED' && a.customerName].filter(Boolean).join(' · ')}
                    </div>
                  </div>
                  <div className="act-right">
                    <b>
                      {a.amountBefore != null && <span className="muted act-was">{money(a.amountBefore)} → </span>}
                      {money(a.amount)}
                    </b>
                    <small>{time(a.at)}</small>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        </div>
      ))}
      {data && data.content.length === 0 && <p className="empty-note">No activity found.</p>}
      <Pager data={data} onPage={setPage} />
    </section>
  )
}
