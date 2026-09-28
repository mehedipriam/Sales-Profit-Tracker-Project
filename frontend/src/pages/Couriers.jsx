import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { seesFinancials } from '../auth/roles'
import Modal from '../components/Modal'
import Stat from '../components/Stat'
import Tracking from '../components/Tracking'
import { ClockIcon, TruckIcon, WalletIcon } from '../components/icons'
import { toDateStr } from '../utils/dateRange'
import { dateTime, money } from '../utils/format'

function CourierForm({ courier, onSaved, onCancel }) {
  const [name, setName] = useState(courier?.name ?? '')
  const [trackingUrl, setTrackingUrl] = useState(courier?.trackingUrl ?? '')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    const body = { name, trackingUrl }
    try {
      if (courier) await api.put(`/couriers/${courier.id}`, body)
      else await api.post('/couriers', body)
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <label>Courier name<input required maxLength={100} value={name} placeholder="e.g. Pathao, Karatoa Courier…"
                                onChange={(e) => setName(e.target.value)} /></label>
      <label>
        Tracking link (optional)
        <input type="url" maxLength={300} value={trackingUrl} placeholder="https://…/track/{tracking}"
               onChange={(e) => setTrackingUrl(e.target.value)} />
        <span className="hint">
          The courier&apos;s parcel tracking page. Put <code>{'{tracking}'}</code> where the tracking number goes and every
          order with a tracking number gets a one-click link.
        </span>
      </label>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button type="button" className="btn secondary" onClick={onCancel}>Cancel</button>
        <button className="btn" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
    </form>
  )
}

/** Delivered orders whose cash a courier still holds: tick the ones it paid out and record the payout. */
function PayoutsDue({ rows, couriers, courierId, onCourier, onDone, setError }) {
  const [selected, setSelected] = useState(() => new Set())
  const [paidOn, setPaidOn] = useState(toDateStr(new Date()))
  const [busy, setBusy] = useState(false)

  const picked = rows.filter((r) => selected.has(r.id))
  const pickedTotal = picked.reduce((s, r) => s + Number(r.codAmount), 0)
  const allPicked = rows.length > 0 && picked.length === rows.length
  const toggle = (id) => setSelected((s) => {
    const next = new Set(s)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    return next
  })

  const markPaid = async () => {
    setBusy(true)
    try {
      await api.post('/couriers/payouts', { orderIds: picked.map((r) => r.id), paidOn })
      setSelected(new Set())
      onDone()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="viz-card">
      <div className="viz-head">
        <div>
          <h2 className="viz-title">Waiting for payout</h2>
          <p className="muted viz-sub">Delivered orders whose cash the courier collected but hasn&apos;t sent you yet · oldest first</p>
        </div>
        <select aria-label="Courier" value={courierId} onChange={(e) => { setSelected(new Set()); onCourier(e.target.value) }}>
          <option value="">All couriers</option>
          {couriers.map((c) => <option key={c.courierId} value={c.courierId}>{c.name}</option>)}
        </select>
      </div>

      {rows.length === 0 ? (
        <p className="empty-note">Nothing waiting: every courier has paid out what it collected.</p>
      ) : (
        <>
          <div className="table-wrap">
            <table className="striped pick-table">
              <thead>
                <tr>
                  <th className="check-col">
                    <input type="checkbox" aria-label="Select all" checked={allPicked}
                           onChange={() => setSelected(allPicked ? new Set() : new Set(rows.map((r) => r.id)))} />
                  </th>
                  <th>Order</th><th>Date</th><th>Customer</th><th>Courier</th><th>Parcel ID</th><th>Tracking</th>
                  <th className="num">Cash collected</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.id} className={selected.has(r.id) ? 'picked' : ''} onClick={() => toggle(r.id)}>
                    <td className="check-col" onClick={(e) => e.stopPropagation()}>
                      <input type="checkbox" aria-label={`Select order ${r.id}`} checked={selected.has(r.id)}
                             onChange={() => toggle(r.id)} />
                    </td>
                    <td onClick={(e) => e.stopPropagation()}><Link to={`/orders/${r.id}`}>#{r.id}</Link></td>
                    <td className="nowrap">{dateTime(r.orderedAt)}</td>
                    <td>{r.customerName}</td>
                    <td>{r.courierName}</td>
                    <td>{r.consignmentId}</td>
                    <td onClick={(e) => e.stopPropagation()}>
                      {r.trackingNumber
                        ? <Tracking number={r.trackingNumber} link={r.trackingLink} />
                        : r.trackingLink && <a href={r.trackingLink} target="_blank" rel="noopener noreferrer">Track ↗</a>}
                    </td>
                    <td className="num"><b>{money(r.codAmount)}</b></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="payout-bar">
            <span className="payout-sum">
              {picked.length === 0 ? 'Tick the orders the courier paid you for'
                : <><b>{money(pickedTotal)}</b> from {picked.length} order{picked.length === 1 ? '' : 's'}</>}
            </span>
            <label className="inline-label">
              Received on
              <input type="date" required value={paidOn} onChange={(e) => setPaidOn(e.target.value)} />
            </label>
            <button className="btn" disabled={busy || picked.length === 0 || !paidOn} onClick={markPaid}>
              {busy ? 'Saving…' : 'Mark as received'}
            </button>
          </div>
        </>
      )}
    </div>
  )
}

