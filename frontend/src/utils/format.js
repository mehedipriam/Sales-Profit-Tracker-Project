/** Currencies offered in Settings; any ISO 4217 code the store is set to still formats correctly. */
export const CURRENCIES = [
  ['BDT', 'Bangladeshi taka'], ['USD', 'US dollar'], ['EUR', 'Euro'], ['GBP', 'British pound'],
  ['INR', 'Indian rupee'], ['PKR', 'Pakistani rupee'], ['NPR', 'Nepalese rupee'], ['LKR', 'Sri Lankan rupee'],
  ['AED', 'UAE dirham'], ['SAR', 'Saudi riyal'], ['QAR', 'Qatari riyal'], ['KWD', 'Kuwaiti dinar'],
  ['MYR', 'Malaysian ringgit'], ['SGD', 'Singapore dollar'], ['IDR', 'Indonesian rupiah'], ['THB', 'Thai baht'],
  ['PHP', 'Philippine peso'], ['CNY', 'Chinese yuan'], ['JPY', 'Japanese yen'], ['KRW', 'South Korean won'],
  ['CAD', 'Canadian dollar'], ['AUD', 'Australian dollar'], ['NZD', 'New Zealand dollar'], ['TRY', 'Turkish lira'],
  ['ZAR', 'South African rand'], ['NGN', 'Nigerian naira'], ['BRL', 'Brazilian real'], ['MXN', 'Mexican peso'],
]

// The store's currency, set from the signed-in user (AuthContext) so every money() call follows it.
let formatter = null
export const setCurrency = (code) => {
  try {
    formatter = new Intl.NumberFormat('en-US', { style: 'currency', currency: code || 'BDT', currencyDisplay: 'narrowSymbol' })
  } catch {
    formatter = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'BDT', currencyDisplay: 'narrowSymbol' })
  }
}
setCurrency('BDT')

/** Symbol and decimals come from the currency: ৳1,440.00, $1,440.00, ¥1,440. */
export const money = (n) => formatter.format(Number(n ?? 0))

/** The current currency's symbol, e.g. ৳, $, €; for the logo. */
export const currencySymbol = () => formatter.formatToParts(0).find((p) => p.type === 'currency')?.value ?? '৳'

export const dateTime = (iso) =>
  new Date(iso).toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' })

/** Value for <input type="datetime-local"> in the user's local time. */
export const toLocalInput = (d = new Date()) => {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}

export const STATUSES = ['PAID', 'PENDING', 'RETURNED', 'CANCELLED']
export const STATUS_LABEL = { PAID: 'Paid', PENDING: 'Pending', RETURNED: 'Returned / Refunded', CANCELLED: 'Cancelled' }

export const EXPENSE_TYPES = ['DELIVERY', 'PACKAGING', 'ADS', 'PLATFORM_COMMISSION', 'MISC']
export const EXPENSE_LABEL = {
  DELIVERY: 'Delivery',
  PACKAGING: 'Packaging',
  ADS: 'Ads / boost',
  PLATFORM_COMMISSION: 'Platform commission',
  MISC: 'Miscellaneous',
}

export const STOCK_REASONS = ['INITIAL', 'RESTOCK', 'DAMAGE', 'CORRECTION', 'ORDER']
export const STOCK_REASON_LABEL = {
  INITIAL: 'Opening stock',
  RESTOCK: 'Restock',
  DAMAGE: 'Damaged',
  CORRECTION: 'Correction',
  ORDER: 'Order',
}
