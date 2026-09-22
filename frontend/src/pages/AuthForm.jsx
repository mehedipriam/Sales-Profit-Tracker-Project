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
  const isLogin = mode === 'login'

  if (user) return <Navigate to="/" replace />

  const onSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      if (isLogin) await login(values.email, values.password)
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
        {FIELDS[mode].map((f) => (
          <label key={f.name}>
            {f.label}
            <input
              required
              type={f.type}
              minLength={f.minLength}
              autoComplete={f.name === 'password' ? (isLogin ? 'current-password' : 'new-password') : undefined}
              value={values[f.name] || ''}
              onChange={(e) => setValues({ ...values, [f.name]: e.target.value })}
            />
          </label>
        ))}
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
