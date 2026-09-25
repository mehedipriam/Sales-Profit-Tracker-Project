import axios from 'axios'

const TOKEN_KEY = 'spt_token'

// "Remember me" keeps the token in localStorage (survives closing the browser); otherwise it lives in
// sessionStorage and is gone with the tab. Without `remember`, set() keeps the token where it already is.
export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY),
  set: (t, remember = Boolean(localStorage.getItem(TOKEN_KEY))) => {
    tokenStore.clear()
    ;(remember ? localStorage : sessionStorage).setItem(TOKEN_KEY, t)
  },
  clear: () => {
    localStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(TOKEN_KEY)
  },
}

const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use((config) => {
  const token = tokenStore.get()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// An expired/invalid token on a protected call sends the user back to login.
api.interceptors.response.use(
  (res) => res,
  (err) => {
    const isAuthCall = err.config?.url?.startsWith('/auth/login') || err.config?.url?.startsWith('/auth/register')
    if (err.response?.status === 401 && !isAuthCall) {
      tokenStore.clear()
      window.dispatchEvent(new Event('auth:logout'))
    }
    return Promise.reject(err)
  },
)

export const errorMessage = (err) => err.response?.data?.message || 'Something went wrong. Please try again.'

export default api
