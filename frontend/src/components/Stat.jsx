/** A figure card. `icon` (an svg) and `accent` (blue, green, amber, red, teal, slate, violet) are optional extras. */
export default function Stat({ label, value, tone, hint, icon, accent }) {
  return (
    <div className={`stat${accent ? ` accent-${accent}` : ''}`}>
      <div className="stat-head">
        <span className="stat-label">{label}</span>
        {icon && <span className="stat-icon" aria-hidden="true">{icon}</span>}
      </div>
      <span className={`stat-value ${tone ?? ''}`}>{value}</span>
      {hint && <span className="stat-hint">{hint}</span>}
    </div>
  )
}
