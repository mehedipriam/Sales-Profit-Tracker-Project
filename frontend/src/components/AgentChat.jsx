import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { STATUSES, STATUS_LABEL, money } from '../utils/format'
import AsyncPicker from './AsyncPicker'
import { loadChat, saveChat } from './agentStore'

const searchProducts = (q) => api.get('/products', { params: { q, size: 10 } }).then((r) => r.data.content)

let uid = 0
const nextKey = () => ++uid

const HELLO = 'Paste an order message from Facebook and I will prepare it for you. Nothing is saved until you press Confirm.'

/** The draft the server sent, as editable form state (inputs hold text). */
const toState = (d) => ({
  customer: {
    id: d.customer.id, isNew: d.customer.isNew,
    name: d.customer.name ?? '', phone: d.customer.phone ?? '', address: d.customer.address ?? '',
  },
  platformId: d.platformId ? String(d.platformId) : '',
  status: d.status,
  deliveryCharge: Number(d.deliveryCharge) > 0 ? String(d.deliveryCharge) : '',
  notes: d.notes ?? '',
  lines: d.items.map((l) => ({
    key: nextKey(), text: l.text, productId: l.productId, name: l.productName,
    quantity: String(l.quantity), soldPrice: l.soldPrice == null ? '' : String(l.soldPrice),
    candidates: l.candidates ?? [],
  })),
  // Errors are worked out again from what is on screen as it is fixed; only the warnings come from the server.
  warnings: d.issues.filter((i) => i.level === 'WARNING').map((i) => i.message),
})

const price = (l) => Number(l.soldPrice) || 0
const qty = (l) => Number(l.quantity) || 0
const total = (s) => s.lines.reduce((sum, l) => sum + qty(l) * price(l), 0) + (Number(s.deliveryCharge) || 0)

/** What still stops this draft from being confirmed. */
function blockers(s) {
  const out = []
  if (s.customer.isNew && !s.customer.name.trim()) out.push("Enter the customer's name")
  if (!s.platformId) out.push('Choose a platform')
  if (s.lines.length === 0) out.push('Add at least one product')
  for (const l of s.lines) {
    if (!l.productId) out.push(`Choose the product for "${l.text}"`)
    else if (!Number.isInteger(qty(l)) || qty(l) < 1) out.push(`Check the quantity of ${l.name}`)
    else if (l.soldPrice === '' || !(Number(l.soldPrice) >= 0)) out.push(`Enter the price of ${l.name}`)
  }
  return out
}

/** The draft as the ordinary order request the server expects. */
const toRequest = (s) => ({
  platformId: Number(s.platformId),
  ...(s.customer.isNew
    ? { newCustomer: { name: s.customer.name.trim(), phone: s.customer.phone.trim() || null, address: s.customer.address.trim() || null } }
    : { customerId: s.customer.id }),
  status: s.status,
  deliveryCharge: Number(s.deliveryCharge) || 0,
  notes: s.notes.trim() || null,
  items: s.lines.map((l) => ({ productId: l.productId, quantity: qty(l), soldPrice: price(l) })),
})

