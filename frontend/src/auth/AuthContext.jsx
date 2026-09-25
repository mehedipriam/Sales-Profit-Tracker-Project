import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import api, { tokenStore } from '../api/client'
import { applyLogo } from '../components/logo'
import { currencySymbol, setCurrency } from '../utils/format'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUserState] = useState(null)
  // Amounts everywhere are formatted in the store's currency, so it is applied the moment the user is known.
  const setUser = useCallback((next) => {
    setCurrency(next?.currency)
    applyLogo(next ? currencySymbol() : null)
    setUserState(next)
  }, [])
  const [loading, setLoading] = useState(() => Boolean(tokenStore.get()))

  // Restore the session from a stored token. Only a 401 means the token is bad (the client interceptor then
  // clears it); any other failure - e.g. a 502 while the backend is still starting - is retried, so a
  // remembered login survives reopening the app right after a restart.
  useEffect(() => {
    if (!tokenStore.get()) return
    let cancelled = false
    let timer
    const restore = (attempt) =>
      api
        .get('/auth/me')
        .then((res) => {
          if (cancelled) return
          setUser(res.data)
          setLoading(false)
        })
        .catch((err) => {
          if (cancelled) return
          if (err.response?.status === 401 || attempt >= 30) {
            if (err.response?.status === 401) tokenStore.clear()
            setLoading(false)
          } else {
            timer = setTimeout(() => restore(attempt + 1), 2000)
          }
        })
    restore(1)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [setUser])

  useEffect(() => {
    const onLogout = () => setUser(null)
    window.addEventListener('auth:logout', onLogout)
    return () => window.removeEventListener('auth:logout', onLogout)
  }, [])

  const authenticate = useCallback(async (path, body) => {
    const res = await api.post(path, body)
    tokenStore.set(res.data.token, Boolean(body.rememberMe))
    setUser(res.data.user)
  }, [])

  const login = useCallback(
    (email, password, rememberMe) => authenticate('/auth/login', { email, password, rememberMe }),
    [authenticate],
  )
  const register = useCallback((data) => authenticate('/auth/register', data), [authenticate])
  // After a settings change: a new token when the server issued one (e.g. the email changed), and the fresh user.
  const setSession = useCallback(({ token, user: next }) => {
    if (token) tokenStore.set(token)
    setUser(next)
  }, [])
  const logout = useCallback(() => {
    tokenStore.clear()
    setUser(null)
  }, [])

  const value = useMemo(
    () => ({ user, loading, login, register, logout, setSession }),
    [user, loading, login, register, logout, setSession],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = () => useContext(AuthContext)
