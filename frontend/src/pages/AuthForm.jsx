import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { lastSymbol, logoUrl } from '../components/logo'
import { useAuth } from '../auth/AuthContext'

const FIELDS = {
  login: [
    { name: 'email', label: 'Email', type: 'email' },
    { name: 'password', label: 'Password', type: 'password' },
  ],
  register: [
    { name: 'businessName', label: 'Business name', type: 'text' },
    { name: 'fullName', label: 'Your name', type: 'text' },
    { name: 'email', label: 'Email', type: 'email' },
    { name: 'password', label: 'Password (min 8 characters)', type: 'password', minLength: 8 },
  ],
}

export default function AuthForm({ mode }) {
  const { user, login, register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [values, setValues] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const [remember, setRemember] = useState(false)
  const isLogin = mode === 'login'

  if (user) return <Navigate to="/" replace />

  const onSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      if (isLogin) await login(values.email, values.password, remember)
      else await register(values)
      navigate(location.state?.from?.pathname || '/', { replace: true })
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-page">
      <form className="card" onSubmit={onSubmit}>
        <div className="auth-brand"><img src={logoUrl(lastSymbol())} alt="" />Sales &amp; Profit Tracker</div>
        <h1>{isLogin ? 'Log in' : 'Create your workspace'}</h1>
        {FIELDS[mode].map((f) => {
          const isPassword = f.type === 'password'
          const input = (
            <input
              required
              type={isPassword && showPassword ? 'text' : f.type}
              minLength={f.minLength}
              autoComplete={isPassword ? (isLogin ? 'current-password' : 'new-password') : undefined}
              value={values[f.name] || ''}
              onChange={(e) => setValues({ ...values, [f.name]: e.target.value })}
            />
          )
          return (
            <label key={f.name}>
              {f.label}
              {isPassword ? (
                <span className="password-field">
                  {input}
                  <button
                    type="button"
                    className="password-toggle"
                    onClick={() => setShowPassword((s) => !s)}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    aria-pressed={showPassword}
                    title={showPassword ? 'Hide password' : 'Show password'}
                  >
                    <EyeIcon off={showPassword} />
                  </button>
                </span>
              ) : (
                input
              )}
            </label>
          )
        })}
        {isLogin && (
          <label className="remember">
            <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
            Remember me for 30 days
          </label>
        )}
        {error && <p className="error" role="alert">{error}</p>}
        <button className="btn" disabled={busy}>{busy ? 'Please wait…' : isLogin ? 'Log in' : 'Sign up'}</button>
        <p className="muted">
          {isLogin ? (
            <>No account? <Link to="/register">Register</Link></>
          ) : (
            <>Already registered? <Link to="/login">Log in</Link></>
          )}
        </p>
      </form>
    </div>
  )
}

// Open eye = "show password"; struck through once the password is visible.
function EyeIcon({ off }) {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z" />
      <circle cx="12" cy="12" r="3" />
      {off && <path d="M3 3l18 18" />}
    </svg>
  )
}
