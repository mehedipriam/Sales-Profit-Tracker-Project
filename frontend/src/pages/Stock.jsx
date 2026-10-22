import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import Modal from '../components/Modal'
import Pager from '../components/Pager'
import Stat from '../components/Stat'
import useDebounce from '../hooks/useDebounce'
import { BoxIcon, ClockIcon, ReturnIcon, TrendUpIcon } from '../components/icons'
import { STOCK_REASONS, STOCK_REASON_LABEL } from '../utils/format'
import { tint } from '../utils/tint'

const MANUAL_REASONS = ['RESTOCK', 'DAMAGE', 'CORRECTION']
// Shorter names for the tabs; the log itself uses the full labels.
const REASON_TAB = { INITIAL: 'Opening', RESTOCK: 'Restock', DAMAGE: 'Damaged', CORRECTION: 'Correction', ORDER: 'Orders' }

const day = (iso) => new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
const time = (iso) => new Date(iso).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })

function AdjustForm({ products, productId, onSaved, onCancel }) {
  const [form, setForm] = useState({ productId: productId ?? '', reason: 'RESTOCK', quantity: '', note: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const chosen = products.find((p) => String(p.id) === String(form.productId))
  // Restock adds and damage removes, so the user types a plain quantity; only a correction needs a sign.
  const signed = form.reason === 'CORRECTION'

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    const qty = Number(form.quantity)
    const change = form.reason === 'DAMAGE' ? -Math.abs(qty) : form.reason === 'RESTOCK' ? Math.abs(qty) : qty
    try {
      await api.post('/stock/adjustments', {
        productId: Number(form.productId), reason: form.reason, change, note: form.note,
      })
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <label>
        Product
        <select required value={form.productId} onChange={set('productId')}>
          <option value="">Choose a product…</option>
          {products.map((p) => <option key={p.id} value={p.id}>{p.name} ({p.stockQty} in stock)</option>)}
        </select>
      </label>
      <div className="row">
        <label>
          Reason
          <select value={form.reason} onChange={set('reason')}>
            {MANUAL_REASONS.map((r) => <option key={r} value={r}>{STOCK_REASON_LABEL[r]}</option>)}
          </select>
        </label>
        <label>
          {signed ? 'Change (+ or −)' : 'Quantity'}
          <input required type="number" step="1" min={signed ? undefined : 1} value={form.quantity} onChange={set('quantity')} />
        </label>
      </div>
      {chosen && form.quantity !== '' && Number(form.quantity) !== 0 && (
        <p className="hint">
          {chosen.stockQty} → {chosen.stockQty + (form.reason === 'DAMAGE' ? -Math.abs(form.quantity) : form.reason === 'RESTOCK' ? Math.abs(form.quantity) : Number(form.quantity))}
        </p>
      )}
      <label>Note (optional)<input maxLength={255} value={form.note} onChange={set('note')} /></label>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button type="button" className="btn secondary" onClick={onCancel}>Cancel</button>
        <button className="btn" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
    </form>
  )
}

export default function Stock() {
  const [params, setParams] = useSearchParams()
  const productId = params.get('productId') ?? ''
  const [reason, setReason] = useState('')
  const [search, setSearch] = useState('')
  const dq = useDebounce(search)
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [summary, setSummary] = useState(null)
  const [products, setProducts] = useState([])
  const [adjusting, setAdjusting] = useState(false)
  const [error, setError] = useState('')

  const loadProducts = useCallback(() => {
    api.get('/products', { params: { size: 100 } })
      .then((res) => setProducts(res.data.content.filter((p) => p.stockQty != null)))
      .catch((err) => setError(errorMessage(err)))
  }, [])

  const load = useCallback(() => {
    api.get('/stock/adjustments', { params: { productId: productId || undefined, reason: reason || undefined, q: dq.trim() || undefined, page } })
      .then((res) => { setData(res.data); setError('') })
      .catch((err) => setError(errorMessage(err)))
    api.get('/stock/adjustments/summary').then((res) => setSummary(res.data)).catch(() => {})
  }, [productId, reason, dq, page])

  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { load() }, [load])
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { loadProducts() }, [loadProducts])

  const remove = async (a) => {
    const undo = a.change > 0 ? `take ${a.change} back out of` : `put ${-a.change} back into`
    if (!window.confirm(`Delete this ${STOCK_REASON_LABEL[a.reason].toLowerCase()}? It will ${undo} ${a.productName}'s stock.`)) return
    try {
      await api.delete(`/stock/adjustments/${a.id}`)
      load()
      loadProducts()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Stock</h1>
          <p className="muted prod-sub">Every restock, sale and correction, newest first</p>
        </div>
        <button className="btn" onClick={() => setAdjusting(true)}>+ Adjust stock</button>
      </div>

      {summary && (
        <div className="stats kpis">
          <Stat label="Units in stock" value={summary.unitsInStock} hint={`across ${summary.products} products`} icon={<BoxIcon />} accent="blue" />
          <Stat label="Added this month" value={`+${summary.addedThisMonth}`} tone="pos" hint="restocks and returns" icon={<TrendUpIcon />} accent="green" />
          <Stat label="Taken out this month" value={`−${summary.takenOutThisMonth}`} tone={summary.takenOutThisMonth ? 'neg' : ''}
                hint="sales and damage" icon={<ReturnIcon />} accent="red" />
          <Stat label="Need restocking" value={summary.needRestocking} tone={summary.needRestocking ? 'warn' : ''}
                hint="low or out of stock" icon={<ClockIcon />} accent="amber" />
        </div>
      )}

      {summary?.needRestocking > 0 && (
        <div className="dash-lowstock" role="alert">
          <span>⚠</span>
          <span>
            <b>{summary.needRestocking} product{summary.needRestocking === 1 ? '' : 's'} need restocking:</b>{' '}
            {summary.restockFirst.map((p, i) => (
              <span key={p.productId}>{i > 0 && ', '}<Link to={`/stock?productId=${p.productId}`}>{p.name}</Link> ({p.stockQty} left)</span>
            ))}
            {summary.needRestocking > summary.restockFirst.length && ` and ${summary.needRestocking - summary.restockFirst.length} more`}
          </span>
          <button type="button" className="link" onClick={() => setAdjusting(true)}>Restock →</button>
        </div>
      )}

      <div className="rep-bar">
        <input type="search" className="prod-search" value={search} placeholder="Search product or note…"
               aria-label="Search the stock log by product or note" onChange={(e) => { setSearch(e.target.value); setPage(0) }} />
        <select aria-label="Product" value={productId}
                onChange={(e) => { setParams(e.target.value ? { productId: e.target.value } : {}); setPage(0) }}>
          <option value="">All products</option>
          {products.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
        <div className="dash-tabs" role="tablist" aria-label="Reason">
          {[['', 'All'], ...STOCK_REASONS.map((r) => [r, REASON_TAB[r]])].map(([value, label]) => (
            <button key={value} type="button" role="tab" aria-selected={reason === value}
                    className={reason === value ? 'on' : ''} onClick={() => { setReason(value); setPage(0) }}>{label}</button>
          ))}
        </div>
      </div>

      {error && <p className="error" role="alert">{error}</p>}

      <div className="dash-card prod-table">
        <table>
          <thead>
            <tr><th>When</th><th>Product</th><th>Reason</th><th className="num">Change</th><th>Stock</th><th>Note</th><th /></tr>
          </thead>
          <tbody>
            {data?.content.map((a) => (
              <tr key={a.id}>
                <td className="stk-when"><b>{day(a.createdAt)}</b><small>{time(a.createdAt)}</small></td>
                <td>
                  <div className="ord-who">
                    <span className="ord-av" style={tint(a.productName)} aria-hidden="true">{a.productName.trim().charAt(0).toUpperCase()}</span>
                    <span className="cust-name">{a.productName}</span>
                  </div>
                </td>
                <td>
                  <span className={`stk-pill ${a.reason}`}>{STOCK_REASON_LABEL[a.reason]}</span>
                  {a.reason === 'ORDER' && <small className="muted stk-auto" title="Written automatically when an order is recorded, changed or returned">auto</small>}
                </td>
                <td className="num"><span className={`stk-chg ${a.change < 0 ? 'down' : 'up'}`}>{a.change > 0 ? `+${a.change}` : a.change}</span></td>
                <td className="stk-flow"><span className="muted">{a.stockAfter - a.change} →</span> <b>{a.stockAfter}</b></td>
                <td className="notes">
                  {a.orderId ? <Link to={`/orders/${a.orderId}`}>{a.note || `Order #${a.orderId}`}</Link> : a.note || '—'}
                </td>
                <td className="row-actions">
                  {MANUAL_REASONS.includes(a.reason) && (
                    <button className="link danger" onClick={() => remove(a)}>Delete</button>
                  )}
                </td>
              </tr>
            ))}
            {data && data.content.length === 0 && (
              <tr><td colSpan={7} className="empty">{dq || reason || productId ? 'No stock movements match these filters.' : 'No stock movements yet.'}</td></tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager data={data} onPage={setPage} />

      {adjusting && (
        <Modal title="Adjust stock" onClose={() => setAdjusting(false)}>
          <AdjustForm
            products={products}
            productId={productId}
            onSaved={() => { setAdjusting(false); load(); loadProducts() }}
            onCancel={() => setAdjusting(false)}
          />
        </Modal>
      )}
    </section>
  )
}
