import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import Change from '../components/Change'
import Modal from '../components/Modal'
import Pager from '../components/Pager'
import useDebounce from '../hooks/useDebounce'
import { PRESETS, describeRange, previousRange, resolveRange, toDateStr } from '../utils/dateRange'
import { EXPENSE_LABEL, EXPENSE_TYPES, money } from '../utils/format'

// Shorter names for the type tabs; the table and cards use the full labels.
const EXPENSE_SHORT = { DELIVERY: 'Delivery', PACKAGING: 'Packaging', ADS: 'Ads', PLATFORM_COMMISSION: 'Commission', MISC: 'Misc' }

function ExpenseForm({ expense, orderId, onSaved, onCancel }) {
  const [form, setForm] = useState({
    type: expense?.type ?? 'DELIVERY',
    amount: expense?.amount ?? '',
    expenseDate: expense?.expenseDate ?? toDateStr(new Date()),
    description: expense?.description ?? '',
    orderId: expense?.orderId ?? orderId ?? '',
  })
  // The order is typed by its number (ORD-2610-0001); the id behind it is kept until the text is changed.
  const [orderNo, setOrderNo] = useState(expense?.orderNo ?? '')
  const [knownNo, setKnownNo] = useState(expense?.orderNo ?? '')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  // Opened from an order's "Expense" link: only its id is known, so look up the number to show.
  useEffect(() => {
    if (expense || !orderId) return
    api.get(`/orders/${orderId}`).then(({ data }) => { setOrderNo(data.orderNo); setKnownNo(data.orderNo) }).catch(() => {})
  }, [expense, orderId])

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      const typed = orderNo.trim()
      let linked = null
      if (typed) {
        if (form.orderId !== '' && typed.toLowerCase() === knownNo.toLowerCase()) {
          linked = Number(form.orderId)
        } else {
          const { data } = await api.get('/orders', { params: { q: typed, size: 20 } })
          const match = data.content.find((o) => o.orderNo.toLowerCase() === typed.toLowerCase())
          if (!match) throw new Error(`No order has the number ${typed}.`)
          linked = match.id
        }
      }
      const body = { ...form, orderId: linked }
      if (expense) await api.put(`/expenses/${expense.id}`, body)
      else await api.post('/expenses', body)
      onSaved()
    } catch (err) {
      setError(err.response ? errorMessage(err) : err.message)
      setBusy(false)
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <div className="row">
        <label>
          Type
          <select value={form.type} onChange={set('type')}>
            {EXPENSE_TYPES.map((t) => <option key={t} value={t}>{EXPENSE_LABEL[t]}</option>)}
          </select>
        </label>
        <label>
          Amount
          <input required type="number" min="0.01" step="0.01" value={form.amount} onChange={set('amount')} />
        </label>
        <label>
          Date
          <input required type="date" value={form.expenseDate} onChange={set('expenseDate')} />
        </label>
      </div>
      <label>Description (optional)<input maxLength={255} value={form.description} onChange={set('description')} /></label>
      <label>
        Order no. (optional)
        <input value={orderNo} onChange={(e) => setOrderNo(e.target.value)} maxLength={20}
               placeholder="e.g. ORD-2610-0001, to link it to a specific order" />
      </label>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button type="button" className="btn secondary" onClick={onCancel}>Cancel</button>
        <button className="btn" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
    </form>
  )
}

const TYPE_COLOR = {
  DELIVERY: 'var(--primary)',
  PACKAGING: 'var(--warning)',
  ADS: '#a78bfa',
  PLATFORM_COMMISSION: '#2dd4bf',
  MISC: 'var(--muted)',
}

const weekday = (s) => new Date(`${s}T00:00:00`).toLocaleDateString('en-GB', { weekday: 'long' })
const longDay = (s) => new Date(`${s}T00:00:00`).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })

