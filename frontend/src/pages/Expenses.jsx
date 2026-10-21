import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import Modal from '../components/Modal'
import Pager from '../components/Pager'
import RangePicker from '../components/RangePicker'
import Stat from '../components/Stat'
import { describeRange, resolveRange, toDateStr } from '../utils/dateRange'
import { EXPENSE_LABEL, EXPENSE_TYPES, money } from '../utils/format'

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

export default function Expenses() {
  const [params, setParams] = useSearchParams()
  const [range, setRange] = useState({ preset: 'this_month', from: '', to: '' })
  const [type, setType] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [summary, setSummary] = useState(null)
  const [error, setError] = useState('')
  const [viewing, setViewing] = useState(null)
  // ?newFor=<order id> (from the orders screens) opens the form already linked to that order
  const [editing, setEditing] = useState(() => (params.get('newFor') ? { orderId: params.get('newFor') } : null))

  const resolved = useMemo(() => resolveRange(range), [range])

  const load = useCallback(() => {
    if (resolved.error) return
    const dates = { from: resolved.from, to: resolved.to }
    Promise.all([
      api.get('/expenses', { params: { ...dates, type: type || undefined, page } }),
      api.get('/expenses/summary', { params: dates }),
    ])
      .then(([list, sum]) => { setData(list.data); setSummary(sum.data); setError('') })
      .catch((err) => setError(errorMessage(err)))
  }, [resolved, type, page])
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

  return (
    <section>
      <div className="page-head">
        <h1>Expenses</h1>
        <button className="btn" onClick={() => setEditing({})}>+ Add expense</button>
      </div>

      <div className="filters">
        <RangePicker value={range} onChange={(r) => { setRange(r); setPage(0) }} />
        <select aria-label="Expense type" value={type} onChange={(e) => { setType(e.target.value); setPage(0) }}>
          <option value="">All types</option>
          {EXPENSE_TYPES.map((t) => <option key={t} value={t}>{EXPENSE_LABEL[t]}</option>)}
        </select>
      </div>

      {resolved.error && <p className="error" role="alert">{resolved.error}</p>}
      {error && <p className="error" role="alert">{error}</p>}

      {summary && (
        <>
          <p className="muted period">{describeRange(resolved)} · all expense types</p>
          <div className="stats">
            <Stat label="Total expenses" value={money(summary.total)} tone="neg" hint={`${summary.count} expense${summary.count === 1 ? '' : 's'}`} />
            {summary.byType.map((t) => (
              <Stat key={t.type} label={EXPENSE_LABEL[t.type]} value={money(t.total)} hint={`${t.count} entr${t.count === 1 ? 'y' : 'ies'}`} />
            ))}
          </div>
        </>
      )}

      <div className="table-wrap spaced">
        <table>
          <thead>
            <tr><th>Date</th><th>Type</th><th>Description</th><th>Order</th><th className="num">Amount</th><th /></tr>
          </thead>
          <tbody>
            {data?.content.map((e) => (
              <tr key={e.id} className="clickable" title="Click for the expense's details"
                  onClick={(ev) => { if (!ev.target.closest('a, button')) setViewing(e) }}>
                <td>{e.expenseDate}</td>
                <td>{EXPENSE_LABEL[e.type]}{e.autoGenerated && <> <span className="badge auto" title="Calculated automatically from the platform's commission rate">Auto</span></>}</td>
                <td className="notes">{e.description || '—'}</td>
                <td>{e.orderId ? <Link to={`/orders/${e.orderId}`}>{e.orderNo ?? `#${e.orderId}`}{e.platformName ? ` · ${e.platformName}` : ''}</Link> : '—'}</td>
                <td className="num">{money(e.amount)}</td>
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
              <tr><td colSpan={6} className="empty">No expenses in this period.</td></tr>
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
