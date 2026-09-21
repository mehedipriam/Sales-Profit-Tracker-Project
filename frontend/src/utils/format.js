export const money = (n) => {
  const v = Number(n ?? 0)
  return (v < 0 ? '-' : '') + '৳' + Math.abs(v).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

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
