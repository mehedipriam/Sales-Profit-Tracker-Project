import { money } from '../utils/format'

/**
 * How a figure moved against the period before: "▲ 12% vs last month". `better` is which way is good news ('up',
 * 'down', or nothing when neither is); `count` shows the difference in whole numbers instead of a percentage, since
 * one return becoming two is not usefully "+100%".
 */
export default function Change({ now, before, compare, better, count = false }) {
  const a = Number(now)
  const b = Number(before)
  const diff = a - b
  const direction = diff > 0 ? 'up' : diff < 0 ? 'down' : 'flat'
  const judged = direction === 'flat' || !better ? '' : direction === better ? ' good' : ' bad'

  let text
  if (diff === 0) text = 'no change'
  else if (count) text = `${diff > 0 ? '+' : '−'}${Math.abs(diff)}`
  else if (b === 0) text = `was ${money(0)}`
  else {
    const pct = (Math.abs(diff) / Math.abs(b)) * 100
    text = `${diff > 0 ? '▲' : '▼'} ${pct < 10 ? pct.toFixed(1) : Math.round(pct)}%`
  }

  return (
    <span className={`change${judged}`} title={`${compare.period}: ${count ? b : money(b)}`}>
      {text} <span className="vs">{compare.label}</span>
    </span>
  )
}
