import { toggleTheme, useTheme } from '../theme'
import { MoonIcon, SunIcon } from './icons'

/** Round button that flips between the light and dark theme; shows the theme it will switch to. */
export default function ThemeToggle({ className = '' }) {
  const dark = useTheme() === 'dark'
  const label = dark ? 'Switch to light theme' : 'Switch to dark theme'
  return (
    <button type="button" className={`theme-toggle ${className}`} onClick={toggleTheme} title={label} aria-label={label}>
      {dark ? <SunIcon /> : <MoonIcon />}
    </button>
  )
}