function DraftCard({ message, platforms, onEdit, onConfirm, onDiscard }) {
  const { draft: s, status, error } = message
  const open = status === 'open' || status === 'saving'
  const issues = blockers(s)
  const set = (patch) => onEdit((d) => ({ ...d, ...patch }))
  const setCustomer = (patch) => onEdit((d) => ({ ...d, customer: { ...d.customer, ...patch } }))
  const setLine = (key, patch) => onEdit((d) => ({ ...d, lines: d.lines.map((l) => (l.key === key ? { ...l, ...patch } : l)) }))
  const choose = (line, p) => setLine(line.key, {
    productId: p.id, name: p.name, candidates: [],
    soldPrice: line.soldPrice === '' && p.sellingPrice != null ? String(p.sellingPrice) : line.soldPrice,
  })

  if (status === 'done') {
    return (
      <div className="agent-card agent-done">
        <b>Order {message.order.orderNo} created</b>
        <span>{message.order.customerName} · {money(Number(message.order.revenue) + Number(message.order.deliveryCharge))}</span>
        <Link to={`/orders/${message.order.id}`}>Open the order</Link>
      </div>
    )
  }
  if (status === 'discarded') return <div className="agent-card muted">Draft discarded.</div>

  return (
    <div className="agent-card">
      <div className="agent-block">
        {s.customer.isNew ? (
          <>
            <span className="agent-tag">New customer</span>
            <input aria-label="Customer name" placeholder="Name" value={s.customer.name} disabled={!open}
                   onChange={(e) => setCustomer({ name: e.target.value })} />
            <input aria-label="Customer phone" placeholder="Phone" value={s.customer.phone} disabled={!open}
                   onChange={(e) => setCustomer({ phone: e.target.value })} />
            <input aria-label="Customer address" placeholder="Address" value={s.customer.address} disabled={!open}
                   onChange={(e) => setCustomer({ address: e.target.value })} />
          </>
        ) : (
          <>
            <span className="agent-tag found">Existing customer</span>
            <b>{s.customer.name}</b>
            <span className="muted">{[s.customer.phone, s.customer.address].filter(Boolean).join(' · ')}</span>
          </>
        )}
      </div>

      <div className="agent-block">
        <div className="agent-row">
          <label>Platform
            <select value={s.platformId} disabled={!open} onChange={(e) => set({ platformId: e.target.value })}>
              {!s.platformId && <option value="">Choose…</option>}
              {platforms.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
          </label>
          <label>Status
            <select value={s.status} disabled={!open} onChange={(e) => set({ status: e.target.value })}>
              {STATUSES.map((st) => <option key={st} value={st}>{STATUS_LABEL[st]}</option>)}
            </select>
          </label>
        </div>
      </div>

      <div className="agent-block">
        {s.lines.map((l) => (
          <div key={l.key} className="agent-line">
            {l.productId ? (
              <b className="agent-name">{l.name}</b>
            ) : (
              <div className="agent-unmatched">
                <span>Which product is "{l.text}"?</span>
                {l.candidates.length > 0 && (
                  <div className="agent-choices">
                    {l.candidates.map((c) => (
                      <button key={c.id} type="button" className="btn secondary small" disabled={!open} onClick={() => choose(l, c)}>
                        {c.name}
                      </button>
                    ))}
                  </div>
                )}
                {open && (
                  <AsyncPicker search={searchProducts} placeholder="Search products…" onPick={(p) => choose(l, p)}
                               label={(p) => `${p.name} · ${money(p.sellingPrice)}`} />
                )}
              </div>
            )}
            <div className="agent-nums">
              <input type="number" min="1" step="1" aria-label="Quantity" value={l.quantity} disabled={!open}
                     onChange={(e) => setLine(l.key, { quantity: e.target.value })} />
              <span>×</span>
              <input type="number" min="0" step="0.01" aria-label="Price" value={l.soldPrice} disabled={!open}
                     onChange={(e) => setLine(l.key, { soldPrice: e.target.value })} />
              <button type="button" className="link danger" aria-label={`Remove ${l.name ?? l.text}`} disabled={!open}
                      onClick={() => set({ lines: s.lines.filter((x) => x.key !== l.key) })}>✕</button>
            </div>
          </div>
        ))}
        {s.lines.length === 0 && <span className="muted">No products.</span>}
        {s.lines.length > 0 && open && s.lines.every((l) => l.productId) && (
          <AsyncPicker search={searchProducts} placeholder="Add another product…" label={(p) => `${p.name} · ${money(p.sellingPrice)}`}
                       onPick={(p) => set({ lines: [...s.lines, { key: nextKey(), text: p.name, productId: p.id, name: p.name, quantity: '1', soldPrice: String(p.sellingPrice ?? ''), candidates: [] }] })} />
        )}
      </div>

      <div className="agent-block">
        <div className="agent-row">
          <label>Delivery charge
            <input type="number" min="0" step="0.01" value={s.deliveryCharge} disabled={!open}
                   onChange={(e) => set({ deliveryCharge: e.target.value })} />
          </label>
          <div className="agent-total"><span className="muted">Customer pays</span><b>{money(total(s))}</b></div>
        </div>
        {s.notes && <span className="muted">Note: {s.notes}</span>}
      </div>

      {s.warnings.length > 0 && (
        <ul className="agent-warnings">{s.warnings.map((w) => <li key={w}>{w}</li>)}</ul>
      )}
      {open && issues.length > 0 && (
        <ul className="agent-errors">{issues.map((i) => <li key={i}>{i}</li>)}</ul>
      )}
      {error && <p className="error" role="alert">{error}</p>}

      <div className="actions">
        <button type="button" className="btn secondary small" disabled={status === 'saving'} onClick={onDiscard}>Cancel</button>
        <button type="button" className="btn small" disabled={!open || status === 'saving' || issues.length > 0} onClick={onConfirm}>
          {status === 'saving' ? 'Saving…' : 'Confirm'}
        </button>
      </div>
    </div>
  )
}

/** The floating order assistant: paste a message, check the draft, press Confirm. */
export default function AgentChat() {
  const { user } = useAuth()
  // Picked up again after a reload (same tab, same user), so a draft in progress is not lost.
  const [saved] = useState(() => {
    const chat = loadChat(user.id)
    uid = Math.max(uid, chat.maxKey)
    return chat
  })
  const [open, setOpen] = useState(saved.open)
  const [messages, setMessages] = useState(saved.messages)
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [platforms, setPlatforms] = useState([])
  const endRef = useRef(null)
  const inputRef = useRef(null)

  useEffect(() => {
    if (!open) return undefined
    api.get('/platforms').then((r) => setPlatforms(r.data)).catch(() => {})
    inputRef.current?.focus()
    const onKey = (e) => e.key === 'Escape' && setOpen(false)
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  useEffect(() => { saveChat(user.id, open, messages) }, [user.id, open, messages])

  useEffect(() => { endRef.current?.scrollIntoView?.({ block: 'end' }) }, [messages, busy, open])

  const patch = (key, change) => setMessages((ms) => ms.map((m) => (m.key === key ? { ...m, ...change(m) } : m)))

  const send = async () => {
    const msg = text.trim()
    if (!msg || busy) return
    setText('')
    setBusy(true)
    setMessages((ms) => [...ms, { key: nextKey(), role: 'user', text: msg }])
    try {
      const { data } = await api.post('/agent/draft', { text: msg })
      setMessages((ms) => [...ms, { key: nextKey(), role: 'agent', draft: toState(data), status: 'open' }])
    } catch (err) {
      setMessages((ms) => [...ms, { key: nextKey(), role: 'agent', text: errorMessage(err), failed: true }])
    } finally {
      setBusy(false)
    }
  }

  const confirm = async (m) => {
    patch(m.key, () => ({ status: 'saving', error: '' }))
    try {
      const { data } = await api.post('/agent/confirm', toRequest(m.draft))
      patch(m.key, () => ({ status: 'done', order: data }))
    } catch (err) {
      patch(m.key, () => ({ status: 'open', error: errorMessage(err) }))
    }
  }

  const onKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      send()
    }
  }

  return (
    <>
      {open && (
        <section className="agent-panel" role="dialog" aria-label="Order assistant">
          <header className="agent-head">
            <b>Order assistant</b>
            <button type="button" className="link" aria-label="Close" onClick={() => setOpen(false)}>✕</button>
          </header>
          <div className="agent-body">
            <p className="agent-bubble agent">{HELLO}</p>
            {messages.map((m) => (m.draft ? (
              <DraftCard key={m.key} message={m} platforms={platforms}
                         onEdit={(fn) => patch(m.key, (cur) => ({ draft: fn(cur.draft) }))}
                         onConfirm={() => confirm(m)}
                         onDiscard={() => patch(m.key, () => ({ status: 'discarded' }))} />
            ) : (
              <p key={m.key} className={`agent-bubble ${m.role}${m.failed ? ' failed' : ''}`}>{m.text}</p>
            )))}
            {busy && <p className="agent-bubble agent muted">Reading the message…</p>}
            <div ref={endRef} />
          </div>
          <div className="agent-input">
            <textarea ref={inputRef} rows={2} value={text} placeholder="Paste or type the order…" maxLength={4000}
                      aria-label="Order message" onChange={(e) => setText(e.target.value)} onKeyDown={onKeyDown} />
            <button type="button" className="btn small" disabled={busy || !text.trim()} onClick={send}>Send</button>
          </div>
        </section>
      )}
      <button type="button" className="agent-fab" aria-label={open ? 'Close order assistant' : 'Open order assistant'}
              aria-expanded={open} title="Order assistant" onClick={() => setOpen((o) => !o)}>
        <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="1.8"
             strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          {open ? <path d="M6 6l12 12M18 6L6 18" /> : <path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z" />}
        </svg>
      </button>
    </>
  )
}
