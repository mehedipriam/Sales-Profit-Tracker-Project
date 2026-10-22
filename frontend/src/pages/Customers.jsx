import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import Modal from '../components/Modal'
import Pager from '../components/Pager'
import { MASK, useReveal } from '../components/Reveal'
import Stat from '../components/Stat'
import { ClockIcon, ReturnIcon, TrendUpIcon, WalletIcon } from '../components/icons'
import useDebounce from '../hooks/useDebounce'
import { dateTime, maskPhone, money } from '../utils/format'
import { tint } from '../utils/tint'

const EMPTY = { name: '', phone: '', address: '', sourcePlatformId: '', notes: '' }

function CustomerForm({ customer, platforms, onSaved, onCancel }) {
  const [form, setForm] = useState(
    customer ? { ...EMPTY, ...Object.fromEntries(Object.entries(customer).map(([k, v]) => [k, v ?? ''])) } : EMPTY,
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
      phone: form.phone,
      address: form.address,
      notes: form.notes,
      sourcePlatformId: form.sourcePlatformId === '' ? null : Number(form.sourcePlatformId),
    }
    try {
      if (customer) await api.put(`/customers/${customer.id}`, body)
      else await api.post('/customers', body)
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <label>Name<input required maxLength={150} value={form.name} onChange={set('name')} /></label>
      <div className="row">
        <label>Phone<input maxLength={32} value={form.phone} onChange={set('phone')} /></label>
        <label>
          Source platform
          <select value={form.sourcePlatformId} onChange={set('sourcePlatformId')}>
            <option value="">—</option>
            {platforms.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
          </select>
        </label>
      </div>
      <label>Address<input maxLength={500} value={form.address} onChange={set('address')} /></label>
      <label>Notes<textarea rows={3} maxLength={5000} value={form.notes} onChange={set('notes')} /></label>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button type="button" className="btn secondary" onClick={onCancel}>Cancel</button>
        <button className="btn" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
    </form>
  )
}

const PAGE_SIZE = 20
const GROUPS = [['', 'All'], ['repeat', 'Repeat'], ['new', 'New'], ['risk', 'Often returns']]

/** "2 of 5 orders" style breakdown of the returned / cancelled ones, for the return-rate tooltip. */
const problemsText = (ins) =>
  `${ins.returned} returned, ${ins.cancelled} cancelled out of ${ins.paid + ins.returned + ins.cancelled} settled orders`

export default function Customers() {
  const [q, setQ] = useState('')
  const [platformId, setPlatformId] = useState('')
  const [group, setGroup] = useState('')
  const [summary, setSummary] = useState(null)
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [platforms, setPlatforms] = useState([])
  const [editing, setEditing] = useState(null)
  const [error, setError] = useState('')
  const dq = useDebounce(q)
  // Phone, address and spent stay hidden until the eye is opened (same password or PIN as the other pages).
  const { shown, eye, prompt } = useReveal()
  const show = shown.all

  useEffect(() => {
    api.get('/platforms').then((res) => setPlatforms(res.data)).catch(() => {})
  }, [])

  const load = useCallback(() => {
    api
      .get('/customers', { params: { q: dq, platformId: platformId || undefined, group, page, size: PAGE_SIZE, sort: 'created' } })
      .then((res) => { setData(res.data); setError('') })
      .catch((err) => setError(errorMessage(err)))
    api.get('/customers/summary').then((res) => setSummary(res.data)).catch(() => {})
  }, [dq, platformId, group, page])

  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { load() }, [load])

  const platformName = (id) => platforms.find((p) => p.id === id)?.name ?? '—'
  const onSaved = () => { setEditing(null); load() }

  const remove = async (c) => {
    if (!window.confirm(`Delete customer "${c.name}"?`)) return
    try {
      await api.delete(`/customers/${c.id}`)
      load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Customers</h1>
          <p className="muted prod-sub">Who buys from you, how often and how much</p>
        </div>
        <span className="page-eye">{show ? 'Hide' : 'Show'} details{eye('all', 'customer details')}</span>
        <button className="btn" onClick={() => setEditing({})}>+ Add customer</button>
      </div>

      {summary && (
        <div className="stats kpis">
          <Stat label="Customers" value={summary.customers} icon={<TrendUpIcon />} accent="blue" />
          <Stat label="Repeat buyers" value={summary.repeat} hint="ordered 2 or more times" icon={<ReturnIcon />} accent="violet" />
          <Stat label="Total spent" value={show ? money(summary.totalSpent) : MASK} tone={show ? '' : 'masked'}
                hint="paid orders, with delivery" icon={<WalletIcon />} accent="green" />
          <Stat label="Often return" value={summary.oftenReturn} tone={summary.oftenReturn ? 'neg' : ''}
                hint="returns or cancels a lot" icon={<ClockIcon />} accent="red" />
        </div>
      )}

      <div className="rep-bar">
        <input
          className="prod-search"
          type="search"
          placeholder="Search name, phone or address…"
          value={q}
          onChange={(e) => { setQ(e.target.value); setPage(0) }}
        />
        <div className="dash-tabs" role="tablist" aria-label="Customer group">
          {GROUPS.map(([value, label]) => (
            <button key={value} type="button" role="tab" aria-selected={group === value}
                    className={group === value ? 'on' : ''} onClick={() => { setGroup(value); setPage(0) }}>{label}</button>
          ))}
        </div>
        <select aria-label="Platform" value={platformId} onChange={(e) => { setPlatformId(e.target.value); setPage(0) }}>
          <option value="">All platforms</option>
          {platforms.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
      </div>

      {error && <p className="error" role="alert">{error}</p>}

      <div className="dash-card prod-table">
        <table>
          <thead>
            <tr>
              <th>Customer</th><th>Platform</th><th className="num">Orders</th><th className="num">Spent</th>
              <th className="num">Return rate</th><th>Last order</th><th />
            </tr>
          </thead>
          <tbody>
            {data?.content.map((c) => (
              <tr key={c.id}>
                <td>
                  <div className="prod-name">
                    <span className="cust-av" style={tint(c.name)} aria-hidden="true">{c.name.trim().charAt(0).toUpperCase()}</span>
                    <div>
                      <b>
                        {c.name}
                        {c.insights.flagged ? <span className="badge risk" title={problemsText(c.insights)}>Often returns</span>
                          : c.insights.orders >= 2 ? <span className="badge repeat">Repeat</span>
                            : <span className="badge fresh">New</span>}
                      </b>
                      <small className={show ? '' : 'masked'}>
                        {show ? (c.phone || 'No phone') : maskPhone(c.phone)}
                        {' · '}{show ? (c.address || 'No address') : (c.address ? MASK : 'No address')}
                      </small>
                      {c.notes && <small className="cust-note" title={c.notes}>📝 {c.notes}</small>}
                    </div>
                  </div>
                </td>
                <td>{c.sourcePlatformId ? <span className="rep-pill">{platformName(c.sourcePlatformId)}</span> : <span className="muted">—</span>}</td>
                <td className="num">
                  {c.insights.orders > 0 ? (
                    <Link to={`/orders?customerId=${c.id}&customer=${encodeURIComponent(c.name)}`}
                          title={`Last order ${dateTime(c.insights.lastOrderAt)}`}>
                      {c.insights.orders}
                    </Link>
                  ) : '0'}
                </td>
                <td className={`num${show ? '' : ' masked'}`}>{show ? money(c.insights.spent) : MASK}</td>
                <td className="num" title={c.insights.returnRatePct == null ? 'No settled orders yet' : problemsText(c.insights)}>
                  {c.insights.returnRatePct == null ? <span className="muted">—</span>
                    : <span className={`prod-margin ${c.insights.flagged ? 'neg' : 'pos'}`}>{c.insights.returnRatePct}%</span>}
                </td>
                <td className="muted">{c.insights.lastOrderAt ? dateTime(c.insights.lastOrderAt) : '—'}</td>
                <td className="row-actions">
                  <button className="link" onClick={() => setEditing(c)}>Edit</button>
                  <button className="link danger" onClick={() => remove(c)}>Delete</button>
                </td>
              </tr>
            ))}
            {data && data.content.length === 0 && (
              <tr><td colSpan={7} className="empty">No customers found.</td></tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager data={data} onPage={setPage} />
      {prompt}

      {editing && (
        <Modal title={editing.id ? 'Edit customer' : 'Add customer'} onClose={() => setEditing(null)}>
          <CustomerForm
            customer={editing.id ? editing : null}
            platforms={platforms}
            onSaved={onSaved}
            onCancel={() => setEditing(null)}
          />
        </Modal>
      )}
    </section>
  )
}
