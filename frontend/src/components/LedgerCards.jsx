import { Link } from 'react-router-dom'
import { EXPENSE_LABEL, STOCK_REASON_LABEL, money } from '../utils/format'

const date = (iso) => new Date(`${iso}T00:00:00`).toLocaleDateString('en-GB', { dateStyle: 'medium' })

/** The Confirm / Cancel row every card ends with. */
function Actions({ busy, onConfirm, onDiscard }) {
  return (
    <div className="actions">
      <button type="button" className="btn secondary small" disabled={busy} onClick={onDiscard}>Cancel</button>
      <button type="button" className="btn small" disabled={busy} onClick={onConfirm}>{busy ? 'Saving…' : 'Confirm'}</button>
    </div>
  )
}

/** An expense waiting for Confirm: type, amount, day, what it was for, and the order it belongs to if any. */
export function ExpenseCard({ message, onConfirm, onDiscard }) {
  const { expense: e, status, error } = message
  if (status === 'done') {
    return (
      <div className="agent-card agent-done">
        <b>{EXPENSE_LABEL[e.type]} expense of {money(e.amount)} recorded</b>
        <Link to="/expenses">Open Expenses</Link>
      </div>
    )
  }
  if (status === 'discarded') return <div className="agent-card muted">Cancelled. Nothing was recorded.</div>
  return (
    <div className="agent-card">
      <div className="agent-block">
        <span className="agent-tag">New expense</span>
        <dl className="agent-facts">
          <dt>Type</dt><dd>{EXPENSE_LABEL[e.type]}</dd>
          <dt>Amount</dt><dd><b>{money(e.amount)}</b></dd>
          <dt>Date</dt><dd>{date(e.date)}</dd>
          {e.description && <><dt>For</dt><dd>{e.description}</dd></>}
          {e.orderNo && <><dt>Order</dt><dd>{e.orderNo} · {e.customerName}</dd></>}
        </dl>
      </div>
      {e.warnings.length > 0 && <ul className="agent-warnings">{e.warnings.map((w) => <li key={w}>{w}</li>)}</ul>}
      {error && <p className="error" role="alert">{error}</p>}
      <Actions busy={status === 'saving'} onConfirm={onConfirm} onDiscard={onDiscard} />
    </div>
  )
}

/** A stock change waiting for Confirm: the product, why, and its stock before and after. */
export function StockCard({ message, onConfirm, onDiscard }) {
  const { stock: s, status, error } = message
  const sign = s.change > 0 ? `+${s.change}` : String(s.change)
  if (status === 'done') {
    return (
      <div className="agent-card agent-done">
        <b>{s.productName}: stock is now {s.after}</b>
        <Link to="/stock">Open Stock</Link>
      </div>
    )
  }
  if (status === 'discarded') return <div className="agent-card muted">Cancelled. The stock was not changed.</div>
  return (
    <div className="agent-card">
      <div className="agent-block">
        <span className="agent-tag found">{STOCK_REASON_LABEL[s.reason]}</span>
        <b>{s.productName}</b>
        <div className="agent-status-move">
          <span>{s.before}</span>
          <span aria-hidden="true">→</span>
          <b className={s.change > 0 ? 'pos' : 'neg'}>{s.after}</b>
          <span className="muted">({sign})</span>
        </div>
      </div>
      {error && <p className="error" role="alert">{error}</p>}
      <Actions busy={status === 'saving'} onConfirm={onConfirm} onDiscard={onDiscard} />
    </div>
  )
}
