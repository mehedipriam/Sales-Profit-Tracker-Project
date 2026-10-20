import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { seesFinancials } from '../auth/roles'
import { STATUSES, STATUS_LABEL, money } from '../utils/format'
import { prepareImage } from '../utils/image'
import AnswerCard from './AnswerCard'
import AsyncPicker from './AsyncPicker'
import { CustomerInfoCard, CustomerProposalCard } from './CustomerCards'
import { ExpenseCard, StockCard } from './LedgerCards'
import StatusCard from './StatusCard'
import { loadChat, saveChat } from './agentStore'

const searchProducts = (q) => api.get('/products', { params: { q, size: 10 } }).then((r) => r.data.content)

let uid = 0
const nextKey = () => ++uid

const HELLO = 'Paste an order message from Facebook, or attach a screenshot of the conversation, and I will prepare it for you. Nothing is saved until you press Confirm. You can also ask me about your business, add, find or change a customer, change the status of an order, record an expense, or restock a product.'

/** What to try on an empty chat. Profit questions are for Owner and Admin only. */
const SUGGESTIONS = [
  { text: "This month's profit", money: true },
  { text: "Last month's profit", money: true },
  { text: 'Best-selling products this month', money: true },
  { text: 'Top customers this month', money: true },
  { text: 'How many orders this month?', money: false },
  { text: 'What is running low on stock?', money: false },
  { text: 'Add a customer', money: false },
  { text: 'Expenses this month', money: true },
  { text: 'Lowest-margin products', money: true },
]

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

/** The fields of a new-customer proposal as form state (inputs hold text). */
const toForm = (p) => {
  const value = (key) => p.fields.find((f) => f.key === key)?.after ?? ''
  return { name: value('name'), phone: value('phone'), address: value('address'), platformId: p.platformId ? String(p.platformId) : '' }
}

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

/** The draft as the revise endpoint expects it (the same fields, numbers as numbers). */
const toRevisionDraft = (s) => ({
  customer: s.customer,
  platformId: s.platformId ? Number(s.platformId) : null,
  status: s.status,
  deliveryCharge: Number(s.deliveryCharge) || 0,
  notes: s.notes || null,
  lines: s.lines.map((l) => ({
    text: l.text, productId: l.productId, quantity: Number(l.quantity) || null,
    soldPrice: l.soldPrice === '' ? null : Number(l.soldPrice),
  })),
})

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

