// Light / dark theme. Light by default; a switch to dark is remembered on this browser. The theme is set as
// <html data-theme="..."> so index.css can swap its colour tokens.
import { useSyncExternalStore } from 'react'

const KEY = 'spt_theme'
const listeners = new Set()

function stored() {
  try {
    const t = localStorage.getItem(KEY)
    return t === 'light' || t === 'dark' ? t : null
  } catch {
    return null
  }
}

let theme = stored() ?? 'light'

function apply(next) {
  theme = next
  document.documentElement.dataset.theme = next
  listeners.forEach((l) => l())
}

apply(theme)

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
