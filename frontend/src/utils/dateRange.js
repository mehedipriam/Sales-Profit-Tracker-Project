export const PRESETS = [
  { value: 'this_month', label: 'This month' },
  { value: 'last_month', label: 'Last month' },
  { value: 'this_year', label: 'This year' },
  { value: 'all', label: 'All time' },
  { value: 'custom', label: 'Custom range' },
]

const pad = (n) => String(n).padStart(2, '0')

/** yyyy-MM-dd in the user's local time (what the API expects). */
export const toDateStr = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`

/**
 * Turns a preset (or custom from/to strings) into inclusive { from, to } date strings.
 * Missing bounds mean open-ended. Returns { error } for an inverted custom range.
 */
export function resolveRange({ preset, from = '', to = '' }, now = new Date()) {
  const y = now.getFullYear()
  const m = now.getMonth()
  switch (preset) {
    case 'this_month':
      return { from: toDateStr(new Date(y, m, 1)), to: toDateStr(new Date(y, m + 1, 0)) }
    case 'last_month':
      return { from: toDateStr(new Date(y, m - 1, 1)), to: toDateStr(new Date(y, m, 0)) }
    case 'this_year':
      return { from: `${y}-01-01`, to: `${y}-12-31` }
    case 'custom':
      if (from && to && from > to) return { error: '"From" date must not be after "To" date.' }
      return { from: from || undefined, to: to || undefined }
    default:
      return {}
  }
}

const fmt = (s) => new Date(`${s}T00:00:00`).toLocaleDateString('en-GB', { dateStyle: 'medium' })

export function describeRange({ from, to }) {
  if (from && to) return `${fmt(from)} – ${fmt(to)}`
  if (from) return `From ${fmt(from)}`
  if (to) return `Up to ${fmt(to)}`
  return 'All time'
}
