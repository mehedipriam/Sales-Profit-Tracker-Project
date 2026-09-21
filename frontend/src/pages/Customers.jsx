import { useCallback, useEffect, useState } from 'react'
import api, { errorMessage } from '../api/client'
import Modal from '../components/Modal'
import Pager from '../components/Pager'
import useDebounce from '../hooks/useDebounce'

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

export default function Customers() {
  const [q, setQ] = useState('')
  const [platformId, setPlatformId] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [platforms, setPlatforms] = useState([])
  const [editing, setEditing] = useState(null)
  const [error, setError] = useState('')
  const dq = useDebounce(q)

  useEffect(() => {
    api.get('/platforms').then((res) => setPlatforms(res.data)).catch(() => {})
  }, [])

  const load = useCallback(() => {
    api
      .get('/customers', { params: { q: dq, platformId: platformId || undefined, page } })
      .then((res) => { setData(res.data); setError('') })
      .catch((err) => setError(errorMessage(err)))
  }, [dq, platformId, page])

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
        <h1>Customers</h1>
        <button className="btn" onClick={() => setEditing({})}>+ Add customer</button>
      </div>

      <div className="filters">
        <input
          type="search"
          placeholder="Search name, phone or address…"
          value={q}
          onChange={(e) => { setQ(e.target.value); setPage(0) }}
        />
        <select value={platformId} onChange={(e) => { setPlatformId(e.target.value); setPage(0) }}>
          <option value="">All platforms</option>
          {platforms.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
      </div>

      {error && <p className="error" role="alert">{error}</p>}

      <div className="table-wrap">
        <table>
          <thead>
            <tr><th>Name</th><th>Phone</th><th>Address</th><th>Platform</th><th>Notes</th><th /></tr>
          </thead>
          <tbody>
            {data?.content.map((c) => (
              <tr key={c.id}>
                <td>{c.name}</td>
                <td>{c.phone || '—'}</td>
                <td>{c.address || '—'}</td>
                <td>{platformName(c.sourcePlatformId)}</td>
                <td className="notes">{c.notes || '—'}</td>
                <td className="row-actions">
                  <button className="link" onClick={() => setEditing(c)}>Edit</button>
                  <button className="link danger" onClick={() => remove(c)}>Delete</button>
                </td>
              </tr>
            ))}
            {data && data.content.length === 0 && (
              <tr><td colSpan={6} className="empty">No customers found.</td></tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager data={data} onPage={setPage} />

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
