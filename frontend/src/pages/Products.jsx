import { useCallback, useEffect, useState } from 'react'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { seesFinancials } from '../auth/roles'
import Modal from '../components/Modal'
import Pager from '../components/Pager'
import { EyeHead, MASK, useReveal } from '../components/Reveal'
import Stat from '../components/Stat'
import { BoxIcon, CancelIcon, ClockIcon, WalletIcon } from '../components/icons'
import useDebounce from '../hooks/useDebounce'
import { Link } from 'react-router-dom'
import { money } from '../utils/format'
import { tint } from '../utils/tint'

const EMPTY = { name: '', sku: '', category: '', costPrice: '', sellingPrice: '', stockQty: '', lowStockThreshold: '' }

function ProductForm({ product, categories, showMoney, costHidden, onSaved, onCancel }) {
  const [form, setForm] = useState(
    product ? { ...EMPTY, ...Object.fromEntries(Object.entries(product).map(([k, v]) => [k, v ?? ''])) } : EMPTY,
  )
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    const body = {
      name: form.name,
      sku: form.sku,
      category: form.category,
      // Staff never sees a cost price to resubmit - omit it and the backend leaves the real one untouched
      // (or starts a brand new product at 0, for an owner to price properly later).
      costPrice: showMoney ? form.costPrice : undefined,
      sellingPrice: form.sellingPrice,
      stockQty: form.stockQty === '' ? null : Number(form.stockQty),
      lowStockThreshold: form.lowStockThreshold === '' ? null : Number(form.lowStockThreshold),
    }
    try {
      if (product) await api.put(`/products/${product.id}`, body)
      else await api.post('/products', body)
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <label>Name<input required maxLength={190} value={form.name} onChange={set('name')} /></label>
      <div className="row">
        <label>SKU / code<input maxLength={64} value={form.sku} onChange={set('sku')} /></label>
        <label>
          Category
          <input list="categories" maxLength={100} value={form.category} onChange={set('category')} />
          <datalist id="categories">{categories.map((c) => <option key={c} value={c} />)}</datalist>
        </label>
      </div>
      <div className="row">
        {showMoney && (product && costHidden ? (
          <label>
            Cost price
            <input disabled value={MASK} title="Show costs with the eye in the table to change this" />
          </label>
        ) : (
          <label>
            Cost price
            <input required type="number" min="0" step="0.01" value={form.costPrice} onChange={set('costPrice')} />
          </label>
        ))}
        <label>
          Selling price
          <input required type="number" min="0" step="0.01" value={form.sellingPrice} onChange={set('sellingPrice')} />
        </label>
        <label>
          Stock (optional)
          <input type="number" min="0" step="1" value={form.stockQty} onChange={set('stockQty')} />
        </label>
        <label>
          Alert at or below
          <input type="number" min="0" step="1" value={form.lowStockThreshold} onChange={set('lowStockThreshold')}
                 disabled={form.stockQty === ''} placeholder="optional" />
        </label>
      </div>
      <p className="hint">
        Stock drops automatically when a sale is recorded and returns if the order is returned or cancelled.
        Use Stock log for restocks and damage; a quantity typed here is logged as a correction.
      </p>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button type="button" className="btn secondary" onClick={onCancel}>Cancel</button>
        <button className="btn" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
    </form>
  )
}

const PAGE_SIZE = 20
const STOCK_FILTERS = [['', 'All'], ['in', 'In stock'], ['low', 'Low'], ['out', 'Out']]

/** Stock level as a count, a bar and a status: none left, low (at or under its own alert level), or fine. */
function StockCell({ p }) {
  if (p.stockQty == null) return <span className="muted">—</span>
  const out = p.stockQty <= 0
  const low = !out && p.lowStockThreshold != null && p.stockQty <= p.lowStockThreshold
  const full = Math.max((p.lowStockThreshold ?? 5) * 4, 10) // the amount the bar counts as a full shelf
  const state = out ? 'out' : low ? 'low' : 'ok'
  return (
    <div className="prod-stock">
      <div className="prod-stock-top">
        <Link to={`/stock?productId=${p.id}`} title="View stock history"><b>{p.stockQty}</b></Link>
        {out ? <span className="badge out">Out</span> : low ? <span className="badge low">Low</span>
          : <span className="muted">in stock</span>}
      </div>
      <div className="prod-stock-bar"><i className={state} style={{ width: `${Math.min(100, (Math.max(p.stockQty, 0) / full) * 100)}%` }} /></div>
    </div>
  )
}

