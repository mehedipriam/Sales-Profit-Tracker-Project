import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import AsyncPicker from '../components/AsyncPicker'
import { STATUSES, STATUS_LABEL, money, toLocalInput } from '../utils/format'

const searchProducts = (q) => api.get('/products', { params: { q, size: 10 } }).then((r) => r.data.content)
const searchCustomers = (q) => api.get('/customers', { params: { q, size: 10 } }).then((r) => r.data.content)

let lineKey = 0
const newLine = (product, extra = {}) => ({
  key: ++lineKey,
  productId: product.id,
  name: product.name,
  cost: Number(product.costPrice),
  quantity: 1,
  soldPrice: product.sellingPrice ?? '',
  ...extra,
})

export default function OrderForm() {
  const { id } = useParams()
  const editing = Boolean(id)
  const navigate = useNavigate()

  const [platforms, setPlatforms] = useState([])
  const [platformId, setPlatformId] = useState('')
  const [customer, setCustomer] = useState(null) // picked existing customer
  const [isNewCustomer, setIsNewCustomer] = useState(false)
  const [nc, setNc] = useState({ name: '', phone: '', address: '' })
  const [status, setStatus] = useState('PAID')
  const [orderedAt, setOrderedAt] = useState(toLocalInput())
  const [notes, setNotes] = useState('')
  const [lines, setLines] = useState([])
  const [loading, setLoading] = useState(editing)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api.get('/platforms').then((res) => {
      setPlatforms(res.data)
      if (!editing && res.data.length) setPlatformId(String(res.data[0].id))
    }).catch(() => {})
  }, [editing])

  useEffect(() => {
    if (!editing) return
    api.get(`/orders/${id}`).then(({ data: o }) => {
      setPlatformId(String(o.platformId))
      setCustomer({ id: o.customerId, name: o.customerName, phone: o.customerPhone })
      setStatus(o.status)
      setOrderedAt(o.orderedAt.slice(0, 16))
      setNotes(o.notes ?? '')
      setLines(o.items.map((i) => newLine(
        { id: i.productId, name: i.productName, costPrice: i.costPrice },
        { quantity: i.quantity, soldPrice: i.soldPrice },
      )))
    }).catch((err) => setError(errorMessage(err))).finally(() => setLoading(false))
  }, [editing, id])

  const addProduct = (product) => {
    if (lines.some((l) => l.productId === product.id)) {
      setError(`"${product.name}" is already on this order; change its quantity instead.`)
      return
    }
    setError('')
    setLines([...lines, newLine(product)])
  }
  const updateLine = (key, patch) => setLines(lines.map((l) => (l.key === key ? { ...l, ...patch } : l)))
  const removeLine = (key) => setLines(lines.filter((l) => l.key !== key))

  const lineProfit = (l) => (Number(l.soldPrice || 0) - l.cost) * Number(l.quantity || 0)
  const revenue = lines.reduce((s, l) => s + Number(l.soldPrice || 0) * Number(l.quantity || 0), 0)
  const cost = lines.reduce((s, l) => s + l.cost * Number(l.quantity || 0), 0)

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    if (lines.length === 0) return setError('Add at least one product.')
    if (!isNewCustomer && !customer) return setError('Pick a customer or add a new one.')

    const body = {
      platformId: Number(platformId),
      customerId: isNewCustomer ? null : customer.id,
      newCustomer: isNewCustomer ? nc : null,
      status,
      orderedAt,
      notes,
      items: lines.map((l) => ({ productId: l.productId, quantity: Number(l.quantity), soldPrice: l.soldPrice })),
    }
    setBusy(true)
    try {
      if (editing) await api.put(`/orders/${id}`, body)
      else await api.post('/orders', body)
      navigate('/orders')
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  if (loading) return <p className="center-note">Loading…</p>

  return (
    <section>
      <div className="page-head">
        <h1>{editing ? `Edit order #${id}` : 'New sale'}</h1>
        <Link className="btn secondary" to="/orders">Back to orders</Link>
      </div>

      <form className="card wide" onSubmit={submit}>
        <div className="row">
          <label>
            Platform
            <select required value={platformId} onChange={(e) => setPlatformId(e.target.value)}>
              {platforms.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
          </label>
          <label>
            Date &amp; time
            <input required type="datetime-local" value={orderedAt} onChange={(e) => setOrderedAt(e.target.value)} />
          </label>
          <label>
            Status
            <select value={status} onChange={(e) => setStatus(e.target.value)}>
              {STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
            </select>
          </label>
        </div>

        <fieldset>
          <legend>Customer</legend>
          {!isNewCustomer ? (
            <>
              {customer ? (
                <p className="chosen">
                  <b>{customer.name}</b> {customer.phone && <span className="muted">· {customer.phone}</span>}
                  <button type="button" className="link" onClick={() => setCustomer(null)}>Change</button>
                </p>
              ) : (
                <AsyncPicker search={searchCustomers} placeholder="Search customer by name or phone…"
                             label={(c) => `${c.name}${c.phone ? ' · ' + c.phone : ''}`} onPick={setCustomer} />
              )}
              <button type="button" className="link" onClick={() => setIsNewCustomer(true)}>+ Add a new customer</button>
            </>
          ) : (
            <>
              <div className="row">
                <label>Name<input required value={nc.name} onChange={(e) => setNc({ ...nc, name: e.target.value })} /></label>
                <label>Phone<input value={nc.phone} onChange={(e) => setNc({ ...nc, phone: e.target.value })} /></label>
              </div>
              <label>Address<input value={nc.address} onChange={(e) => setNc({ ...nc, address: e.target.value })} /></label>
              <button type="button" className="link" onClick={() => setIsNewCustomer(false)}>Pick an existing customer instead</button>
            </>
          )}
        </fieldset>

        <fieldset>
          <legend>Products</legend>
          <AsyncPicker search={searchProducts} placeholder="Search products by name or SKU to add…"
                       label={(p) => `${p.name}${p.sku ? ' (' + p.sku + ')' : ''} · ${money(p.sellingPrice)}`}
                       onPick={addProduct} />
          {lines.length > 0 && (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr><th>Product</th><th className="num">Qty</th><th className="num">Sold price</th>
                      <th className="num">Cost</th><th className="num">Line profit</th><th /></tr>
                </thead>
                <tbody>
                  {lines.map((l) => (
                    <tr key={l.key}>
                      <td>{l.name}</td>
                      <td className="num">
                        <input className="small" type="number" min="1" step="1" required aria-label={`Quantity of ${l.name}`}
                               value={l.quantity} onChange={(e) => updateLine(l.key, { quantity: e.target.value })} />
                      </td>
                      <td className="num">
                        <input className="small" type="number" min="0" step="0.01" required aria-label={`Sold price of ${l.name}`}
                               value={l.soldPrice} onChange={(e) => updateLine(l.key, { soldPrice: e.target.value })} />
                      </td>
                      <td className="num">{money(l.cost)}</td>
                      <td className={`num ${lineProfit(l) < 0 ? 'neg' : 'pos'}`}>{money(lineProfit(l))}</td>
                      <td className="row-actions"><button type="button" className="link danger" onClick={() => removeLine(l.key)}>Remove</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </fieldset>

        <label>Notes<textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} /></label>

        <div className="totals">
          <span>Revenue <b>{money(revenue)}</b></span>
          <span>Cost <b>{money(cost)}</b></span>
          <span>Profit <b className={revenue - cost < 0 ? 'neg' : 'pos'}>{money(revenue - cost)}</b></span>
        </div>

        {error && <p className="error" role="alert">{error}</p>}
        <div className="actions">
          <Link className="btn secondary" to="/orders">Cancel</Link>
          <button className="btn" disabled={busy}>{busy ? 'Saving…' : editing ? 'Save changes' : 'Record sale'}</button>
        </div>
      </form>
    </section>
  )
}
