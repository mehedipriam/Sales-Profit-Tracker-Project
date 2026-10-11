import { createContext, useContext, useEffect, useState } from 'react'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Modal from './Modal'
import { EyeIcon, EyeOffIcon } from './icons'

export const MASK = '••••••'
// One password or PIN unlocks every eye in the app for this long; then everything shown hides itself again, in case
// the screen is left unattended.
const UNLOCK_MS = 5 * 60 * 1000

const RevealContext = createContext(null)

/** Remembers, for the signed-in user only, until when cost and profit figures may be shown without asking again. */
export function RevealProvider({ children }) {
  const { user } = useAuth()
  const [unlock, setUnlock] = useState({ userId: null, until: 0 })
  const until = unlock.userId === user?.id ? unlock.until : 0
  const value = { until, unlocked: () => Date.now() < until, grant: () => setUnlock({ userId: user?.id, until: Date.now() + UNLOCK_MS }) }
  return <RevealContext.Provider value={value}>{children}</RevealContext.Provider>
}

/**
 * Per-page show/hide state for masked figures. Each eye (cost, profit, ...) shows or hides only its own figures; the
 * first one opened asks for the password or PIN, and the rest open straight away until the unlock runs out.
 * Returns { shown, eye(key, label), prompt }: render `prompt` once somewhere on the page.
 */
export function useReveal() {
  const reveal = useContext(RevealContext)
  const [shown, setShown] = useState({})
  const [asking, setAsking] = useState(null) // { key, label } while the prompt is open

  useEffect(() => {
    const left = reveal.until - Date.now()
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (left <= 0) { setShown({}); return undefined }
    const timer = setTimeout(() => setShown({}), left)
    return () => clearTimeout(timer)
  }, [reveal.until])

  const eye = (key, label) => {
    const on = !!shown[key]
    const text = `${on ? 'Hide' : 'Show'} ${label}`
    const click = () => {
      if (on) setShown((s) => ({ ...s, [key]: false }))
      else if (reveal.unlocked()) setShown((s) => ({ ...s, [key]: true }))
      else setAsking({ key, label })
    }
    return (
      <button type="button" className="reveal" title={text} aria-label={text} onClick={click}>
        {on ? <EyeOffIcon /> : <EyeIcon />}
      </button>
    )
  }

  const prompt = asking && (
    <Modal title={`Show ${asking.label}`} onClose={() => setAsking(null)}>
      <RevealPrompt
        what={asking.label}
        onRevealed={() => { reveal.grant(); setShown((s) => ({ ...s, [asking.key]: true })); setAsking(null) }}
        onCancel={() => setAsking(null)}
      />
    </Modal>
  )

  return { shown, eye, prompt }
}

/** A heading cell's label with its eye beside it, the label centred over the masked figures below. */
export function EyeHead({ children, eye }) {
  return <span className="hide-head">{children}{eye}</span>
}

// Asks for the signed-in user's login password, or their PIN if they set one. Only one is sent: typing in either box
// disables the other.
function RevealPrompt({ what, onRevealed, onCancel }) {
  const [password, setPassword] = useState('')
  const [pin, setPin] = useState('')
  const [pinStatus, setPinStatus] = useState(null) // { set, locked } once loaded
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const loadPin = () => api.get('/account/pin').then((res) => setPinStatus(res.data)).catch(() => {})
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { loadPin() }, [])

  const pinUsable = pinStatus?.set && !pinStatus.locked
  const pinNote = !pinStatus ? '' : !pinStatus.set ? 'No PIN yet - set one in Settings'
    : pinStatus.locked ? 'Locked after wrong tries - use your password' : ''

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    if (!password && !pin) {
      setError('Enter your password or your PIN.')
      return
    }
    setBusy(true)
    try {
      await api.post('/account/verify-password', password ? { password } : { pin })
      onRevealed()
    } catch (err) {
      setError(errorMessage(err))
      setPassword('')
      setPin('')
      setBusy(false)
      if (pin) loadPin() // a wrong PIN may have just locked it
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <div className="unlock-row">
        <label>
          Your password
          <input autoFocus type="password" autoComplete="current-password" value={password} disabled={!!pin}
                 onChange={(e) => setPassword(e.target.value)} />
        </label>
        <span className="unlock-or">or</span>
        <label>
          PIN
          <input type="password" inputMode="numeric" autoComplete="off" maxLength={6} value={pin}
                 disabled={!pinUsable || !!password} placeholder={pinUsable ? '4-6 digits' : ''}
                 title={pinNote || undefined}
                 onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))} />
        </label>
      </div>
      <span className="hint">
        Cost and profit figures stay hidden until you enter the password you log in with or your PIN. Once you do,
        every eye in the app opens without asking for 5 minutes.{pinNote && <> {pinNote}.</>}
      </span>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button type="button" className="btn secondary" onClick={onCancel}>Cancel</button>
        <button className="btn" disabled={busy}>{busy ? 'Checking…' : `Show ${what}`}</button>
      </div>
    </form>
  )
}
