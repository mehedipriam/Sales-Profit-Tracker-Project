import { useCallback, useEffect, useState } from 'react'
import api, { errorMessage } from '../api/client'
import Modal from '../components/Modal'
import Stat from '../components/Stat'
import { ReceiptIcon, TruckIcon, UsersIcon, WalletIcon } from '../components/icons'
import { useAuth } from '../auth/AuthContext'
import { ROLE_LABELS } from '../auth/roles'
import { tint } from '../utils/tint'

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

// One line on each card; the full rules are in ROLE_HINTS, shown in the form and under the cards.
const ROLE_SHORT = {
  OWNER: 'Full access, including the business settings and every team member.',
  ADMIN: 'Sees everything an Owner does and manages Staff, but not the business settings.',
  STAFF: 'Records and manages sales day to day. Never sees profit, costs or reports.',
}
const ROLE_ICON = { OWNER: '👑 ', ADMIN: '🛡 ', STAFF: '' }
const FILTERS = [['', 'All'], ['OWNER', 'Owner'], ['ADMIN', 'Admin'], ['STAFF', 'Staff'], ['removed', 'Removed']]

export default function Team() {
  const { user } = useAuth()
  const isOwner = user.role === 'OWNER'
  // An Admin can only manage (and hand out) Staff; the api refuses anything else either way.
  const canManage = (m) => m.id === user.id || isOwner || m.role === 'STAFF'
  const assignable = isOwner ? ['STAFF', 'ADMIN', 'OWNER'] : ['STAFF']
  const [members, setMembers] = useState([])
  const [editing, setEditing] = useState(null)
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState('')
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

  const count = (role) => members.filter((m) => m.active && m.role === role).length
  const q = search.trim().toLowerCase()
  const shown = members.filter((m) => {
    if (filter === 'removed' ? m.active : !m.active && filter !== '') return false
    if (filter && filter !== 'removed' && m.role !== filter) return false
    return !q || m.fullName.toLowerCase().includes(q) || m.email.toLowerCase().includes(q)
  })

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Team</h1>
          <p className="muted prod-sub">Who can sign in to your business, and what they can do</p>
        </div>
        <button className="btn" onClick={() => setEditing({})}>+ Add member</button>
      </div>
      {error && <p className="error" role="alert">{error}</p>}

      <div className="stats kpis">
        <Stat label="Members" value={members.filter((m) => m.active).length} icon={<UsersIcon />} accent="blue"
              hint={members.some((m) => !m.active) ? `${members.filter((m) => !m.active).length} removed` : 'everyone who can sign in'} />
        <Stat label="Owners" value={count('OWNER')} icon={<WalletIcon />} accent="amber" />
        <Stat label="Admins" value={count('ADMIN')} icon={<ReceiptIcon />} accent="violet" />
        <Stat label="Staff" value={count('STAFF')} icon={<TruckIcon />} accent="teal" />
      </div>

      <div className="rep-bar">
        <input type="search" className="prod-search" value={search} placeholder="Search name or email…"
               aria-label="Search the team by name or email" onChange={(e) => setSearch(e.target.value)} />
        <div className="dash-tabs" role="tablist" aria-label="Role">
          {FILTERS.map(([value, label]) => (
            <button key={value} type="button" role="tab" aria-selected={filter === value}
                    className={filter === value ? 'on' : ''} onClick={() => setFilter(value)}>{label}</button>
          ))}
        </div>
      </div>

      <div className="cour-grid">
        {shown.map((m) => (
          <div key={m.id} className={`dash-card cour-card${m.active ? '' : ' team-off'}`}>
            <div className="cour-top">
              <span className="cust-av team-av" style={m.active ? tint(m.fullName) : undefined} aria-hidden="true">
                {m.fullName.trim().charAt(0).toUpperCase()}
              </span>
              <div className="team-who">
                <b>{m.fullName}{m.id === user.id && <span className="team-you">You</span>}</b>
                <small className="muted">{m.email}</small>
              </div>
            </div>
            <div className="team-row">
              <span className={`team-role ${m.role}`}>{ROLE_ICON[m.role]}{ROLE_LABELS[m.role]}</span>
              <span className={`team-status${m.active ? '' : ' off'}`}><i />{m.active ? 'Active' : 'Deactivated'}</span>
            </div>
            <p className="team-can">{m.active ? ROLE_SHORT[m.role] : 'Cannot sign in. Their sales history stays.'}</p>
            <div className="team-acts">
              {canManage(m) && <button className="link" onClick={() => setEditing(m)}>Edit</button>}
              {m.role !== 'OWNER' && m.id !== user.id && canManage(m) && m.active && (
                <button className="link danger" onClick={() => deactivate(m)}>Remove access</button>
              )}
            </div>
          </div>
        ))}
        <button type="button" className="cour-add" onClick={() => setEditing({})}>
          <span>＋</span>
          {members.length === 0 ? 'No team members yet.' : 'Add a team member'}
        </button>
      </div>
      {shown.length === 0 && members.length > 0 && <p className="empty-note">No one matches these filters.</p>}

      <div className="team-roles">
        {['OWNER', 'ADMIN', 'STAFF'].map((r) => (
          <div key={r} className="dash-card">
            <h3 className="team-roles-title">{ROLE_ICON[r]}{ROLE_LABELS[r]}</h3>
            <p>{ROLE_HINTS[r]}</p>
          </div>
        ))}
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
