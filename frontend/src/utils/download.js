import api, { errorMessage } from '../api/client'

/** The API answers a failed download with JSON, but axios hands it over as a Blob; read the message out of it. */
async function downloadErrorMessage(err) {
  const data = err.response?.data
  if (data instanceof Blob) {
    try {
      return JSON.parse(await data.text()).message ?? errorMessage(err)
    } catch {
      /* not JSON - fall through */
    }
  }
  return errorMessage(err)
}

/** Fetches the CSV with the auth header (a plain link can't send it) and hands it to the browser as a file. */
export async function downloadCsv(params, filename) {
  try {
    const res = await api.get('/reports/export.csv', { params, responseType: 'blob' })
    const url = URL.createObjectURL(res.data)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch (err) {
    throw new Error(await downloadErrorMessage(err))
  }
}

/** e.g. sales-report_2026-09-01_to_2026-09-30_daraz.csv - the platform is part of the name so exports don't collide. */
export function csvFileName({ from, to }, platformName) {
  const slug = platformName ? `_${platformName.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'platform'}` : ''
  return `sales-report_${from ?? 'start'}_to_${to ?? 'latest'}${slug}.csv`
}

/**
 * Opens the browser's print dialog ("Save as PDF" is one of its destinations). The browser lays the page out with its
 * own text engine, so every script - including Bangla product and customer names - renders correctly.
 * The document title becomes the suggested file name.
 */
export function printAs(title) {
  const previous = document.title
  const restore = () => {
    document.title = previous
    window.removeEventListener('afterprint', restore)
  }
  document.title = title
  window.addEventListener('afterprint', restore)
  window.print()
}
