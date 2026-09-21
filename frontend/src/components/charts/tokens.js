// Chart design tokens (light surface). Series colors come from the validated categorical palette:
// Revenue and Profit take slots 1 and 6; platforms take slots 2,3,4,5,7,8 in a fixed order, so the same
// color never means two different things on one page. Swap this file to re-brand every chart.

export const REVENUE = '#2a78d6' // blue, slot 1
export const PROFIT = '#008300' // green, slot 6
export const LOSS = '#e34948' // red, slot 8 - the negative pole of the margin bars

// Platforms: orange, aqua, yellow, magenta, violet, red. Beyond six, platforms share a neutral gray and are
// told apart by their labels (never a generated hue).
export const PLATFORM_COLORS = ['#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#4a3aa7', '#e34948']
export const OTHER = '#898781'

export const SURFACE = '#ffffff'
export const GRID = '#e1e0d9'
export const BASELINE = '#c3c2b7'
export const MUTED = '#898781'
export const TEXT_SECONDARY = '#52514e'
export const INK = '#0b0b0b'

/** Stable color per platform: by position among the business's platforms ordered by id (not by rank in a report). */
export function platformColorMap(platforms) {
  const map = new Map()
  ;[...platforms].sort((a, b) => a.id - b.id).forEach((p, i) => map.set(p.id, PLATFORM_COLORS[i] ?? OTHER))
  return map
}

const channel = (v) => {
  const c = v / 255
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
}

/** Text color that stays readable on top of a filled mark. */
export function textOn(hex) {
  const n = parseInt(hex.slice(1), 16)
  const lum = 0.2126 * channel(n >> 16) + 0.7152 * channel((n >> 8) & 255) + 0.0722 * channel(n & 255)
  return lum > 0.3 ? INK : '#ffffff'
}

export const compact = (n) => new Intl.NumberFormat('en', { notation: 'compact', maximumFractionDigits: 1 }).format(n)
