import { useState } from 'react'
import { csvFileName, downloadCsv, printAs } from '../utils/download'

/** CSV download and print/save-as-PDF for whatever slice the page currently shows. `range` = { from, to } strings. */
export default function ExportButtons({ range, platformId, platformName, printTitle }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const exportCsv = async () => {
    setBusy(true)
    setError('')
    try {
      await downloadCsv({ from: range.from, to: range.to, platformId: platformId || undefined }, csvFileName(range, platformName))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="export-buttons no-print">
      <button type="button" className="btn secondary" onClick={exportCsv} disabled={busy}>
        {busy ? 'Preparing…' : 'Export CSV'}
      </button>
      <button type="button" className="btn secondary" onClick={() => printAs(printTitle)}>
        Print / Save as PDF
      </button>
      {error && <span className="error" role="alert">{error}</span>}
    </div>
  )
}
