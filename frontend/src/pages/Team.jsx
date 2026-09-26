import { useCallback, useEffect, useState } from 'react'
import api, { errorMessage } from '../api/client'
import Modal from '../components/Modal'
import { useAuth } from '../auth/AuthContext'
import { ROLE_LABELS } from '../auth/roles'

const ROLE_HINTS = {
  OWNER: 'Owners have full access, including the business settings (store name, currency) and managing every team '
    + 'member.',
  ADMIN: 'Admins see everything an Owner does (dashboard, reports, expenses, cost prices, profit, commission rates) '
    + "and can manage Staff, but can't change the business settings or add, edit or remove Owners and Admins.",
  STAFF: "Staff can record and manage sales day to day (products, customers, platforms, orders, stock) but never see "
    + "the dashboard, reports, expenses, cost prices or profit figures, and can't change a platform's commission "
    + 'rate or manage the team.',
}

function StaffForm({ member, isSelf, roles, onSaved, onCancel }) {
  const [fullName, setFullName] = useState(member?.fullName ?? '')
  const [email, setEmail] = useState(member?.email ?? '')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState(member?.role ?? 'STAFF')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      if (member) await api.put(`/users/${member.id}`, { fullName, password: password || undefined, role })
      else await api.post('/users', { fullName, email, password, role })
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
      <label>
        Role
        <select value={role} onChange={(e) => setRole(e.target.value)} disabled={isSelf || roles.length < 2}>
          {roles.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
        </select>
      </label>
      <p className="hint">{isSelf ? "You can't change your own role." : ROLE_HINTS[role]}</p>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button type="button" className="btn secondary" onClick={onCancel}>Cancel</button>
        <button className="btn" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
    </form>
  )
}

export default function Team() {
  const { user } = useAuth()
  const isOwner = user.role === 'OWNER'
  // An Admin can only manage (and hand out) Staff; the api refuses anything else either way.
  const canManage = (m) => m.id === user.id || isOwner || m.role === 'STAFF'
  const assignable = isOwner ? ['STAFF', 'ADMIN', 'OWNER'] : ['STAFF']
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
        <button className="btn" onClick={() => setEditing({})}>+ Add member</button>
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
                <td>{ROLE_LABELS[m.role]}</td>
                <td>{m.active ? 'Active' : <span className="muted">Deactivated</span>}</td>
                <td className="row-actions">
                  {canManage(m) && <button className="link" onClick={() => setEditing(m)}>Edit</button>}
                  {m.role !== 'OWNER' && m.id !== user.id && canManage(m) && m.active && (
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
        <Modal title={editing.id ? `Edit ${editing.fullName}` : 'Add team member'} onClose={() => setEditing(null)}>
          <StaffForm
            member={editing.id ? editing : null}
            isSelf={editing.id === user.id}
            roles={editing.id === user.id ? [editing.role] : assignable}
            onSaved={() => { setEditing(null); load() }}
            onCancel={() => setEditing(null)}
          />
        </Modal>
      )}
    </section>
  )
}
