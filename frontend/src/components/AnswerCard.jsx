import { money } from '../utils/format'
import { MASK, useReveal } from './Reveal'

const toneClass = { GOOD: 'pos', BAD: 'neg' }

/** A figure as the card shows it: money behind the eye, counts and text always. */
function cell(value, kind, show) {
  if (kind === 'MONEY') return show ? money(value) : MASK
  if (kind === 'PERCENT') return value === '-' || value == null ? '–' : show ? `${Number(value)}%` : MASK
  return String(value)
}

/**
 * The answer to a question about the business: labelled numbers, a small table for lists, and one sentence. Money
 * figures (and the sentence, which quotes them) stay masked until the eye is opened, like on the Reports page.
 */
export default function AnswerCard({ answer }) {
  const { shown, eye, prompt } = useReveal()
  const show = !!shown.figures
  const columns = answer.table?.columns ?? []
  const hasMoney = answer.stats.some((s) => s.kind === 'MONEY' || s.kind === 'PERCENT')
    || columns.some((c) => c.kind === 'MONEY' || c.kind === 'PERCENT')

  return (
    <div className="agent-card agent-answer">
      <div className="agent-answer-head">
        <div>
          <b>{answer.title}</b>
          <span className="muted">{answer.period}</span>
        </div>
        {hasMoney && <span>{eye('figures', 'figures')}</span>}
      </div>

      <div className="agent-stats">
        {answer.stats.map((s) => (
          <div key={s.label} className="agent-stat">
            <span className="muted">{s.label}</span>
            <b className={show || s.kind === 'COUNT' || s.kind === 'TEXT' ? toneClass[s.tone] ?? '' : ''}>{cell(s.value, s.kind, show)}</b>
          </div>
        ))}
      </div>

      {answer.table && (
        <div className="agent-table-wrap">
          <table className="agent-table">
            <thead>
              <tr>{columns.map((c, i) => <th key={c.label} className={i === 0 ? '' : 'num'}>{c.label}</th>)}</tr>
            </thead>
            <tbody>
              {answer.table.rows.map((row, r) => (
                // The first column (a name) is unique enough to key on, with the position for repeats.
                <tr key={`${row[0]}-${r}`}>
                  {row.map((v, i) => (
                    <td key={columns[i].label} className={i === 0 ? '' : 'num'}>{cell(v, columns[i].kind, show)}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {hasMoney && !show
        ? <p className="agent-sentence muted">Figures are hidden. Use the eye above to show them.</p>
        : <p className="agent-sentence">{answer.sentence}</p>}
      {prompt}
    </div>
  )
}
