import { useCallback, useEffect, useState } from 'react'
import api, { errorMessage } from '../api/client'
import Modal from '../components/Modal'
import Pager from '../components/Pager'
import useDebounce from '../hooks/useDebounce'
import { money } from '../utils/format'

const EMPTY = { name: '', sku: '', category: '', costPrice: '', sellingPrice: '', stockQty: '' }

function ProductForm({ product, categories, onSaved, onCancel }) {
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
      costPrice: form.costPrice,
      sellingPrice: form.sellingPrice,
      stockQty: form.stockQty === '' ? null : Number(form.stockQty),
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
        <label>
          Cost price
          <input required type="number" min="0" step="0.01" value={form.costPrice} onChange={set('costPrice')} />
        </label>
        <label>
          Selling price
          <input required type="number" min="0" step="0.01" value={form.sellingPrice} onChange={set('sellingPrice')} />
        </label>
        <label>
          Stock (optional)
          <input type="number" min="0" step="1" value={form.stockQty} onChange={set('stockQty')} />
        </label>
      </div>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button type="button" className="btn secondary" onClick={onCancel}>Cancel</button>
        <button className="btn" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
    </form>
  )
}

export default function Products() {
  const [q, setQ] = useState('')
  const [category, setCategory] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [categories, setCategories] = useState([])
  const [editing, setEditing] = useState(null) // null = closed, {} = new, product = edit
  const [error, setError] = useState('')
  const dq = useDebounce(q)

  const load = useCallback(() => {
    api
      .get('/products', { params: { q: dq, category, page } })
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
        <table>
          <thead>
            <tr>
              <th>Name</th><th>SKU</th><th>Category</th>
              <th className="num">Cost</th><th className="num">Price</th><th className="num">Margin</th>
              <th className="num">Stock</th><th />
            </tr>
          </thead>
          <tbody>
            {data?.content.map((p) => {
              const margin = p.sellingPrice - p.costPrice
              return (
                <tr key={p.id}>
                  <td>{p.name}</td>
                  <td>{p.sku || '—'}</td>
                  <td>{p.category || '—'}</td>
                  <td className="num">{money(p.costPrice)}</td>
                  <td className="num">{money(p.sellingPrice)}</td>
                  <td className={`num ${margin < 0 ? 'neg' : 'pos'}`}>{money(margin)}</td>
                  <td className="num">{p.stockQty ?? '—'}</td>
                  <td className="row-actions">
                    <button className="link" onClick={() => setEditing(p)}>Edit</button>
                    <button className="link danger" onClick={() => remove(p)}>Delete</button>
                  </td>
                </tr>
              )
            })}
            {data && data.content.length === 0 && (
              <tr><td colSpan={8} className="empty">No products found.</td></tr>
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
            onSaved={onSaved}
            onCancel={() => setEditing(null)}
          />
        </Modal>
      )}
    </section>
  )
}
