import { Link } from 'react-router-dom'
import { dateTime, maskPhone, money } from '../utils/format'
import { MASK, useReveal } from './Reveal'

/**
 * A saved customer's details. The phone number and address sit behind an eye, like on the Customers page, and so does
 * what they spent (sent for Owner and Admin only).
 */
export function CustomerInfoCard({ customer: c }) {
  const { shown, eye, prompt } = useReveal()
  return (
    <div className="agent-card">
      <div className="agent-answer-head">
        <b>{c.name}</b>
        <span>{eye('contact', 'phone and address')}</span>
      </div>
      <dl className="agent-facts">
        <dt>Phone</dt><dd>{shown.contact ? c.phone || '—' : maskPhone(c.phone)}</dd>
        <dt>Address</dt><dd>{shown.contact ? c.address || '—' : c.address ? MASK : '—'}</dd>
        {c.platform && <><dt>Came from</dt><dd>{c.platform}</dd></>}
        <dt>Orders</dt><dd>{c.orders}</dd>
        <dt>Last order</dt><dd>{c.lastOrderAt ? dateTime(c.lastOrderAt) : 'No orders yet'}</dd>
        {c.spent != null && (
          <>
            <dt>Spent {eye('spent', 'what they spent')}</dt>
            <dd>{shown.spent ? money(c.spent) : MASK}</dd>
          </>
        )}
      </dl>
      {prompt}
    </div>
  )
}

/**
 * A change waiting for Confirm. A new customer is a small form (the details the message gave are filled in, anything
 * can be corrected); a change to a saved customer is read-only, showing each field before and after.
 */
export function CustomerProposalCard({ message, platforms, onEdit, onConfirm, onDiscard }) {
  const { proposal: p, status, form, error, saved } = message
  const { shown, eye, prompt } = useReveal()
  const busy = status === 'saving'
  const open = status === 'open' || busy

  if (status === 'done') {
    return (
      <div className="agent-card agent-done">
        <b>{p.kind === 'ADD' ? `${saved.name} added` : `${saved.name} updated`}</b>
        <Link to="/customers">Open Customers</Link>
      </div>
    )
  }
  if (status === 'discarded') return <div className="agent-card muted">Cancelled. Nothing was saved.</div>

  const set = (patch) => onEdit((f) => ({ ...f, ...patch }))
  // Old phone numbers and addresses stay behind the eye; what the user just typed is theirs to see.
  const before = (f) => (f.before == null ? '—' : f.key === 'phone' || f.key === 'address'
    ? (shown.contact ? f.before : f.key === 'phone' ? maskPhone(f.before) : MASK) : f.before)

  return (
    <div className="agent-card">
      {p.kind === 'ADD' ? (
        <>
          <span className="agent-tag">New customer</span>
          <input aria-label="Customer name" placeholder="Name" value={form.name} disabled={!open || busy}
                 onChange={(e) => set({ name: e.target.value })} />
          <input aria-label="Customer phone" placeholder="Phone" value={form.phone} disabled={busy}
                 onChange={(e) => set({ phone: e.target.value })} />
          <input aria-label="Customer address" placeholder="Address" value={form.address} disabled={busy}
                 onChange={(e) => set({ address: e.target.value })} />
          <label>Came from
            <select value={form.platformId} disabled={busy} onChange={(e) => set({ platformId: e.target.value })}>
              <option value="">Not set</option>
              {platforms.map((pl) => <option key={pl.id} value={pl.id}>{pl.name}</option>)}
            </select>
          </label>
        </>
      ) : (
        <>
          <div className="agent-answer-head">
            <div><span className="agent-tag found">Change customer</span><b>{p.customerName}</b></div>
            <span>{eye('contact', 'the old phone and address')}</span>
          </div>
          <table className="agent-table">
            <thead><tr><th>Field</th><th>Before</th><th>After</th></tr></thead>
            <tbody>
              {p.fields.map((f) => (
                <tr key={f.key}><td>{f.label}</td><td className="muted">{before(f)}</td><td><b>{f.after}</b></td></tr>
              ))}
            </tbody>
          </table>
          <span className="muted">Only these fields change. Everything else stays as it is.</span>
        </>
      )}
      {p.warnings.length > 0 && <ul className="agent-warnings">{p.warnings.map((w) => <li key={w}>{w}</li>)}</ul>}
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button type="button" className="btn secondary small" disabled={busy} onClick={onDiscard}>Cancel</button>
        <button type="button" className="btn small" disabled={busy || (p.kind === 'ADD' && !form.name.trim())} onClick={onConfirm}>
          {busy ? 'Saving…' : 'Confirm'}
        </button>
      </div>
      {prompt}
    </div>
  )
}