/** The share of each expense type as a ring, beside a bar per type. */
function TypeBreakdown({ summary }) {
  const total = Number(summary.total)
  const rows = summary.byType.filter((t) => Number(t.total) > 0)
  let at = 0
  const stops = rows.map((t) => {
    const from = at
    at += (Number(t.total) / total) * 100
    return `${TYPE_COLOR[t.type]} ${from}% ${at}%`
  })
  const max = Math.max(1, ...rows.map((t) => Number(t.total)))
  return (
    <div className="dash-card">
      <h3>By type</h3>
      {rows.length === 0 ? <p className="muted">No expenses in this period.</p> : (
        <div className="dash-donut-wrap">
          <div className="dash-donut big" style={{ background: `conic-gradient(${stops.join(', ')})` }}>
            <div><b>{summary.count}</b><span>{summary.count === 1 ? 'entry' : 'entries'}</span></div>
          </div>
          <div className="exp-rank">
            {rows.map((t) => (
              <div key={t.type}>
                <i className="exp-dot" style={{ background: TYPE_COLOR[t.type] }} />
                <span className="exp-rank-name">{EXPENSE_LABEL[t.type]}</span>
                <span className="dash-rank-bar"><i style={{ width: `${(Number(t.total) / max) * 100}%`, background: TYPE_COLOR[t.type] }} /></span>
                <b>{money(t.total)}</b>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

export default function Expenses() {
  const [params, setParams] = useSearchParams()
  const [range, setRange] = useState({ preset: 'this_month', from: '', to: '' })
  const [type, setType] = useState('')
  const [search, setSearch] = useState('')
  const dq = useDebounce(search)
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [summary, setSummary] = useState(null)
  const [previous, setPrevious] = useState(null) // the same figures for the period before, when there is one
  const [error, setError] = useState('')
  const [viewing, setViewing] = useState(null)
  // ?newFor=<order id> (from the orders screens) opens the form already linked to that order
  const [editing, setEditing] = useState(() => (params.get('newFor') ? { orderId: params.get('newFor') } : null))

  const resolved = useMemo(() => resolveRange(range), [range])
  const prior = useMemo(() => previousRange(range), [range])

  const load = useCallback(() => {
    if (resolved.error) return
    const dates = { from: resolved.from, to: resolved.to }
    Promise.all([
      api.get('/expenses', { params: { ...dates, type: type || undefined, q: dq.trim() || undefined, page } }),
      api.get('/expenses/summary', { params: dates }),
      prior ? api.get('/expenses/summary', { params: { from: prior.from, to: prior.to } }) : null,
    ])
      .then(([list, sum, before]) => { setData(list.data); setSummary(sum.data); setPrevious(before && before.data); setError('') })
      .catch((err) => setError(errorMessage(err)))
  }, [resolved, prior, type, dq, page])
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { load() }, [load])

  const closeForm = () => {
    setEditing(null)
    if (params.has('newFor')) setParams({}, { replace: true })
  }

  const remove = async (e) => {
    if (!window.confirm(`Delete this ${EXPENSE_LABEL[e.type].toLowerCase()} expense of ${money(e.amount)}?`)) return
    try {
      await api.delete(`/expenses/${e.id}`)
      load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const biggest = summary?.byType[0]
  const compare = prior && { label: prior.label, period: describeRange(prior) }

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Expenses</h1>
          <p className="muted prod-sub">Where the money goes besides the stock itself</p>
        </div>
        <button className="btn" onClick={() => setEditing({})}>+ Add expense</button>
      </div>

      <div className="rep-bar">
        <div className="dash-tabs" role="tablist" aria-label="Period">
          {PRESETS.map((p) => (
            <button key={p.value} type="button" role="tab" aria-selected={range.preset === p.value}
                    className={range.preset === p.value ? 'on' : ''}
                    onClick={() => { setRange({ ...range, preset: p.value }); setPage(0) }}>{p.label.replace(' range', '')}</button>
          ))}
        </div>
        {range.preset === 'custom' && (
          <>
            <label className="inline-label">From
              <input type="date" value={range.from} max={range.to || undefined}
                     onChange={(e) => { setRange({ ...range, from: e.target.value }); setPage(0) }} />
            </label>
            <label className="inline-label">To
              <input type="date" value={range.to} min={range.from || undefined}
                     onChange={(e) => { setRange({ ...range, to: e.target.value }); setPage(0) }} />
            </label>
          </>
        )}
        <span className="rep-period muted">{describeRange(resolved)}</span>
      </div>

      {resolved.error && <p className="error" role="alert">{resolved.error}</p>}
      {error && <p className="error" role="alert">{error}</p>}

      {summary && (
        <div className="rep-hero exp-hero">
          <div className="net-hero stacked loss">
            <span className="net-hero-label">Total expenses</span>
            <span className="net-hero-value">{money(summary.total)}</span>
            {compare && previous && <Change now={summary.total} before={previous.total} compare={compare} better="down" />}
            <span className="net-hero-sub">
              {summary.count} expense{summary.count === 1 ? '' : 's'}
              {biggest && Number(biggest.total) > 0 && <> · biggest: {EXPENSE_LABEL[biggest.type]} {money(biggest.total)}</>}
            </span>
          </div>
          <TypeBreakdown summary={summary} />
        </div>
      )}

      <div className="rep-bar">
        <input type="search" className="prod-search" value={search} placeholder="Search description or order no."
               aria-label="Search expenses by description or order number"
               onChange={(e) => { setSearch(e.target.value); setPage(0) }} />
        <div className="dash-tabs" role="tablist" aria-label="Expense type">
          {[['', 'All'], ...EXPENSE_TYPES.map((t) => [t, EXPENSE_SHORT[t]])].map(([value, label]) => (
            <button key={value} type="button" role="tab" aria-selected={type === value}
                    className={type === value ? 'on' : ''} onClick={() => { setType(value); setPage(0) }}>{label}</button>
          ))}
        </div>
      </div>

      <div className="dash-card prod-table">
        <table>
          <thead>
            <tr><th>Date</th><th>Type</th><th>Description</th><th>Order</th><th className="num">Amount</th><th /></tr>
          </thead>
          <tbody>
            {data?.content.map((e) => (
              <tr key={e.id} className="clickable" title="Click for the expense's details"
                  onClick={(ev) => { if (!ev.target.closest('a, button')) setViewing(e) }}>
                <td className="stk-when"><b>{longDay(e.expenseDate)}</b><small>{weekday(e.expenseDate)}</small></td>
                <td>
                  <span className="exp-type" style={{ '--c': TYPE_COLOR[e.type] }}>{EXPENSE_LABEL[e.type]}</span>
                  {e.autoGenerated && <small className="muted stk-auto" title="Calculated automatically from the platform's commission rate">auto</small>}
                </td>
                <td className="exp-desc">{e.description || <span className="muted">—</span>}</td>
                <td>{e.orderId ? <Link to={`/orders/${e.orderId}`}>{e.orderNo ?? `#${e.orderId}`}{e.platformName ? ` · ${e.platformName}` : ''}</Link> : <span className="muted">—</span>}</td>
                <td className="num exp-amt">{money(e.amount)}</td>
                <td className="row-actions">
                  {e.autoGenerated ? (
                    <span className="muted">calculated</span>
                  ) : (
                    <>
                      <button className="link" onClick={() => setEditing(e)}>Edit</button>
                      <button className="link danger" onClick={() => remove(e)}>Delete</button>
                    </>
                  )}
                </td>
              </tr>
            ))}
            {data && data.content.length === 0 && (
              <tr><td colSpan={6} className="empty">{dq || type ? 'No expenses match these filters.' : 'No expenses in this period.'}</td></tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager data={data} onPage={setPage} />

      {viewing && (
        <Modal title="Expense details" onClose={() => setViewing(null)}>
          <div className="od-totals">
            <span>Date</span><span className="num">{viewing.expenseDate}</span>
            <span>Type</span><span className="num">{EXPENSE_LABEL[viewing.type]}{viewing.autoGenerated ? ' (calculated automatically)' : ''}</span>
            <span>Amount</span><span className="num">{money(viewing.amount)}</span>
            <span>Order</span>
            <span className="num">{viewing.orderId
              ? <Link to={`/orders/${viewing.orderId}`}>{viewing.orderNo ?? `#${viewing.orderId}`}{viewing.platformName ? ` · ${viewing.platformName}` : ''}</Link>
              : '—'}</span>
          </div>
          <h3>Description</h3>
          <p style={{ overflowWrap: 'anywhere', whiteSpace: 'pre-wrap' }}>{viewing.description || '—'}</p>
          <div className="actions">
            <button type="button" className="btn secondary" onClick={() => setViewing(null)}>Close</button>
          </div>
        </Modal>
      )}

      {editing && (
        <Modal title={editing.id ? 'Edit expense' : 'Add expense'} onClose={closeForm}>
          <ExpenseForm
            expense={editing.id ? editing : null}
            orderId={editing.orderId}
            onSaved={() => { closeForm(); load() }}
            onCancel={closeForm}
          />
        </Modal>
      )}
    </section>
  )
}
