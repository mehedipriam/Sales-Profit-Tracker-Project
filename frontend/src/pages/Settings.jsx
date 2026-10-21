import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { seesFinancials } from '../auth/roles'
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

const ICONS = {
  store: <><path d="M3 9l1.5-5h15L21 9" /><path d="M4 9v11h16V9" /><path d="M3 9a3 3 0 0 0 6 0 3 3 0 0 0 6 0 3 3 0 0 0 6 0" /><path d="M10 20v-5h4v5" /></>,
  user: <><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></>,
  lock: <><rect x="4" y="11" width="16" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></>,
  key: <><circle cx="8" cy="15" r="4" /><path d="M11 12l9-9" /><path d="M16 7l3 3" /></>,
}

/** The card title with an icon tile and a one-line description of what the card is for. */
function CardHead({ icon, title, text }) {
  return (
    <div className="settings-head">
      <span className="settings-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8"
             strokeLinecap="round" strokeLinejoin="round">{ICONS[icon]}</svg>
      </span>
      <div>
        <h2>{title}</h2>
        <p className="muted">{text}</p>
      </div>
    </div>
  )
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
  const [phone, setPhone] = useState(user.businessPhone ?? '')
  const [address, setAddress] = useState(user.businessAddress ?? '')
  const [invoiceNote, setInvoiceNote] = useState(user.invoiceNote ?? '')
  const known = CURRENCIES.some(([code]) => code === currency)
  const save = useSave()
  const submit = (e) => save.run(e, async () => {
    const { data } = await api.put('/account/business', { name, currency, phone, address, invoiceNote })
    setSession({ user: data })
  }, 'Store settings saved.')

  return (
    <form className="card" onSubmit={submit}>
      <CardHead icon="store" title="Store" text="Your shop's name, currency and what is printed on invoices." />
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
      <fieldset>
        <legend>On invoices &amp; packing slips</legend>
        <label>Shop phone <input maxLength={32} value={phone} placeholder="e.g. 01711-000000"
                                 onChange={(e) => setPhone(e.target.value)} /></label>
        <label>Shop address
          <textarea rows={2} maxLength={500} value={address} onChange={(e) => setAddress(e.target.value)} />
        </label>
        <label>Invoice note
          <textarea rows={2} maxLength={500} value={invoiceNote} placeholder="e.g. Thanks for shopping with us! Exchange within 7 days."
                    onChange={(e) => setInvoiceNote(e.target.value)} />
          <span className="hint">Printed at the bottom of every invoice. All three are optional.</span>
        </label>
      </fieldset>
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
      <CardHead icon="user" title="Your profile" text="The name and email you use to log in." />
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
      <CardHead icon="lock" title="Password" text="Change the password you log in with." />
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

// A short PIN that unlocks cost prices and margins instead of the login password. It is stored hashed, so it can
// only be set, changed or removed (each with the login password), never shown.
function PinCard() {
  const [status, setStatus] = useState(null) // { set, locked } once loaded
  const [currentPassword, setCurrentPassword] = useState('')
  const [pin, setPin] = useState('')
  const [confirm, setConfirm] = useState('')
  const save = useSave()

  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { api.get('/account/pin').then((res) => setStatus(res.data)).catch(() => {}) }, [])

  const send = (e, newPin, message) => save.run(e, async () => {
    const { data } = await api.put('/account/pin', { currentPassword, pin: newPin })
    setStatus(data)
    setCurrentPassword('')
    setPin('')
    setConfirm('')
  }, message)

  const submit = (e) => {
    if (pin !== confirm) {
      e.preventDefault()
      save.fail('The PINs do not match.')
      return
    }
    send(e, pin, status?.set ? 'PIN changed.' : 'PIN set. Use it to show costs and margins on the Products page.')
  }
  const remove = (e) => {
    if (!currentPassword) {
      save.fail('Enter your current password to remove the PIN.')
      return
    }
    send(e, null, 'PIN removed.')
  }
  const digits = (setter) => (e) => setter(e.target.value.replace(/\D/g, ''))

  return (
    <form className="card" onSubmit={submit}>
      <CardHead icon="key" title="PIN for costs" text="A quick code that unlocks cost prices and margins." />
      <p className="hint pin-status">
        {status?.set
          ? (status.locked ? 'Your PIN is locked after too many wrong tries - enter your password once on the Products page, or set a new PIN here.'
            : 'You have a PIN. It is stored securely and cannot be shown - you can change or remove it.')
          : 'A 4 to 6 digit PIN you can type instead of your password to show cost prices and margins.'}
      </p>
      <label>Current password
        <input required type="password" autoComplete="current-password" value={currentPassword}
               onChange={(e) => setCurrentPassword(e.target.value)} />
        <span className="hint">Needed to set, change or remove the PIN.</span>
      </label>
      <label>{status?.set ? 'New PIN' : 'PIN'}
        <input required type="password" inputMode="numeric" autoComplete="new-password" minLength={4} maxLength={6}
               pattern="\d{4,6}" title="4 to 6 digits" value={pin} onChange={digits(setPin)} />
        <span className="hint">4 to 6 digits.</span>
      </label>
      <label>Confirm PIN
        <input required type="password" inputMode="numeric" autoComplete="new-password" minLength={4} maxLength={6}
               pattern="\d{4,6}" title="4 to 6 digits" value={confirm} onChange={digits(setConfirm)} />
      </label>
      <Feedback save={save} />
      <div className="actions">
        {status?.set && (
          <button type="button" className="btn secondary" disabled={save.busy} onClick={remove}>Remove PIN</button>
        )}
        <button className="btn" disabled={save.busy}>{save.busy ? 'Saving…' : status?.set ? 'Change PIN' : 'Set PIN'}</button>
      </div>
    </form>
  )
}

export default function Settings() {
  const { user, setSession } = useAuth()
  const [params, setParams] = useSearchParams()
  const sections = [
    user.role === 'OWNER' && { id: 'store', label: 'Store', icon: 'store', render: () => <BusinessCard user={user} setSession={setSession} /> },
    { id: 'profile', label: 'Your profile', icon: 'user', render: () => <ProfileCard user={user} setSession={setSession} /> },
    { id: 'password', label: 'Password', icon: 'lock', render: () => <PasswordCard /> },
    seesFinancials(user) && { id: 'pin', label: 'PIN for costs', icon: 'key', render: () => <PinCard /> },
  ].filter(Boolean)
  const active = sections.find((x) => x.id === params.get('tab')) ?? sections[0]

  return (
    <section className="settings-page">
      <div className="settings-title">
        <h1>Settings</h1>
        <p className="muted">Manage your store, your account and your security.</p>
      </div>
      <div className="settings">
        <nav className="settings-nav" aria-label="Settings sections">
          {sections.map((x) => (
            <button key={x.id} type="button" className={x.id === active.id ? 'active' : ''}
                    aria-current={x.id === active.id ? 'page' : undefined}
                    onClick={() => setParams({ tab: x.id }, { replace: true })}>
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8"
                   strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{ICONS[x.icon]}</svg>
              {x.label}
            </button>
          ))}
        </nav>
        <div className="settings-panel" key={active.id}>{active.render()}</div>
      </div>
    </section>
  )
}
