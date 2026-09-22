import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import AsyncPicker from '../components/AsyncPicker'
import { EXPENSE_LABEL, STATUSES, STATUS_LABEL, money, toLocalInput } from '../utils/format'

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
  const { user } = useAuth()
  const isOwner = user?.role === 'OWNER'
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
  const [deliveryCharge, setDeliveryCharge] = useState('')
  const [lines, setLines] = useState([])
  const [original, setOriginal] = useState(null) // { platformId, rate, expenses } of the order being edited
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
      setOriginal({ platformId: String(o.platformId), rate: Number(o.commissionPct), expenses: o.expenses })
      setCustomer({ id: o.customerId, name: o.customerName, phone: o.customerPhone })
      setStatus(o.status)
      setOrderedAt(o.orderedAt.slice(0, 16))
      setNotes(o.notes ?? '')
      setDeliveryCharge(Number(o.deliveryCharge) > 0 ? o.deliveryCharge : '')
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
  const delivery = Number(deliveryCharge || 0)

  // The rate a sale is charged: an edited order keeps the rate it was recorded at unless its platform changes.
  const platform = platforms.find((p) => String(p.id) === platformId)
  const rate = editing && original && platformId === original.platformId ? original.rate : Number(platform?.commissionPct ?? 0)
  const accrues = status === 'PAID' || status === 'PENDING'
  const commission = accrues ? Math.round(revenue * rate) / 100 : 0
  const expenseTotal = (original?.expenses ?? []).reduce((s, x) => s + Number(x.amount), 0)

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
      deliveryCharge: deliveryCharge === '' ? 0 : deliveryCharge,
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
                      {isOwner && <><th className="num">Cost</th><th className="num">Line profit</th></>}<th /></tr>
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
                      {isOwner && <>
                        <td className="num">{money(l.cost)}</td>
                        <td className={`num ${lineProfit(l) < 0 ? 'neg' : 'pos'}`}>{money(lineProfit(l))}</td>
                      </>}
                      <td className="row-actions"><button type="button" className="link danger" onClick={() => removeLine(l.key)}>Remove</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </fieldset>

        <label>
          Delivery charge paid by the customer
          <input type="number" min="0" step="0.01" placeholder="0" value={deliveryCharge}
                 onChange={(e) => setDeliveryCharge(e.target.value)} />
          <span className="hint">Money the customer paid for delivery on top of the products. Leave empty for free delivery.
            Record what the courier charged you as a Delivery expense; the two cancel out in net profit.</span>
        </label>

        <label>Notes<textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} /></label>

        <div className="totals">
          <span>Revenue <b>{money(revenue)}</b></span>
          {isOwner && <>
            <span>Cost <b>{money(cost)}</b></span>
            <span>Profit <b className={revenue - cost < 0 ? 'neg' : 'pos'}>{money(revenue - cost)}</b></span>
          </>}
          {delivery > 0 && <>
            <span>Delivery <b>{money(delivery)}</b></span>
            <span>Customer pays <b>{money(revenue + delivery)}</b></span>
          </>}
        </div>

        {isOwner && rate > 0 && (
          <p className="hint commission-note">
            {platform?.name} commission {rate}%
            {accrues ? <> ≈ <b>{money(commission)}</b> will be added automatically as an expense.</> : ' is not charged while an order is returned or cancelled.'}
          </p>
        )}

        {isOwner && editing && original && (
          <fieldset>
            <legend>Expenses on this order</legend>
            {original.expenses.length === 0 ? (
              <p className="muted">None recorded yet.</p>
            ) : (
              <ul className="expense-list">
                {original.expenses.map((x) => (
                  <li key={x.id}>
                    <span>{EXPENSE_LABEL[x.type]}{x.autoGenerated && <> <span className="badge auto">Auto</span></>}{x.description ? ` · ${x.description}` : ''}</span>
                    <b>{money(x.amount)}</b>
                  </li>
                ))}
                <li className="expense-total"><span>Total</span><b>{money(expenseTotal)}</b></li>
              </ul>
            )}
            <Link className="link" to={`/expenses?newFor=${id}`}>+ Add an expense to this order</Link>
          </fieldset>
        )}

        {error && <p className="error" role="alert">{error}</p>}
        <div className="actions">
          <Link className="btn secondary" to="/orders">Cancel</Link>
          <button className="btn" disabled={busy}>{busy ? 'Saving…' : editing ? 'Save changes' : 'Record sale'}</button>
        </div>
      </form>
    </section>
  )
}
