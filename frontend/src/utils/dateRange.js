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

const parse = (s) => new Date(`${s}T00:00:00`)
const daysIn = (y, m) => new Date(y, m + 1, 0).getDate()

/**
 * The period a report is compared with: { from, to, label }, or null when there is nothing fair to compare
 * (all time, open-ended ranges). A month or year still in progress is compared with the same stretch of the one
 * before (1–16 Sep against 1–16 Aug), so a half-finished month doesn't look like a slump.
 */
export function previousRange({ preset, from = '', to = '' }, now = new Date()) {
  const y = now.getFullYear()
  const m = now.getMonth()
  const d = now.getDate()
  switch (preset) {
    case 'this_month':
      return {
        from: toDateStr(new Date(y, m - 1, 1)),
        // On the month's last day it is whole month against whole month (30 Sep takes in 31 Aug).
        to: toDateStr(new Date(y, m - 1, d === daysIn(y, m) ? daysIn(y, m - 1) : Math.min(d, daysIn(y, m - 1)))),
        label: 'vs last month',
      }
    case 'last_month':
      return { from: toDateStr(new Date(y, m - 2, 1)), to: toDateStr(new Date(y, m - 1, 0)), label: 'vs the month before' }
    case 'this_year':
      return {
        from: `${y - 1}-01-01`,
        to: toDateStr(new Date(y - 1, m, d === daysIn(y, m) ? daysIn(y - 1, m) : Math.min(d, daysIn(y - 1, m)))),
        label: 'vs last year',
      }
    case 'custom': {
      if (!from || !to || from > to) return null
      // The same number of days, ending the day before this range starts.
      const start = parse(from)
      const days = Math.round((parse(to) - start) / 86_400_000) + 1
      const prevTo = new Date(start.getFullYear(), start.getMonth(), start.getDate() - 1)
      const prevFrom = new Date(prevTo.getFullYear(), prevTo.getMonth(), prevTo.getDate() - (days - 1))
      return { from: toDateStr(prevFrom), to: toDateStr(prevTo), label: `vs previous ${days} day${days === 1 ? '' : 's'}` }
    }
    default:
      return null
  }
}
