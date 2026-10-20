import { Link } from 'react-router-dom'
import { STATUS_LABEL, money } from '../utils/format'

/**
 * An order's status change waiting for Confirm: the order, "Pending to Returned", and what that does to stock and
 * profit. Nothing changes until Confirm.
 */
export default function StatusCard({ message, onConfirm, onDiscard }) {
  const { statusChange: c, status, error } = message
  const busy = status === 'saving'

  if (status === 'done') {
    return (
      <div className="agent-card agent-done">
        <b>{c.orderNo} is now {STATUS_LABEL[c.to]}</b>
        <Link to={`/orders/${c.orderId}`}>Open the order</Link>
      </div>
    )
  }
  if (status === 'discarded') return <div className="agent-card muted">Cancelled. The order was not changed.</div>

  return (
    <div className="agent-card">
      <div className="agent-block">
        <span className="agent-tag found">Change order status</span>
        <b><Link to={`/orders/${c.orderId}`}>{c.orderNo}</Link> · {c.customerName}</b>
        <span className="muted">{c.items.join(', ')}</span>
        <span className="muted">Customer pays {money(c.total)}</span>
      </div>
      <div className="agent-block">
        <div className="agent-status-move">
          <span className={`agent-status ${c.from}`}>{STATUS_LABEL[c.from]}</span>
          <span aria-hidden="true">→</span>
          <span className={`agent-status ${c.to}`}>{STATUS_LABEL[c.to]}</span>
        </div>
        <span>{c.effect}</span>
      </div>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button type="button" className="btn secondary small" disabled={busy} onClick={onDiscard}>Cancel</button>
        <button type="button" className="btn small" disabled={busy} onClick={onConfirm}>{busy ? 'Saving…' : 'Confirm'}</button>
      </div>
    </div>
  )
}
