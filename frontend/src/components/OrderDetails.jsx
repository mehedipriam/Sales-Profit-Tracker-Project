import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { seesFinancials } from '../auth/roles'
import { STATUS_LABEL, dateTime, maskPhone, money } from '../utils/format'
import Modal from './Modal'
import { EyeHead, MASK, useReveal } from './Reveal'
import Tracking from './Tracking'

const day = (iso) => new Date(`${iso}T00:00:00`).toLocaleDateString('en-GB', { dateStyle: 'medium' })

/**
 * Everything about one order in a read-only popup: who and where, the courier, each line, and the totals. Phone and
 * address sit behind an eye like on the Customers page; cost and profit behind theirs, and only for Owner and Admin.
 */
export default function OrderDetails({ id, onClose }) {
  const { user } = useAuth()
  const showMoney = seesFinancials(user)
  const { shown, eye, prompt } = useReveal()
  const [order, setOrder] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let live = true
    api.get(`/orders/${id}`)
      .then((res) => { if (live) setOrder(res.data) })
      .catch((err) => { if (live) setError(errorMessage(err)) })
    return () => { live = false }
  }, [id])

  const o = order
  const delivery = Number(o?.deliveryCharge ?? 0)
  const expenseTotal = (o?.expenses ?? []).reduce((sum, e) => sum + Number(e.amount), 0)
  // What the courier keeps is already in the expenses, so it is shown with the delivery and left out of "other".
  const courierKeeps = Number(o?.courier?.deliveryFee ?? 0) + Number(o?.courier?.codFee ?? 0)
  const deliveryEarns = o?.courier ? delivery - courierKeeps : 0 // only a courier's charges are known
  const otherExpenses = Math.max(expenseTotal - courierKeeps, 0)

  return (
    <Modal title={o ? `Order ${o.orderNo}` : 'Order'} onClose={onClose} wide>
      {error && <p className="error" role="alert">{error}</p>}
      {!o && !error && <p className="muted">Loading…</p>}
      {o && (
        <div className="od">
          <div className="od-top">
            <span className={`badge ${o.status}`}>{STATUS_LABEL[o.status]}</span>
            <span className="muted">{dateTime(o.orderedAt)} · {o.platformName}</span>
          </div>

          <div className="od-grid">
            <div className="od-box">
              <h3>Customer {eye('contact', 'phone and address')}</h3>
              <b>{o.customerName}</b>
              <span>{shown.contact ? (o.customerPhone || '—') : maskPhone(o.customerPhone)}</span>
              <span className={shown.contact ? '' : 'masked'}>
                {shown.contact ? (o.customerAddress || '—') : (o.customerAddress ? MASK : '—')}
              </span>
            </div>
            <div className="od-box">
              <h3>Delivery</h3>
              {o.courier ? (
                <>
                  <b>{o.courier.courierName}</b>
                  {o.courier.consignmentId && <span>Consignment {o.courier.consignmentId}</span>}
                  {o.courier.trackingNumber
                    ? <span>Tracking: <Tracking number={o.courier.trackingNumber} link={o.courier.trackingLink} /></span>
                    : o.courier.trackingLink && <a href={o.courier.trackingLink} target="_blank" rel="noopener noreferrer">Track parcel ↗</a>}
                  <span>Cash to collect {money(o.courier.codAmount)}
                    {o.courier.paidOn ? ` · paid out ${day(o.courier.paidOn)}` : ' · courier still holds it'}</span>
                  {o.courier.youReceive != null && Number(o.courier.deliveryFee) + Number(o.courier.codFee) > 0 && (
                    <span>
                      Courier keeps {money(Number(o.courier.deliveryFee) + Number(o.courier.codFee))}
                      {' '}(delivery {money(o.courier.deliveryFee)} + COD {money(o.courier.codFee)}) · you receive {money(o.courier.youReceive)}
                    </span>
                  )}
                </>
              ) : <span className="muted">No courier (handed over directly)</span>}
            </div>
          </div>

          <div className="table-wrap">
            <table className="striped od-items">
              <thead>
                <tr>
                  <th>Product</th><th className="num">Qty</th><th className="num">Price</th><th className="num">Total</th>
                  {showMoney && <>
                    <th className="num"><EyeHead eye={eye('cost', 'costs')}>Cost</EyeHead></th>
                    <th className="num"><EyeHead eye={eye('profit', 'profits')}>Profit</EyeHead></th>
                  </>}
                </tr>
              </thead>
              <tbody>
                {o.items.map((i) => (
                  <tr key={i.productId}>
                    <td>{i.productName}</td>
                    <td className="num">{i.quantity}</td>
                    <td className="num">{money(i.soldPrice)}</td>
                    <td className="num">{money(i.lineRevenue)}</td>
                    {showMoney && <>
                      <td className={`num${shown.cost ? '' : ' masked'}`}>{shown.cost ? money(i.lineCost) : MASK}</td>
                      <td className={`num${shown.profit ? '' : ' masked'}`}>{shown.profit ? money(i.lineProfit) : MASK}</td>
                    </>}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="od-totals">
            <span>Products</span><span className="num">{money(o.revenue)}</span>
            {delivery > 0 && <><span>Delivery charge</span><span className="num">{money(delivery)}</span></>}
            <span className="total">Customer pays</span><span className="num total">{money(Number(o.revenue) + delivery)}</span>
            {showMoney && <>
              <span className="muted">Cost of goods</span>
              <span className={`num${shown.cost ? '' : ' masked'}`}>{shown.cost ? money(o.cost) : MASK}</span>
              <span className="muted">Profit on products</span>
              <span className={`num${shown.profit ? '' : ' masked'}`}>{shown.profit ? money(o.profit) : MASK}</span>
              {o.courier && (delivery > 0 || courierKeeps > 0) && <>
                <span className="muted">{deliveryEarns >= 0 ? 'Delivery earns you' : 'Delivery costs you'}
                  {courierKeeps > 0 && ` (${money(delivery)} charged − ${money(courierKeeps)} to the courier)`}</span>
                <span className={`num${shown.profit ? '' : ' masked'}`}>{shown.profit ? money(deliveryEarns) : MASK}</span>
              </>}
              <span className="total">Profit on this order</span>
              <span className={`num total${shown.profit ? '' : ' masked'}`}>{shown.profit ? money(Number(o.profit) + deliveryEarns) : MASK}</span>
              {otherExpenses > 0 && <>
                <span className="muted">Other expenses on this order (ads, commission…)</span>
                <span className={`num${shown.cost ? '' : ' masked'}`}>{shown.cost ? money(otherExpenses) : MASK}</span>
              </>}
            </>}
          </div>

          {o.notes && <p className="od-notes"><b>Notes:</b> {o.notes}</p>}

          <div className="actions">
            <button type="button" className="btn secondary" onClick={onClose}>Close</button>
            <Link className="btn secondary" to={`/orders/${o.id}/invoice`}>Invoice</Link>
            <Link className="btn" to={`/orders/${o.id}`}>Edit order</Link>
          </div>
        </div>
      )}
      {prompt}
    </Modal>
  )
}
