import { useState } from 'react'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { CURRENCIES, money } from '../utils/format'

/** A settings card: its own error/success message and busy state around one save. */
function useSave() {
  const [error, setError] = useState('')
  const [done, setDone] = useState('')
  const [busy, setBusy] = useState(false)

  const run = async (e, action, message) => {
    e.preventDefault()
    setError('')
    setDone('')
    setBusy(true)
    try {
      await action()
      setDone(message)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }
  const fail = (message) => {
    setDone('')
    setError(message)
  }
  return { error, done, busy, run, fail }
}

function Feedback({ save }) {
  return <>
    {save.error && <p className="error" role="alert">{save.error}</p>}
    {save.done && <p className="success" role="status">{save.done}</p>}
  </>
}

function BusinessCard({ user, setSession }) {
  const [name, setName] = useState(user.businessName)
  const [currency, setCurrencyCode] = useState(user.currency ?? 'BDT')
  const known = CURRENCIES.some(([code]) => code === currency)
  const save = useSave()
  const submit = (e) => save.run(e, async () => {
    const { data } = await api.put('/account/business', { name, currency })
    setSession({ user: data })
  }, 'Store settings saved.')

  return (
    <form className="card" onSubmit={submit}>
      <h2>Store</h2>
      <label>Store name<input required maxLength={150} value={name} onChange={(e) => setName(e.target.value)} /></label>
      <label>
        Currency
        <select value={currency} onChange={(e) => setCurrencyCode(e.target.value)}>
          {!known && <option value={currency}>{currency}</option>}
          {CURRENCIES.map(([code, label]) => <option key={code} value={code}>{code} · {label}</option>)}
        </select>
        <span className="hint">
          Every amount in the app is shown in this currency, e.g. {money(1440)} after saving. Amounts already recorded
          are not converted - only the symbol changes.
        </span>
      </label>
      <Feedback save={save} />
      <div className="actions"><button className="btn" disabled={save.busy}>{save.busy ? 'Saving…' : 'Save'}</button></div>
    </form>
  )
}

function ProfileCard({ user, setSession }) {
  const [fullName, setFullName] = useState(user.fullName)
  const [email, setEmail] = useState(user.email)
  const [currentPassword, setCurrentPassword] = useState('')
  const emailChanged = email.trim().toLowerCase() !== user.email
  const save = useSave()
  const submit = (e) => save.run(e, async () => {
    const { data } = await api.put('/account/profile', { fullName, email, currentPassword: currentPassword || null })
    setSession(data)
    setCurrentPassword('')
  }, 'Profile saved.')

  return (
    <form className="card" onSubmit={submit}>
      <h2>Your profile</h2>
      <label>Full name<input required maxLength={150} value={fullName} onChange={(e) => setFullName(e.target.value)} /></label>
      <label>
        Email
        <input required type="email" maxLength={190} value={email} onChange={(e) => setEmail(e.target.value)} />
        <span className="hint">You log in with this email.</span>
      </label>
      {emailChanged && (
        <label>
          Current password
          <input required type="password" autoComplete="current-password" value={currentPassword}
                 onChange={(e) => setCurrentPassword(e.target.value)} />
          <span className="hint">Needed to change the email you log in with.</span>
        </label>
      )}
      <Feedback save={save} />
      <div className="actions"><button className="btn" disabled={save.busy}>{save.busy ? 'Saving…' : 'Save'}</button></div>
    </form>
  )
}

function PasswordCard() {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const save = useSave()
  const submit = (e) => {
    if (newPassword !== confirm) {
      e.preventDefault()
      save.fail('The new passwords do not match.')
      return
    }
    save.run(e, async () => {
      await api.put('/account/password', { currentPassword, newPassword })
      setCurrentPassword('')
      setNewPassword('')
      setConfirm('')
    }, 'Password changed. Use the new one next time you log in.')
  }

  return (
    <form className="card" onSubmit={submit}>
      <h2>Password</h2>
      <label>Current password
        <input required type="password" autoComplete="current-password" value={currentPassword}
               onChange={(e) => setCurrentPassword(e.target.value)} />
      </label>
      <label>New password
        <input required type="password" autoComplete="new-password" minLength={8} maxLength={72} value={newPassword}
               onChange={(e) => setNewPassword(e.target.value)} />
        <span className="hint">At least 8 characters.</span>
      </label>
      <label>Confirm new password
        <input required type="password" autoComplete="new-password" minLength={8} maxLength={72} value={confirm}
               onChange={(e) => setConfirm(e.target.value)} />
      </label>
      <Feedback save={save} />
      <div className="actions"><button className="btn" disabled={save.busy}>{save.busy ? 'Saving…' : 'Change password'}</button></div>
    </form>
  )
}

export default function Settings() {
  const { user, setSession } = useAuth()
  return (
    <section>
      <h1>Settings</h1>
      <div className="settings">
        {user.role === 'OWNER' && <BusinessCard user={user} setSession={setSession} />}
        <ProfileCard user={user} setSession={setSession} />
        <PasswordCard />
      </div>
    </section>
  )
}
