import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import Modal from '../components/Modal'
import Pager from '../components/Pager'
import { STOCK_REASONS, STOCK_REASON_LABEL, dateTime } from '../utils/format'

const MANUAL_REASONS = ['RESTOCK', 'DAMAGE', 'CORRECTION']

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
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [products, setProducts] = useState([])
  const [adjusting, setAdjusting] = useState(false)
  const [error, setError] = useState('')

  const loadProducts = useCallback(() => {
    api.get('/products', { params: { size: 100 } })
      .then((res) => setProducts(res.data.content.filter((p) => p.stockQty != null)))
      .catch((err) => setError(errorMessage(err)))
  }, [])

  const load = useCallback(() => {
    api.get('/stock/adjustments', { params: { productId: productId || undefined, reason: reason || undefined, page } })
      .then((res) => { setData(res.data); setError('') })
      .catch((err) => setError(errorMessage(err)))
  }, [productId, reason, page])

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
        <h1>Stock log</h1>
        <button className="btn" onClick={() => setAdjusting(true)}>+ Adjust stock</button>
      </div>

      <div className="filters">
        <select aria-label="Product" value={productId}
                onChange={(e) => { setParams(e.target.value ? { productId: e.target.value } : {}); setPage(0) }}>
          <option value="">All products</option>
          {products.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
        <select aria-label="Reason" value={reason} onChange={(e) => { setReason(e.target.value); setPage(0) }}>
          <option value="">All reasons</option>
          {STOCK_REASONS.map((r) => <option key={r} value={r}>{STOCK_REASON_LABEL[r]}</option>)}
        </select>
      </div>

      {error && <p className="error" role="alert">{error}</p>}

      <div className="table-wrap spaced">
        <table className="striped">
          <thead>
            <tr><th>When</th><th>Product</th><th>Reason</th><th className="num">Change</th><th className="num">Stock after</th><th>Note</th><th /></tr>
          </thead>
          <tbody>
            {data?.content.map((a) => (
              <tr key={a.id}>
                <td className="nowrap">{dateTime(a.createdAt)}</td>
                <td>{a.productName}</td>
                <td>
                  <span className="reason">
                    {STOCK_REASON_LABEL[a.reason]}
                    {a.reason === 'ORDER' && <span className="badge auto" title="Written automatically when an order is recorded, changed or returned">Auto</span>}
                  </span>
                </td>
                <td className={`num ${a.change < 0 ? 'neg' : 'pos'}`}>{a.change > 0 ? `+${a.change}` : a.change}</td>
                <td className="num">{a.stockAfter}</td>
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
              <tr><td colSpan={7} className="empty">No stock movements yet.</td></tr>
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
