import { createContext, useContext, useEffect, useState } from 'react'
import api, { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Modal from './Modal'
import { EyeIcon, EyeOffIcon } from './icons'

export const MASK = '••••••'
// One password or PIN unlocks every eye in the app for this long; then everything shown hides itself again, in case
// the screen is left unattended.
const UNLOCK_MS = 5 * 60 * 1000
// The master eye's "show everything" lasts until the tab is closed: sessionStorage belongs to this tab alone and is
// cleared with it. It holds the id of the user who turned it on, so it never carries over to someone else.
const ALL_KEY = 'reveal-all'

const readAll = () => {
  try { return sessionStorage.getItem(ALL_KEY) } catch { return null }
}
const writeAll = (userId) => {
  try {
    if (userId == null) sessionStorage.removeItem(ALL_KEY)
    else sessionStorage.setItem(ALL_KEY, String(userId))
  } catch { /* storage blocked: it then lasts until the page is reloaded */ }
}

const RevealContext = createContext(null)

/**
 * For the signed-in user only: until when cost and profit figures may be shown without asking again, and whether the
 * master eye has shown everything on every page for the rest of this tab's life.
 */
export function RevealProvider({ children }) {
  const { user } = useAuth()
  const [unlock, setUnlock] = useState({ userId: null, until: 0 })
  const [allFor, setAllFor] = useState(readAll) // the user id the master eye was turned on for, or null

  useEffect(() => { if (!user) writeAll(null) }, [user]) // logging out ends it

  const until = unlock.userId === user?.id ? unlock.until : 0
  const all = user != null && allFor === String(user.id)
  const value = {
    until,
    all,
    unlocked: () => all || Date.now() < until,
    grant: () => setUnlock({ userId: user?.id, until: Date.now() + UNLOCK_MS }),
    setAll: (on) => {
      const id = on ? String(user.id) : null
      writeAll(id)
      setAllFor(id)
    },
  }
  return <RevealContext.Provider value={value}>{children}</RevealContext.Provider>
}

/**
 * The master eye in the top bar: one password or PIN shows every cost and profit figure on every page, and they stay
 * shown until this tab is closed, the user logs out, or it is clicked again.
 */
export function MasterEye() {
  const reveal = useContext(RevealContext)
  const [asking, setAsking] = useState(false)
  const text = reveal.all ? 'Hide all figures again' : 'Show all figures on every page until this tab is closed'
  const click = () => (reveal.all ? reveal.setAll(false) : setAsking(true))
  return <>
    <button type="button" className={`theme-toggle master-eye${reveal.all ? ' on' : ''}`} title={text} aria-label={text}
            aria-pressed={reveal.all} onClick={click}>
      {reveal.all ? <EyeOffIcon /> : <EyeIcon />}
    </button>
    {asking && (
      <Modal title="Show all figures" onClose={() => setAsking(false)}>
        <RevealPrompt
          what="all figures"
          note="Every cost and profit figure on every page stays shown until you close this tab, log out, or click the eye in the top bar again."
          onRevealed={() => { reveal.setAll(true); setAsking(false) }}
          onCancel={() => setAsking(false)}
        />
      </Modal>
    )}
  </>
}

/**
 * Per-page show/hide state for masked figures. Each eye (cost, profit, ...) shows or hides only its own figures; the
 * first one opened asks for the password or PIN, and the rest open straight away until the unlock runs out. While
 * the master eye is on, everything starts shown, and a page's own eye can still hide its figures there.
 * Returns { shown, eye(key, label), prompt }: render `prompt` once somewhere on the page.
 */
export function useReveal() {
  const reveal = useContext(RevealContext)
  // Eyes flipped away from the default, which is shown while the master eye is on and hidden otherwise.
  const [flipped, setFlipped] = useState({})
  const [asking, setAsking] = useState(null) // { key, label } while the prompt is open
  const isShown = (key) => reveal.all !== !!flipped[key]
  const shown = new Proxy({}, { get: (_, key) => isShown(key) })

  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { setFlipped({}) }, [reveal.all]) // the master eye turning on or off resets every page eye

  useEffect(() => {
    if (reveal.all) return undefined
    const left = reveal.until - Date.now()
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (left <= 0) { setFlipped({}); return undefined }
    const timer = setTimeout(() => setFlipped({}), left)
    return () => clearTimeout(timer)
  }, [reveal.until, reveal.all])

  const flip = (key) => setFlipped((f) => ({ ...f, [key]: !f[key] }))

  const eye = (key, label) => {
    const on = isShown(key)
    const text = `${on ? 'Hide' : 'Show'} ${label}`
    const click = () => {
      if (on || reveal.unlocked()) flip(key)
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
        onRevealed={() => { reveal.grant(); flip(asking.key); setAsking(null) }}
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
function RevealPrompt({ what, note, onRevealed, onCancel }) {
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
        {note ?? <>Cost and profit figures stay hidden until you enter the password you log in with or your PIN. Once
          you do, every eye in the app opens without asking for 5 minutes. To keep everything shown until you close
          this tab, use the eye in the top bar instead.</>}
        {pinNote && <> {pinNote}.</>}
      </span>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button type="button" className="btn secondary" onClick={onCancel}>Cancel</button>
        <button className="btn" disabled={busy}>{busy ? 'Checking…' : `Show ${what}`}</button>
      </div>
    </form>
  )
}
