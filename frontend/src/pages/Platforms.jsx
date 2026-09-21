import { useCallback, useEffect, useState } from 'react'
import api, { errorMessage } from '../api/client'
import Modal from '../components/Modal'

function PlatformForm({ platform, onSaved, onCancel }) {
  const [name, setName] = useState(platform?.name ?? '')
  const [commissionPct, setCommissionPct] = useState(platform?.commissionPct ?? 0)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    const body = { name, commissionPct: commissionPct === '' ? 0 : commissionPct }
    try {
      if (platform) await api.put(`/platforms/${platform.id}`, body)
      else await api.post('/platforms', body)
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <label>Platform name<input required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} /></label>
      <label>
        Commission % (optional)
        <input type="number" min="0" max="100" step="0.01" value={commissionPct}
               onChange={(e) => setCommissionPct(e.target.value)} />
        <span className="hint">
          Charged automatically as an expense on every new order on this platform (while it is paid or pending).
          Changing it later affects new orders only.
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

export default function Platforms() {
  const [platforms, setPlatforms] = useState([])
  const [editing, setEditing] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    api.get('/platforms').then((res) => setPlatforms(res.data)).catch((err) => setError(errorMessage(err)))
  }, [])
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { load() }, [load])

  const remove = async (p) => {
    if (!window.confirm(`Remove "${p.name}"? Existing orders keep it; you just can't pick it for new sales.`)) return
    try {
      await api.delete(`/platforms/${p.id}`)
      load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <section>
      <div className="page-head">
        <h1>Platforms</h1>
        <button className="btn" onClick={() => setEditing({})}>+ Add platform</button>
      </div>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="table-wrap">
        <table>
          <thead><tr><th>Name</th><th className="num">Commission</th><th /></tr></thead>
          <tbody>
            {platforms.map((p) => (
              <tr key={p.id}>
                <td>{p.name}</td>
                <td className="num">{Number(p.commissionPct)}%</td>
                <td className="row-actions">
                  <button className="link" onClick={() => setEditing(p)}>Edit</button>
                  <button className="link danger" onClick={() => remove(p)}>Remove</button>
                </td>
              </tr>
            ))}
            {platforms.length === 0 && <tr><td colSpan={3} className="empty">No platforms yet.</td></tr>}
          </tbody>
        </table>
      </div>
      {editing && (
        <Modal title={editing.id ? 'Edit platform' : 'Add platform'} onClose={() => setEditing(null)}>
          <PlatformForm
            platform={editing.id ? editing : null}
            onSaved={() => { setEditing(null); load() }}
            onCancel={() => setEditing(null)}
          />
        </Modal>
      )}
    </section>
  )
}
