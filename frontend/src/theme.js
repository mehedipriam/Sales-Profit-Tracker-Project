// Light / dark theme. The choice is remembered on this browser; until someone picks one, the app follows the
// system setting. The theme is set as <html data-theme="..."> so index.css can swap its colour tokens.
import { useSyncExternalStore } from 'react'

const KEY = 'spt_theme'
const systemDark = window.matchMedia('(prefers-color-scheme: dark)')
const listeners = new Set()

function stored() {
  try {
    const t = localStorage.getItem(KEY)
    return t === 'light' || t === 'dark' ? t : null
  } catch {
    return null
  }
}

let theme = stored() ?? (systemDark.matches ? 'dark' : 'light')

function apply(next) {
  theme = next
  document.documentElement.dataset.theme = next
  listeners.forEach((l) => l())
}

apply(theme)
systemDark.addEventListener('change', (e) => {
  if (!stored()) apply(e.matches ? 'dark' : 'light')
})

export function toggleTheme() {
  const next = theme === 'dark' ? 'light' : 'dark'
  try {
    localStorage.setItem(KEY, next)
  } catch { /* storage unavailable: the choice lasts until the page reloads */ }
  apply(next)
}

const subscribe = (l) => {
  listeners.add(l)
  return () => listeners.delete(l)
}

/** The current theme, 'light' or 'dark'; re-renders the caller when it changes. */
export function useTheme() {
  return useSyncExternalStore(subscribe, () => theme)
}
