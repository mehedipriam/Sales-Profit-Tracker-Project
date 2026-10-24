import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { seesFinancials } from '../auth/roles'
import Modal from '../components/Modal'
import Stat from '../components/Stat'
import Tracking from '../components/Tracking'
import { ClockIcon, PencilIcon, TrashIcon, TruckIcon, WalletIcon } from '../components/icons'
import { toDateStr } from '../utils/dateRange'
import { dateTime, money } from '../utils/format'
import { tint } from '../utils/tint'

const daysSince = (iso) => Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 86400000))

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
    <div className="cour-block">
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
        <div className="dash-card"><p className="empty-note">Nothing waiting: every courier has paid out what it collected.</p></div>
      ) : (
        <>
          <div className="dash-card prod-table">
            <table className="pick-table">
              <thead>
                <tr>
                  <th className="check-col">
                    <input type="checkbox" aria-label="Select all" checked={allPicked}
                           onChange={() => setSelected(allPicked ? new Set() : new Set(rows.map((r) => r.id)))} />
                  </th>
                  <th>Order</th><th>Customer</th><th>Courier</th><th>Parcel / tracking</th>
                  <th className="num">Cash collected</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => {
                  const age = daysSince(r.orderedAt)
                  return (
                    <tr key={r.id} className={selected.has(r.id) ? 'picked' : ''} onClick={() => toggle(r.id)}>
                      <td className="check-col" onClick={(e) => e.stopPropagation()}>
                        <input type="checkbox" aria-label={`Select order ${r.id}`} checked={selected.has(r.id)}
                               onChange={() => toggle(r.id)} />
                      </td>
                      <td onClick={(e) => e.stopPropagation()}>
                        <Link to={`/orders/${r.id}`}><b>#{r.id}</b></Link>
                        <small className="muted cour-when">{dateTime(r.orderedAt)}</small>
                        <span className={`cour-age${age >= 7 ? ' late' : ''}`}>{age === 0 ? 'today' : `${age} day${age === 1 ? '' : 's'}`}</span>
                      </td>
                      <td>{r.customerName}</td>
                      <td><span className="rep-pill" style={tint(r.courierName)}>{r.courierName}</span></td>
                      <td onClick={(e) => e.stopPropagation()}>
                        {r.consignmentId && <span className="muted">{r.consignmentId}</span>}
                        {r.trackingNumber
                          ? <> <Tracking number={r.trackingNumber} link={r.trackingLink} /></>
                          : r.trackingLink && <> <a href={r.trackingLink} target="_blank" rel="noopener noreferrer">Track ↗</a></>}
                      </td>
                      <td className="num"><b>{money(r.codAmount)}</b></td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <div className="pay-bar">
            <span className="pay-sum">
              {picked.length === 0 ? 'Tick the orders the courier paid you for'
                : <><b>{money(pickedTotal)}</b> from {picked.length} order{picked.length === 1 ? '' : 's'}</>}
            </span>
            <label className="inline-label">
              Received on
              <input type="date" required value={paidOn} onChange={(e) => setPaidOn(e.target.value)} />
            </label>
            <button className="pay-go" disabled={busy || picked.length === 0 || !paidOn} onClick={markPaid}>
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
    <div className="cour-block">
      <div className="viz-head">
        <div>
          <h2 className="viz-title">Recently received</h2>
          <p className="muted viz-sub">The latest payouts you recorded · undo one entered by mistake</p>
        </div>
      </div>
      <div className="dash-card cour-recent">
        {rows.map((r) => (
          <div key={r.id} className="cour-recent-row">
            <span className="cour-tick" aria-hidden="true">✓</span>
            <div className="cour-recent-main">
              <b>{money(r.codAmount)}</b> from {r.courierName}
              <small className="muted">
                <Link to={`/orders/${r.id}`}>Order #{r.id}</Link> · {r.customerName} · received{' '}
                {new Date(`${r.paidOn}T00:00:00`).toLocaleDateString('en-GB', { dateStyle: 'medium' })}
              </small>
            </div>
            <button className="link" onClick={() => undo(r)}>Undo</button>
          </div>
        ))}
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
        <div>
          <h1>Couriers</h1>
          <p className="muted prod-sub">Your delivery services and the cash they owe you</p>
        </div>
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
      <div className="cour-grid">
        {couriers.map((c) => {
          const b = balanceById.get(c.id)
          const due = b && b.dueOrders > 0
          return (
            <div key={c.id} className="dash-card cour-card">
              <div className="cour-top">
                <span className="cust-av cour-tile" style={tint(c.name)} aria-hidden="true">{c.name.trim().charAt(0).toUpperCase()}</span>
                <b className="cour-name">{c.name}</b>
                <span className="cour-acts">
                  <button type="button" className="ord-act" title="Edit" aria-label={`Edit ${c.name}`} onClick={() => setEditing(c)}><PencilIcon /></button>
                  <button type="button" className="ord-act danger" title="Remove" aria-label={`Remove ${c.name}`} onClick={() => remove(c)}><TrashIcon /></button>
                </span>
              </div>
              {showMoney && (
                <div className="cour-two">
                  {due ? (
                    <button type="button" className="cour-mini due" title="Show only this courier's orders below"
                            onClick={() => setCourierId(String(c.id))}>
                      <span>Waiting for payout</span><b>{money(b.dueAmount)}</b><small>{b.dueOrders} order{b.dueOrders === 1 ? '' : 's'}</small>
                    </button>
                  ) : (
                    <div className="cour-mini"><span>Waiting for payout</span><b className="muted">—</b><small>nothing</small></div>
                  )}
                  <div className="cour-mini">
                    <span>On the way</span>
                    {b && b.transitOrders > 0
                      ? <><b>{money(b.transitAmount)}</b><small>{b.transitOrders} parcel{b.transitOrders === 1 ? '' : 's'}</small></>
                      : <><b className="muted">—</b><small>nothing</small></>}
                  </div>
                </div>
              )}
              <div className={`cour-track${c.trackingUrl ? ' ok' : ''}`} title={c.trackingUrl ?? ''}>
                {c.trackingUrl ?? 'No tracking link set'}
              </div>
            </div>
          )
        })}
        <button type="button" className="cour-add" onClick={() => setEditing({})}>
          <span>＋</span>
          {couriers.length === 0 ? 'No couriers yet. Add the ones you ship with.' : 'Add a courier'}
        </button>
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