function RecentPayouts({ rows, onDone, setError }) {
  const undo = async (r) => {
    if (!window.confirm(`Undo the payout for order #${r.id}? Its cash will count as still with ${r.courierName}.`)) return
    try {
      await api.post('/couriers/payouts/undo', { orderIds: [r.id] })
      onDone()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  if (rows.length === 0) return null
  return (
    <div className="viz-card">
      <div className="viz-head">
        <div>
          <h2 className="viz-title">Recently received</h2>
          <p className="muted viz-sub">The latest payouts you recorded · undo one entered by mistake</p>
        </div>
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr><th>Received</th><th>Order</th><th>Customer</th><th>Courier</th><th className="num">Amount</th><th /></tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <td className="nowrap">{new Date(`${r.paidOn}T00:00:00`).toLocaleDateString('en-GB', { dateStyle: 'medium' })}</td>
                <td><Link to={`/orders/${r.id}`}>#{r.id}</Link></td>
                <td>{r.customerName}</td>
                <td>{r.courierName}</td>
                <td className="num">{money(r.codAmount)}</td>
                <td className="row-actions"><button className="link" onClick={() => undo(r)}>Undo</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export default function Couriers() {
  const { user } = useAuth()
  const showMoney = seesFinancials(user)
  const [couriers, setCouriers] = useState([])
  const [payouts, setPayouts] = useState(null)
  const [courierId, setCourierId] = useState('')
  const [editing, setEditing] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    api.get('/couriers').then((res) => setCouriers(res.data)).catch((err) => setError(errorMessage(err)))
    if (showMoney) {
      api.get('/couriers/payouts', { params: { courierId: courierId || undefined } })
        .then((res) => setPayouts(res.data)).catch((err) => setError(errorMessage(err)))
    }
  }, [showMoney, courierId])
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { load() }, [load])

  const balanceById = useMemo(() => new Map((payouts?.couriers ?? []).map((b) => [b.courierId, b])), [payouts])

  const remove = async (c) => {
    const owed = balanceById.get(c.id)
    const note = owed && Number(owed.dueAmount) + Number(owed.transitAmount) > 0
      ? ` Its unpaid cash still shows under "Waiting for payout".` : ''
    if (!window.confirm(`Remove "${c.name}"? Existing orders keep it; you just can't pick it for new orders.${note}`)) return
    try {
      await api.delete(`/couriers/${c.id}`)
      load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <section>
      <div className="page-head">
        <h1>Couriers</h1>
        <button className="btn" onClick={() => setEditing({})}>+ Add courier</button>
      </div>
      {error && <p className="error" role="alert">{error}</p>}

      {showMoney && payouts && (
        <div className="stats kpis">
          <Stat label="Cash with couriers" value={money(payouts.dueTotal)} accent="blue" icon={<WalletIcon />}
                hint={`${payouts.dueOrders} delivered order${payouts.dueOrders === 1 ? '' : 's'} not paid out yet`} />
          <Stat label="On the way" value={money(payouts.transitTotal)} accent="amber" icon={<ClockIcon />}
                hint={`cash to collect on ${payouts.transitOrders} pending parcel${payouts.transitOrders === 1 ? '' : 's'}`} />
          <Stat label="Couriers" value={couriers.length} accent="teal" icon={<TruckIcon />}
                hint="add any courier service you use" />
        </div>
      )}

      <h2 className="section-title">Your couriers</h2>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Name</th>
              {showMoney && <><th className="num">Waiting for payout</th><th className="num">On the way</th></>}
              <th>Tracking link</th><th />
            </tr>
          </thead>
          <tbody>
            {couriers.map((c) => {
              const b = balanceById.get(c.id)
              return (
                <tr key={c.id}>
                  <td><b>{c.name}</b></td>
                  {showMoney && <>
                    <td className="num">
                      {b && b.dueOrders > 0
                        ? <button className="link strong" onClick={() => setCourierId(String(c.id))}>{money(b.dueAmount)} · {b.dueOrders}</button>
                        : <span className="muted">—</span>}
                    </td>
                    <td className="num">{b && b.transitOrders > 0 ? `${money(b.transitAmount)} · ${b.transitOrders}` : <span className="muted">—</span>}</td>
                  </>}
                  <td className="notes" title={c.trackingUrl ?? ''}>{c.trackingUrl ?? <span className="muted">Not set</span>}</td>
                  <td className="row-actions">
                    <button className="link" onClick={() => setEditing(c)}>Edit</button>
                    <button className="link danger" onClick={() => remove(c)}>Remove</button>
                  </td>
                </tr>
              )
            })}
            {couriers.length === 0 && (
              <tr><td colSpan={showMoney ? 5 : 3} className="empty">No couriers yet. Add the ones you ship with.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {showMoney && payouts && (
        <>
          <PayoutsDue rows={payouts.due} couriers={payouts.couriers} courierId={courierId} onCourier={setCourierId}
                      onDone={load} setError={setError} />
          <RecentPayouts rows={payouts.recent} onDone={load} setError={setError} />
        </>
      )}

      {editing && (
        <Modal title={editing.id ? 'Edit courier' : 'Add courier'} onClose={() => setEditing(null)}>
          <CourierForm
            courier={editing.id ? editing : null}
            onSaved={() => { setEditing(null); load() }}
            onCancel={() => setEditing(null)}
          />
        </Modal>
      )}
    </section>
  )
}