export default function Products() {
  const { user } = useAuth()
  const showMoney = seesFinancials(user)
  const [q, setQ] = useState('')
  const [category, setCategory] = useState('')
  const [stock, setStock] = useState('')
  const [summary, setSummary] = useState(null)
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [categories, setCategories] = useState([])
  const [editing, setEditing] = useState(null) // null = closed, {} = new, product = edit
  const [error, setError] = useState('')
  // Cost and margin each have their own eye, so one can be shown while the other stays hidden.
  const { shown, eye, prompt } = useReveal()
  const dq = useDebounce(q)

  const load = useCallback(() => {
    api
      .get('/products', { params: { q: dq, category, stock, page, size: PAGE_SIZE } })
      .then((res) => { setData(res.data); setError('') })
      .catch((err) => setError(errorMessage(err)))
    api.get('/products/categories').then((res) => setCategories(res.data)).catch(() => {})
    api.get('/products/summary').then((res) => setSummary(res.data)).catch(() => {})
  }, [dq, category, stock, page])

  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { load() }, [load])

  const onSaved = () => { setEditing(null); load() }

  const remove = async (p) => {
    if (!window.confirm(`Delete "${p.name}"? Past orders keep their records.`)) return
    try {
      await api.delete(`/products/${p.id}`)
      load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Products</h1>
          <p className="muted prod-sub">What you sell, what it costs and what is left</p>
        </div>
        <button className="btn" onClick={() => setEditing({})}>+ Add product</button>
      </div>

      {summary && (
        <div className="stats kpis">
          <Stat label="Products" value={summary.products} icon={<BoxIcon />} accent="blue" />
          {showMoney && (
            <Stat label="Stock value" value={shown.cost ? money(summary.stockValue) : MASK} tone={shown.cost ? '' : 'masked'}
                  hint="stock on hand, at cost" icon={<WalletIcon />} accent="green" />
          )}
          <Stat label="Low stock" value={summary.lowStock} tone={summary.lowStock ? 'warn' : ''}
                hint="at or under the alert level" icon={<ClockIcon />} accent="amber" />
          <Stat label="Out of stock" value={summary.outOfStock} tone={summary.outOfStock ? 'neg' : ''}
                hint="none left" icon={<CancelIcon />} accent="red" />
        </div>
      )}

      <div className="rep-bar">
        <input
          className="prod-search"
          type="search"
          placeholder="Search name or SKU…"
          value={q}
          onChange={(e) => { setQ(e.target.value); setPage(0) }}
        />
        <div className="dash-tabs" role="tablist" aria-label="Stock level">
          {STOCK_FILTERS.map(([value, label]) => (
            <button key={value} type="button" role="tab" aria-selected={stock === value}
                    className={stock === value ? 'on' : ''} onClick={() => { setStock(value); setPage(0) }}>{label}</button>
          ))}
        </div>
        <select aria-label="Category" value={category} onChange={(e) => { setCategory(e.target.value); setPage(0) }}>
          <option value="">All categories</option>
          {categories.map((c) => <option key={c} value={c}>{c}</option>)}
        </select>
      </div>

      {error && <p className="error" role="alert">{error}</p>}

      <div className="dash-card prod-table">
        <table>
          <thead>
            <tr>
              <th>Product</th><th>Category</th>
              {showMoney && <th className="num hide-col"><EyeHead eye={eye('cost', 'costs')}>Cost</EyeHead></th>}
              <th className="num">Price</th>
              {showMoney && <th className="num hide-col"><EyeHead eye={eye('margin', 'margins')}>Margin</EyeHead></th>}
              <th>Stock</th><th />
            </tr>
          </thead>
          <tbody>
            {data?.content.map((p) => {
              const margin = p.sellingPrice - p.costPrice
              const pct = p.sellingPrice > 0 ? (margin / p.sellingPrice) * 100 : null
              return (
                <tr key={p.id}>
                  <td>
                    <div className="prod-name">
                      <span className="prod-tile" style={tint(p.name)} aria-hidden="true">{p.name.trim().charAt(0).toUpperCase()}</span>
                      <div><b>{p.name}</b><small>{p.sku ? `SKU ${p.sku}` : 'No SKU'}</small></div>
                    </div>
                  </td>
                  <td>{p.category ? <span className="prod-cat" style={tint(p.category)}>{p.category}</span> : <span className="muted">—</span>}</td>
                  {showMoney && (
                    <td className={`num ${shown.cost ? '' : 'masked hide-col'}`}>{shown.cost ? money(p.costPrice) : MASK}</td>
                  )}
                  <td className="num">{money(p.sellingPrice)}</td>
                  {showMoney && (shown.margin
                    ? (
                      <td className="num">
                        <span className={`prod-margin ${margin < 0 ? 'neg' : 'pos'}`}>
                          {money(margin)}{pct !== null && ` · ${pct.toFixed(0)}%`}
                        </span>
                      </td>
                    )
                    : <td className="num masked hide-col">{MASK}</td>)}
                  <td><StockCell p={p} /></td>
                  <td className="row-actions">
                    <button className="link" onClick={() => setEditing(p)}>Edit</button>
                    <button className="link danger" onClick={() => remove(p)}>Delete</button>
                  </td>
                </tr>
              )
            })}
            {data && data.content.length === 0 && (
              <tr><td colSpan={showMoney ? 7 : 5} className="empty">No products found.</td></tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager data={data} onPage={setPage} />

      {editing && (
        <Modal title={editing.id ? 'Edit product' : 'Add product'} onClose={() => setEditing(null)}>
          <ProductForm
            product={editing.id ? editing : null}
            categories={categories}
            showMoney={showMoney}
            costHidden={!shown.cost}
            onSaved={onSaved}
            onCancel={() => setEditing(null)}
          />
        </Modal>
      )}

      {prompt}
    </section>
  )
}
