import { PRESETS } from '../utils/dateRange'

/** Preset dropdown plus, for "Custom range", two date inputs. value = { preset, from, to }. */
export default function RangePicker({ value, onChange }) {
  return (
    <>
      <select aria-label="Date range" value={value.preset}
              onChange={(e) => onChange({ ...value, preset: e.target.value })}>
        {PRESETS.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
      </select>
      {value.preset === 'custom' && (
        <>
          <label className="inline-label">From
            <input type="date" value={value.from} max={value.to || undefined}
                   onChange={(e) => onChange({ ...value, from: e.target.value })} />
          </label>
          <label className="inline-label">To
            <input type="date" value={value.to} min={value.from || undefined}
                   onChange={(e) => onChange({ ...value, to: e.target.value })} />
          </label>
        </>
      )}
    </>
  )
}
