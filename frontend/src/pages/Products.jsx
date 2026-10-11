import { useCallback, useEffect, useState } from 'react'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { seesFinancials } from '../auth/roles'
import Modal from '../components/Modal'
import { EyeIcon, EyeOffIcon } from '../components/icons'
import Pager from '../components/Pager'
import useDebounce from '../hooks/useDebounce'
import { Link } from 'react-router-dom'
import { money } from '../utils/format'

const MASK = '••••••'
// Revealed costs hide themselves again after this long, in case the screen is left unattended.
const REVEAL_MS = 5 * 60 * 1000

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

// Asks for the signed-in user's login password before costs or margins are shown.
function RevealPrompt({ what, onRevealed, onCancel }) {
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      await api.post('/account/verify-password', { password })
      onRevealed()
    } catch (err) {
      setError(errorMessage(err))
      setPassword('')
      setBusy(false)
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <label>
        Your password
        <input required autoFocus type="password" autoComplete="current-password" value={password}
               onChange={(e) => setPassword(e.target.value)} />
        <span className="hint">{what === 'costs' ? 'Cost prices stay' : 'Margins stay'} hidden until you enter the password you log in with.</span>
      </label>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button type="button" className="btn secondary" onClick={onCancel}>Cancel</button>
        <button className="btn" disabled={busy}>{busy ? 'Checking…' : `Show ${what}`}</button>
      </div>
    </form>
  )
}

const PAGE_SIZE = 20

export default function Products() {
  const { user } = useAuth()
  const showMoney = seesFinancials(user)
  const [q, setQ] = useState('')
  const [category, setCategory] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [categories, setCategories] = useState([])
  const [editing, setEditing] = useState(null) // null = closed, {} = new, product = edit
  const [error, setError] = useState('')
  // Cost and margin each have their own eye, so one can be shown while the other stays hidden.
  const [revealed, setRevealed] = useState({ cost: false, margin: false })
  const [asking, setAsking] = useState(null) // null, 'cost' or 'margin'
  const dq = useDebounce(q)

  useEffect(() => {
    if (!revealed.cost) return undefined
    const timer = setTimeout(() => setRevealed((r) => ({ ...r, cost: false })), REVEAL_MS)
    return () => clearTimeout(timer)
  }, [revealed.cost])

  useEffect(() => {
    if (!revealed.margin) return undefined
    const timer = setTimeout(() => setRevealed((r) => ({ ...r, margin: false })), REVEAL_MS)
    return () => clearTimeout(timer)
  }, [revealed.margin])

  const revealButton = (col, label) => {
    const shown = revealed[col]
    const text = `${shown ? 'Hide' : 'Show'} ${label}`
    return (
      <button type="button" className="reveal" title={text} aria-label={text}
              onClick={() => (shown ? setRevealed((r) => ({ ...r, [col]: false })) : setAsking(col))}>
        {shown ? <EyeOffIcon /> : <EyeIcon />}
      </button>
    )
  }

  const load = useCallback(() => {
    api
      .get('/products', { params: { q: dq, category, page, size: PAGE_SIZE } })
      .then((res) => { setData(res.data); setError('') })
      .catch((err) => setError(errorMessage(err)))
    api.get('/products/categories').then((res) => setCategories(res.data)).catch(() => {})
  }, [dq, category, page])

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
        <h1>Products</h1>
        <button className="btn" onClick={() => setEditing({})}>+ Add product</button>
      </div>

      <div className="filters">
        <input
          type="search"
          placeholder="Search name or SKU…"
          value={q}
          onChange={(e) => { setQ(e.target.value); setPage(0) }}
        />
        <select value={category} onChange={(e) => { setCategory(e.target.value); setPage(0) }}>
          <option value="">All categories</option>
          {categories.map((c) => <option key={c} value={c}>{c}</option>)}
        </select>
      </div>

      {error && <p className="error" role="alert">{error}</p>}

      <div className="table-wrap">
        <table className="striped">
          <thead>
            <tr>
              <th className="serial">#</th><th>Name</th><th>SKU</th><th>Category</th>
              {showMoney && <th className="num hide-col"><span className="hide-head">Cost{revealButton('cost', 'costs')}</span></th>}
              <th className="num">Price</th>
              {showMoney && <th className="num hide-col"><span className="hide-head">Margin{revealButton('margin', 'margins')}</span></th>}
              <th className="num">Stock</th><th />
            </tr>
          </thead>
          <tbody>
            {data?.content.map((p, i) => {
              const margin = p.sellingPrice - p.costPrice
              return (
                <tr key={p.id}>
                  {/* Counts on from the previous page, so page 2 starts at 21. */}
                  <td className="serial">{data.page * PAGE_SIZE + i + 1}</td>
                  <td>{p.name}</td>
                  <td>{p.sku || '—'}</td>
                  <td>{p.category || '—'}</td>
                  {showMoney && (
                    <td className={`num ${revealed.cost ? '' : 'masked hide-col'}`}>{revealed.cost ? money(p.costPrice) : MASK}</td>
                  )}
                  <td className="num">{money(p.sellingPrice)}</td>
                  {showMoney && (revealed.margin
                    ? <td className={`num ${margin < 0 ? 'neg' : 'pos'}`}>{money(margin)}</td>
                    : <td className="num masked hide-col">{MASK}</td>)}
                  <td className="num">
                    {p.stockQty == null ? '—' : (
                      <Link to={`/stock?productId=${p.id}`} title="View stock history">{p.stockQty}</Link>
                    )}
                    {p.stockQty != null && p.lowStockThreshold != null && p.stockQty <= p.lowStockThreshold && (
                      <span className="badge low">Low</span>
                    )}
                  </td>
                  <td className="row-actions">
                    <button className="link" onClick={() => setEditing(p)}>Edit</button>
                    <button className="link danger" onClick={() => remove(p)}>Delete</button>
                  </td>
                </tr>
              )
            })}
            {data && data.content.length === 0 && (
              <tr><td colSpan={showMoney ? 9 : 7} className="empty">No products found.</td></tr>
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
            costHidden={!revealed.cost}
            onSaved={onSaved}
            onCancel={() => setEditing(null)}
          />
        </Modal>
      )}

      {asking && (
        <Modal title={asking === 'cost' ? 'Show cost prices' : 'Show margins'} onClose={() => setAsking(null)}>
          <RevealPrompt
            what={asking === 'cost' ? 'costs' : 'margins'}
            onRevealed={() => { setRevealed((r) => ({ ...r, [asking]: true })); setAsking(null) }}
            onCancel={() => setAsking(null)}
          />
        </Modal>
      )}
    </section>
  )
}