function DraftCard({ message, platforms, onEdit, onConfirm, onDiscard, onRevise }) {
  const { draft: s, status, error, reply, revising } = message
  const [change, setChange] = useState('')
  const open = (status === 'open' || status === 'saving') && !revising
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

      {(status === 'open' || revising) && (
        <div className="agent-revise">
          {reply && <p className="agent-reply">{reply}</p>}
          <form onSubmit={(e) => {
            e.preventDefault()
            const text = change.trim()
            if (!text || revising) return
            setChange('')
            onRevise(text)
          }}>
            <input aria-label="Change something in this draft" placeholder="Change something… e.g. make it 3" maxLength={1000}
                   value={change} disabled={!open} onChange={(e) => setChange(e.target.value)} />
            <button type="submit" className="btn secondary small" disabled={!open || !change.trim()}>
              {revising ? 'Working…' : 'Change'}
            </button>
          </form>
        </div>
      )}

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
  const [shot, setShot] = useState(null) // the screenshot waiting to be sent: { blob, thumb }
  const [shotError, setShotError] = useState('')
  const fileRef = useRef(null)
  const suggestions = SUGGESTIONS.filter((x) => !x.money || seesFinancials(user)).map((x) => x.text)
  const endRef = useRef(null)
  const inputRef = useRef(null)

  useEffect(() => {
    if (!open) return undefined
    api.get('/platforms').then((r) => setPlatforms(r.data)).catch(() => {})
    inputRef.current?.focus()
    // Escape closes the assistant, unless a dialog (such as the password box of an eye) is open: that closes first.
    const onKey = (e) => e.key === 'Escape' && !document.querySelector('.overlay') && setOpen(false)
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  useEffect(() => { saveChat(user.id, open, messages) }, [user.id, open, messages])

  useEffect(() => { endRef.current?.scrollIntoView?.({ block: 'end' }) }, [messages, busy, open])

  const patch = (key, change) => setMessages((ms) => ms.map((m) => (m.key === key ? { ...m, ...change(m) } : m)))

  const attach = async (file) => {
    setShotError('')
    try {
      setShot(await prepareImage(file))
    } catch (err) {
      setShotError(err.message)
    }
  }

  const imageIn = (files) => Array.from(files ?? []).find((f) => f.type.startsWith('image/'))

  const onPaste = (e) => {
    const file = imageIn(e.clipboardData?.files)
    if (!file) return
    e.preventDefault() // a pasted picture is a screenshot to read, not text
    attach(file)
  }

  const onDrop = (e) => {
    const file = imageIn(e.dataTransfer?.files)
    if (!file) return
    e.preventDefault()
    attach(file)
  }

  const send = async (typed) => {
    const msg = (typeof typed === 'string' ? typed : text).trim()
    if ((!msg && !shot) || busy) return
    const sent = shot
    setText('')
    setShot(null)
    setShotError('')
    setBusy(true)
    setMessages((ms) => [...ms, { key: nextKey(), role: 'user', text: msg, ...(sent ? { screenshot: true, thumb: sent.thumb } : {}) }])
    try {
      let data
      if (sent) {
        const form = new FormData()
        form.append('image', sent.blob, 'screenshot.jpg')
        if (msg) form.append('text', msg)
        data = (await api.post('/agent/draft/image', form)).data
      } else {
        const reply = (await api.post('/agent/message', { text: msg })).data
        if (reply.type === 'ANSWER') {
          setMessages((ms) => [...ms, { key: nextKey(), role: 'agent', answer: reply.answer }])
        } else if (reply.type === 'CUSTOMER') {
          setMessages((ms) => [...ms, { key: nextKey(), role: 'agent', customer: reply.customer }])
        } else if (reply.type === 'EXPENSE') {
          setMessages((ms) => [...ms, { key: nextKey(), role: 'agent', expense: reply.expense, status: 'open' }])
        } else if (reply.type === 'STOCK') {
          setMessages((ms) => [...ms, { key: nextKey(), role: 'agent', stock: reply.stock, status: 'open' }])
        } else if (reply.type === 'STATUS') {
          setMessages((ms) => [...ms, { key: nextKey(), role: 'agent', statusChange: reply.statusChange, status: 'open' }])
        } else if (reply.type === 'PROPOSAL') {
          setMessages((ms) => [...ms, { key: nextKey(), role: 'agent', proposal: reply.proposal, status: 'open', form: toForm(reply.proposal) }])
        } else if (reply.type === 'MESSAGE') {
          setMessages((ms) => [...ms, { key: nextKey(), role: 'agent', text: reply.message }])
        } else {
          setMessages((ms) => [...ms, { key: nextKey(), role: 'agent', draft: toState(reply.draft), status: 'open' }])
        }
        return
      }
      setMessages((ms) => [...ms, { key: nextKey(), role: 'agent', draft: toState(data), status: 'open' }])
    } catch (err) {
      setMessages((ms) => [...ms, { key: nextKey(), role: 'agent', text: errorMessage(err), failed: true }])
    } finally {
      setBusy(false)
    }
  }

  const revise = async (m, instruction) => {
    patch(m.key, () => ({ revising: true, error: '' }))
    try {
      const { data } = await api.post('/agent/revise', { instruction, draft: toRevisionDraft(m.draft) })
      patch(m.key, () => ({ draft: toState(data.draft), reply: data.reply, revising: false }))
    } catch (err) {
      patch(m.key, () => ({ revising: false, error: errorMessage(err) }))
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

  /** Confirm on a customer card: a new customer is created, a change saves only the fields shown. */
  const confirmCustomer = async (m) => {
    patch(m.key, () => ({ status: 'saving', error: '' }))
    try {
      const p = m.proposal
      let data
      if (p.kind === 'ADD') {
        const f = m.form
        data = (await api.post('/agent/customers', {
          name: f.name.trim(), phone: f.phone.trim() || null, address: f.address.trim() || null,
          sourcePlatformId: f.platformId ? Number(f.platformId) : null,
        })).data
      } else {
        const body = {}
        for (const f of p.fields) {
          if (f.key === 'platform') body.sourcePlatformId = p.platformId
          else body[f.key] = f.after
        }
        data = (await api.patch(`/agent/customers/${p.customerId}`, body)).data
      }
      patch(m.key, () => ({ status: 'done', saved: data }))
    } catch (err) {
      patch(m.key, () => ({ status: 'open', error: errorMessage(err) }))
    }
  }

  /** Confirm on a status card: the order must still be in the status the card showed. */
  const confirmStatus = async (m) => {
    patch(m.key, () => ({ status: 'saving', error: '' }))
    try {
      const c = m.statusChange
      await api.post(`/agent/orders/${c.orderId}/status`, { from: c.from, to: c.to })
      patch(m.key, () => ({ status: 'done' }))
    } catch (err) {
      patch(m.key, () => ({ status: 'open', error: errorMessage(err) }))
    }
  }

  /** Confirm on an expense card: saved like one typed on the Expenses page. */
  const confirmExpense = async (m) => {
    patch(m.key, () => ({ status: 'saving', error: '' }))
    try {
      const e = m.expense
      await api.post('/agent/expenses', {
        type: e.type, amount: e.amount, expenseDate: e.date, description: e.description, orderId: e.orderId,
      })
      patch(m.key, () => ({ status: 'done' }))
    } catch (err) {
      patch(m.key, () => ({ status: 'open', error: errorMessage(err) }))
    }
  }

  /** Confirm on a stock card: refused if the stock has moved since the card was shown. */
  const confirmStock = async (m) => {
    patch(m.key, () => ({ status: 'saving', error: '' }))
    try {
      const s = m.stock
      await api.post('/agent/stock', { productId: s.productId, change: s.change, reason: s.reason, before: s.before })
      patch(m.key, () => ({ status: 'done' }))
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
        <section className="agent-panel" role="dialog" aria-label="Order assistant"
                 onDragOver={(e) => e.preventDefault()} onDrop={onDrop}>
          <header className="agent-head">
            <b>Order assistant</b>
            <button type="button" className="link" aria-label="Close" onClick={() => setOpen(false)}>✕</button>
          </header>
          <div className="agent-body">
            <p className="agent-bubble agent">{HELLO}</p>
            {messages.length === 0 && (
              <div className="agent-suggest" aria-label="Suggested questions">
                {suggestions.map((t) => (
                  <button key={t} type="button" className="agent-chip" disabled={busy || !!shot} onClick={() => send(t)}>{t}</button>
                ))}
              </div>
            )}
            {messages.map((m) => (m.answer ? (
              <AnswerCard key={m.key} answer={m.answer} />
            ) : m.customer ? (
              <CustomerInfoCard key={m.key} customer={m.customer} />
            ) : m.expense ? (
              <ExpenseCard key={m.key} message={m} onConfirm={() => confirmExpense(m)}
                           onDiscard={() => patch(m.key, () => ({ status: 'discarded' }))} />
            ) : m.stock ? (
              <StockCard key={m.key} message={m} onConfirm={() => confirmStock(m)}
                         onDiscard={() => patch(m.key, () => ({ status: 'discarded' }))} />
            ) : m.statusChange ? (
              <StatusCard key={m.key} message={m} onConfirm={() => confirmStatus(m)}
                          onDiscard={() => patch(m.key, () => ({ status: 'discarded' }))} />
            ) : m.proposal ? (
              <CustomerProposalCard key={m.key} message={m} platforms={platforms}
                                    onEdit={(fn) => patch(m.key, (cur) => ({ form: fn(cur.form) }))}
                                    onConfirm={() => confirmCustomer(m)}
                                    onDiscard={() => patch(m.key, () => ({ status: 'discarded' }))} />
            ) : m.draft ? (
              <DraftCard key={m.key} message={m} platforms={platforms}
                         onEdit={(fn) => patch(m.key, (cur) => ({ draft: fn(cur.draft) }))}
                         onConfirm={() => confirm(m)}
                         onRevise={(instruction) => revise(m, instruction)}
                         onDiscard={() => patch(m.key, () => ({ status: 'discarded' }))} />
            ) : (
              <div key={m.key} className={`agent-bubble ${m.role}${m.failed ? ' failed' : ''}`}>
                {m.screenshot && (m.thumb
                  ? <img className="agent-thumb" src={m.thumb} alt="Screenshot you sent" />
                  : <span className="agent-tag">Screenshot</span>)}
                {m.text && <p>{m.text}</p>}
              </div>
            )))}
            {busy && <p className="agent-bubble agent muted">Thinking…</p>}
            <div ref={endRef} />
          </div>
          {(shot || shotError) && (
            <div className="agent-attached">
              {shot && <img className="agent-thumb" src={shot.thumb} alt="Screenshot to send" />}
              {shot && <button type="button" className="link danger" onClick={() => setShot(null)}>Remove</button>}
              {shotError && <span className="error" role="alert">{shotError}</span>}
            </div>
          )}
          {messages.length > 0 && (
            <div className="agent-chips" aria-label="Suggested questions">
              {suggestions.map((t) => (
                <button key={t} type="button" className="agent-chip" disabled={busy || !!shot} onClick={() => send(t)}>{t}</button>
              ))}
            </div>
          )}
          <div className="agent-input">
            <input ref={fileRef} type="file" accept="image/*" hidden aria-hidden="true" tabIndex={-1}
                   onChange={(e) => { const f = e.target.files?.[0]; e.target.value = ''; if (f) attach(f) }} />
            <button type="button" className="btn secondary small agent-attach" aria-label="Attach a screenshot"
                    title="Attach a screenshot (or paste one)" disabled={busy} onClick={() => fileRef.current?.click()}>
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8"
                   strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M21 12.5l-8.6 8.6a5.5 5.5 0 0 1-7.8-7.8l9-9a3.7 3.7 0 0 1 5.2 5.2l-9 9a1.8 1.8 0 0 1-2.6-2.6l8.4-8.4" />
              </svg>
            </button>
            <textarea ref={inputRef} rows={2} value={text} maxLength={4000} aria-label="Message to the assistant"
                      placeholder="Paste an order, ask a question, or add a customer…"
                      onChange={(e) => setText(e.target.value)} onKeyDown={onKeyDown} onPaste={onPaste} />
            <button type="button" className="btn small" disabled={busy || (!text.trim() && !shot)} onClick={() => send()}>Send</button>
          </div>
        </section>
      )}
      <button type="button" className="agent-fab" aria-label={open ? 'Close order assistant' : 'Open order assistant'}
              aria-expanded={open} title="Order assistant" onClick={() => setOpen((o) => !o)}>
        <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="1.8"
             strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          {open ? <path d="M6 6l12 12M18 6L6 18" /> : (
            <>
              {/* Sparkles: the usual sign of an AI assistant, so it does not look like a personal chat button. */}
              <path d="M9.9 15.5a2 2 0 0 0-1.4-1.4l-6.1-1.6a.5.5 0 0 1 0-1L8.5 9.9a2 2 0 0 0 1.4-1.4l1.6-6.1a.5.5 0 0 1 1 0l1.6 6.1a2 2 0 0 0 1.4 1.4l6.1 1.6a.5.5 0 0 1 0 1l-6.1 1.6a2 2 0 0 0-1.4 1.4l-1.6 6.1a.5.5 0 0 1-1 0z" />
              <path d="M20 3v4M22 5h-4M4 17v2M5 18H3" />
            </>
          )}
        </svg>
      </button>
    </>
  )
}
