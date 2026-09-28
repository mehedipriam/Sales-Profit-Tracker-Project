import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { logoUrl } from '../components/logo'
import { printAs } from '../utils/download'
import { currencySymbol, invoiceNumber, money } from '../utils/format'

const longDate = (iso) => new Date(iso).toLocaleDateString('en-GB', { dateStyle: 'long' })

/**
 * What the customer owes, from their side: the courier's cash on delivery, anything paid in advance, or "paid".
 * { label, tone, due, advance } where due is what is still to be collected (0 when nothing is).
 */
function payment(order, total) {
  const cod = order.courier ? Number(order.courier.codAmount ?? 0) : null
  switch (order.status) {
    case 'CANCELLED': return { label: 'Cancelled', tone: 'void', due: 0, advance: 0 }
    case 'RETURNED': return { label: 'Returned', tone: 'void', due: 0, advance: 0 }
    case 'PAID': return { label: 'Paid', tone: 'paid', due: 0, advance: 0 }
    default:
      if (cod === null) return { label: 'Payment due', tone: 'due', due: total, advance: 0 }
      if (cod <= 0) return { label: 'Paid in advance', tone: 'paid', due: 0, advance: total }
      return { label: 'Cash on delivery', tone: 'due', due: cod, advance: Math.max(total - cod, 0) }
  }
}

function StoreBlock({ user }) {
  return (
    <div className="inv-brand">
      <img src={logoUrl(currencySymbol())} alt="" />
      <div>
        <h2>{user.businessName}</h2>
        {user.businessAddress && <p>{user.businessAddress}</p>}
        {user.businessPhone && <p>{user.businessPhone}</p>}
      </div>
    </div>
  )
}

function CustomerBlock({ order, title }) {
  return (
    <div className="inv-party">
      <span className="inv-label">{title}</span>
      <b>{order.customerName}</b>
      {order.customerPhone && <span>{order.customerPhone}</span>}
      {order.customerAddress && <span className="inv-address">{order.customerAddress}</span>}
    </div>
  )
}

