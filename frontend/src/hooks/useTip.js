import { useCallback, useState } from 'react'

/**
 * Tooltip state for bar-style charts, where the mark itself is the hit target. `handlers(content)` spreads onto a
 * mark and works from pointer and keyboard focus, so hover and focus reveal the same details.
 */
export default function useTip() {
  const [tip, setTip] = useState(null)

  const show = useCallback((event, content) => {
    if (event.type === 'focus') {
      const r = event.currentTarget.getBoundingClientRect()
      setTip({ x: r.left + r.width / 2, y: r.top, content })
    } else {
      setTip({ x: event.clientX, y: event.clientY, content })
    }
  }, [])
  const hide = useCallback(() => setTip(null), [])

  const handlers = (content) => ({
    onPointerMove: (e) => show(e, content),
    onPointerLeave: hide,
    onFocus: (e) => show(e, content),
    onBlur: hide,
  })

  return { tip, handlers }
}
