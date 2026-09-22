import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import api, { tokenStore } from '../api/client'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(() => Boolean(tokenStore.get()))

  // Restore the session from a stored token.
  useEffect(() => {
    if (!tokenStore.get()) return
    api
      .get('/auth/me')
      .then((res) => setUser(res.data))
      .catch(() => tokenStore.clear())
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    const onLogout = () => setUser(null)
    window.addEventListener('auth:logout', onLogout)
    return () => window.removeEventListener('auth:logout', onLogout)
  }, [])

  const authenticate = useCallback(async (path, body) => {
    const res = await api.post(path, body)
    tokenStore.set(res.data.token)
    setUser(res.data.user)
  }, [])

  const login = useCallback((email, password) => authenticate('/auth/login', { email, password }), [authenticate])
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