/** Courier details; left off entirely for orders delivered without a courier. */
function ShippingBlock({ order }) {
  const c = order.courier
  if (!c) return null
  return (
    <div className="inv-party">
      <span className="inv-label">Shipping</span>
      <span>Courier: <b>{c.courierName}</b></span>
      {c.consignmentId && <span>Parcel ID: <b>{c.consignmentId}</b></span>}
      {c.trackingNumber && !/^https?:\/\//i.test(c.trackingNumber) && <span>Tracking: <b>{c.trackingNumber}</b></span>}
    </div>
  )
}

function InvoiceDoc({ order, user }) {
  const subtotal = Number(order.revenue)
  const delivery = Number(order.deliveryCharge)
  const total = subtotal + delivery
  const pay = payment(order, total)

  return (
    <article className="invoice">
      <header className="inv-head">
        <StoreBlock user={user} />
        <div className="inv-meta">
          <span className="inv-doc">Invoice</span>
          <b>{invoiceNumber(order.id)}</b>
          <span>Date: {longDate(order.orderedAt)}</span>
          <span className={`inv-stamp ${pay.tone}`}>{pay.label}</span>
        </div>
      </header>

      <div className="inv-parties">
        <CustomerBlock order={order} title="Bill to" />
        <ShippingBlock order={order} />
      </div>

      <table className="inv-items">
        <thead>
          <tr><th className="c-no">#</th><th>Item</th><th className="num">Qty</th><th className="num">Unit price</th><th className="num">Amount</th></tr>
        </thead>
        <tbody>
          {order.items.map((i, n) => (
            <tr key={i.productId}>
              <td className="c-no">{n + 1}</td>
              <td>{i.productName}</td>
              <td className="num">{i.quantity}</td>
              <td className="num">{money(i.soldPrice)}</td>
              <td className="num">{money(i.lineRevenue)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="inv-bottom">
        <div className="inv-note">
          {user.invoiceNote && <p>{user.invoiceNote}</p>}
        </div>
        <dl className="inv-totals">
          <div><dt>Subtotal</dt><dd>{money(subtotal)}</dd></div>
          <div><dt>Delivery charge</dt><dd>{delivery > 0 ? money(delivery) : 'Free'}</dd></div>
          <div className="inv-total"><dt>Total</dt><dd>{money(total)}</dd></div>
          {pay.advance > 0 && pay.due > 0 && <div><dt>Paid in advance</dt><dd>{money(pay.advance)}</dd></div>}
          {pay.due > 0 && <div className="inv-due"><dt>{pay.label === 'Cash on delivery' ? 'To pay on delivery' : 'Amount due'}</dt><dd>{money(pay.due)}</dd></div>}
        </dl>
      </div>

      <footer className="inv-foot">Thank you for your order · {user.businessName}</footer>
    </article>
  )
}

/** For the parcel: who it goes to, what's inside (to tick off while packing) and how much to collect. No prices. */
function PackingSlip({ order, user }) {
  const total = Number(order.revenue) + Number(order.deliveryCharge)
  const pay = payment(order, total)
  const units = order.items.reduce((s, i) => s + i.quantity, 0)

  return (
    <article className="invoice slip">
      <header className="inv-head">
        <StoreBlock user={user} />
        <div className="inv-meta">
          <span className="inv-doc">Packing slip</span>
          <b>Order #{order.id}</b>
          <span>{longDate(order.orderedAt)}</span>
        </div>
      </header>

      <div className="slip-ship">
        <CustomerBlock order={order} title="Ship to" />
        <div className={`slip-collect ${pay.due > 0 ? 'due' : 'none'}`}>
          <span className="inv-label">{pay.due > 0 ? 'Collect from customer' : 'Collect'}</span>
          <b>{pay.due > 0 ? money(pay.due) : 'Nothing'}</b>
          <span>{pay.due > 0 ? pay.label : pay.label === 'Paid' ? 'Already paid' : pay.label}</span>
        </div>
      </div>

      {order.courier && (
        <div className="slip-courier">
          <span><span className="inv-label">Courier</span><b>{order.courier.courierName}</b></span>
          {order.courier.consignmentId && <span><span className="inv-label">Parcel ID</span><b>{order.courier.consignmentId}</b></span>}
          {order.courier.trackingNumber && !/^https?:\/\//i.test(order.courier.trackingNumber) && (
            <span><span className="inv-label">Tracking</span><b>{order.courier.trackingNumber}</b></span>
          )}
        </div>
      )}

      <table className="inv-items">
        <thead>
          <tr><th className="c-check" aria-label="Packed" /><th>Item</th><th className="num">Qty</th></tr>
        </thead>
        <tbody>
          {order.items.map((i) => (
            <tr key={i.productId}>
              <td className="c-check"><span className="tick-box" aria-hidden="true" /></td>
              <td>{i.productName}</td>
              <td className="num"><b>{i.quantity}</b></td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr><td /><td>{order.items.length} item{order.items.length === 1 ? '' : 's'}</td><td className="num"><b>{units}</b></td></tr>
        </tfoot>
      </table>

      <footer className="inv-foot">
        From {user.businessName}{user.businessPhone ? ` · ${user.businessPhone}` : ''}
      </footer>
    </article>
  )
}

export default function Invoice() {
  const { id } = useParams()
  const { user } = useAuth()
  const [params, setParams] = useSearchParams()
  const slip = params.get('type') === 'slip'
  const [order, setOrder] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.get(`/orders/${id}`).then((res) => setOrder(res.data)).catch((err) => setError(errorMessage(err)))
  }, [id])

  if (error) return <p className="error" role="alert">{error}</p>
  if (!order) return <p className="center-note">Loading…</p>

  const title = slip ? `Packing slip - Order ${order.id} - ${order.customerName}`
    : `Invoice ${invoiceNumber(order.id)} - ${order.customerName}`

  return (
    <section>
      <div className="page-head no-print">
        <div>
          <p className="eyebrow-plain"><Link to={`/orders/${order.id}`}>← Order #{order.id}</Link></p>
          <h1>{slip ? 'Packing slip' : 'Invoice'}</h1>
        </div>
        <div className="export-buttons">
          <div className="segmented" role="group" aria-label="Document">
            <button type="button" className={slip ? '' : 'on'} aria-pressed={!slip} onClick={() => setParams({})}>Invoice</button>
            <button type="button" className={slip ? 'on' : ''} aria-pressed={slip} onClick={() => setParams({ type: 'slip' })}>Packing slip</button>
          </div>
          <button type="button" className="btn" onClick={() => printAs(title)}>Print / Save as PDF</button>
        </div>
      </div>
      {!slip && !user.businessPhone && !user.businessAddress && user.role === 'OWNER' && (
        <p className="hint no-print inv-tip">
          Tip: add your shop&apos;s phone and address in <Link to="/settings">Settings</Link> so they appear on invoices.
        </p>
      )}
      {slip ? <PackingSlip order={order} user={user} /> : <InvoiceDoc order={order} user={user} />}
    </section>
  )
}
