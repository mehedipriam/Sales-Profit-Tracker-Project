/** Shared tooltip body: title, then rows led by the value with the label secondary and a line key. */
export function TipBody({ title, rows }) {
  return (
    <>
      {title && <div className="tip-title">{title}</div>}
      {rows.map((r) => (
        <div className="tip-row" key={r.label}>
          <span className="tip-key" style={{ background: r.color }} />
          <b>{r.value}</b>
          <span className="tip-label">{r.label}</span>
        </div>
      ))}
    </>
  )
}

/** Floating tooltip for useTip(). Content is rendered as React children (textContent), never as HTML. */
export function TipLayer({ tip }) {
  if (!tip) return null
  return (
    <div className="chart-tip floating" style={{ left: tip.x + 14, top: tip.y + 14 }} role="tooltip">
      {tip.content}
    </div>
  )
}
