// The app logo: a shopping bag carrying the store's currency symbol (৳, $, €...). Built as an SVG data URL so the
// same drawing serves the header, the login page and the browser tab icon. public/favicon.svg is the ৳ version,
// used before anyone has signed in.

const LAST_SYMBOL_KEY = 'spt_logo_symbol'

const escapeXml = (s) => s.replace(/[<>&'"]/g, (c) => `&#${c.charCodeAt(0)};`)

export function logoUrl(symbol = '৳') {
  // Longer symbols (Rs, AED, KWD) shrink to fit the bag.
  const size = symbol.length === 1 ? 27 : symbol.length === 2 ? 17 : 11
  const svg = `<svg viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
  <defs><linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#10b981"/><stop offset="1" stop-color="#047857"/></linearGradient></defs>
  <rect width="64" height="64" rx="14" fill="url(#bg)"/>
  <path d="M24 22 v-3 a8 8 0 0 1 16 0 v3" fill="none" stroke="#fff" stroke-width="3.5" stroke-linecap="round"/>
  <path d="M15 22 h34 l-3 28 a3 3 0 0 1 -3 2.7 h-22 a3 3 0 0 1 -3 -2.7 z" fill="#fff"/>
  <text x="31" y="${symbol.length === 1 ? 48 : 44}" text-anchor="middle" font-family="'Nirmala UI', 'Noto Sans Bengali', 'Segoe UI', Arial, sans-serif" font-size="${size}" font-weight="700" fill="#047857">${escapeXml(symbol)}</text>
  <circle cx="49" cy="15" r="9" fill="#fbbf24"/>
  <path d="M45 17 L49 12 L53 17" fill="none" stroke="#7c2d12" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"/>
</svg>`
  return `data:image/svg+xml,${encodeURIComponent(svg)}`
}

/** Points the browser tab icon at the logo for this symbol, and remembers it for the login page. */
export function applyLogo(symbol) {
  const link = document.querySelector('link[rel="icon"]')
  if (link) link.href = symbol ? logoUrl(symbol) : '/favicon.svg'
  try {
    if (symbol) localStorage.setItem(LAST_SYMBOL_KEY, symbol)
  } catch { /* storage unavailable: the login page just shows the default */ }
}

/** The symbol of the store last signed in on this browser, for the login page; ৳ by default. */
export function lastSymbol() {
  try {
    return localStorage.getItem(LAST_SYMBOL_KEY) || '৳'
  } catch {
    return '৳'
  }
}
