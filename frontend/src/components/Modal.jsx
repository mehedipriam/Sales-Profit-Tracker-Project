import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'

export default function Modal({ title, onClose, wide, children }) {
  const overlay = useRef(null)

  useEffect(() => {
    const onKey = (e) => {
      if (e.key !== 'Escape') return
      // With a dialog open on top of another (a password prompt over an order's details), Escape closes only the top one.
      const open = document.querySelectorAll('.overlay')
      if (open[open.length - 1] === overlay.current) onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  // Drawn straight into <body>, not where it is used. A modal opened from inside the top bar (the master eye) would
  // otherwise be positioned against the bar: in dark mode the bar's backdrop-filter makes it the containing block of
  // every fixed child, so the dialog sat centred on a 77px strip and was cut off above the window.
  return createPortal(
    <div ref={overlay} className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`modal${wide ? ' wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <h2>{title}</h2>
        {children}
      </div>
    </div>,
    document.body,
  )
}
