import { useCallback, useEffect, useState } from 'react'
import api, { errorMessage } from '../api/client'
import Modal from '../components/Modal'

function StaffForm({ member, onSaved, onCancel }) {
  const [fullName, setFullName] = useState(member?.fullName ?? '')
  const [email, setEmail] = useState(member?.email ?? '')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      if (member) await api.put(`/users/${member.id}`, { fullName, password: password || undefined })
      else await api.post('/users', { fullName, email, password })
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <label>Full name<input required maxLength={150} value={fullName} onChange={(e) => setFullName(e.target.value)} /></label>
      {!member && (
        <label>Email<input required type="email" maxLength={190} value={email} onChange={(e) => setEmail(e.target.value)} /></label>
      )}
      <label>
        {member ? 'New password (optional)' : 'Password'}
        <input required={!member} type="password" minLength={8} maxLength={72} value={password}
               onChange={(e) => setPassword(e.target.value)} placeholder={member ? 'Leave blank to keep it unchanged' : ''} />
      </label>
      {!member && (
        <p className="hint">
          Staff can record and manage sales day to day (products, customers, platforms, orders, stock) but never see
          the dashboard, reports, expenses, cost prices or profit figures, and can't change a platform's commission
          rate or manage the team.
        </p>
      )}
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button type="button" className="btn secondary" onClick={onCancel}>Cancel</button>
        <button className="btn" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
    </form>
  )
}

export default function Team() {
  const [members, setMembers] = useState([])
  const [editing, setEditing] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    api.get('/users').then((res) => setMembers(res.data)).catch((err) => setError(errorMessage(err)))
  }, [])
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { load() }, [load])

  const deactivate = async (m) => {
    if (!window.confirm(`Remove ${m.fullName}'s access? Their sales history stays.`)) return
    try {
      await api.delete(`/users/${m.id}`)
      load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <section>
      <div className="page-head">
        <h1>Team</h1>
        <button className="btn" onClick={() => setEditing({})}>+ Add staff</button>
      </div>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="table-wrap">
        <table>
          <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th /></tr></thead>
          <tbody>
            {members.map((m) => (
              <tr key={m.id}>
                <td>{m.fullName}</td>
                <td>{m.email}</td>
                <td>{m.role === 'OWNER' ? 'Owner' : 'Staff'}</td>
                <td>{m.active ? 'Active' : <span className="muted">Deactivated</span>}</td>
                <td className="row-actions">
                  <button className="link" onClick={() => setEditing(m)}>Edit</button>
                  {m.role === 'STAFF' && m.active && (
                    <button className="link danger" onClick={() => deactivate(m)}>Remove access</button>
                  )}
                </td>
              </tr>
            ))}
            {members.length === 0 && <tr><td colSpan={5} className="empty">No team members yet.</td></tr>}
          </tbody>
        </table>
      </div>
      {editing && (
        <Modal title={editing.id ? `Edit ${editing.fullName}` : 'Add staff'} onClose={() => setEditing(null)}>
          <StaffForm
            member={editing.id ? editing : null}
            onSaved={() => { setEditing(null); load() }}
            onCancel={() => setEditing(null)}
          />
        </Modal>
      )}
    </section>
  )
}
