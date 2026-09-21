import { useEffect, useState } from 'react'
import useDebounce from '../hooks/useDebounce'

/**
 * Type-ahead picker. `search(q)` must return a promise of items and must be a stable reference
 * (module-level function or useCallback). Picking calls onPick(item) and clears the box.
 */
export default function AsyncPicker({ search, label, onPick, placeholder }) {
  const [text, setText] = useState('')
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState([])
  const dq = useDebounce(text, 250)

  useEffect(() => {
    if (!open) return undefined
    let live = true
    search(dq).then((r) => live && setItems(r)).catch(() => {})
    return () => { live = false }
  }, [dq, open, search])

  const pick = (item) => {
    onPick(item)
    setText('')
    setOpen(false)
  }

  return (
    <div className="picker">
      <input
        type="search"
        placeholder={placeholder}
        value={text}
        onChange={(e) => setText(e.target.value)}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
      />
      {open && (
        <ul className="picker-list">
          {items.map((it) => (
            <li key={it.id} onMouseDown={(e) => { e.preventDefault(); pick(it) }}>{label(it)}</li>
          ))}
          {items.length === 0 && <li className="muted">No matches</li>}
        </ul>
      )}
    </div>
  )
}
