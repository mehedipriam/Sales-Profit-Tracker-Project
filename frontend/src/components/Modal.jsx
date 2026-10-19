import { useEffect } from 'react'
import { createPortal } from 'react-dom'

export default function Modal({ title, onClose, children }) {
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  // Drawn straight into <body>, not where it is used. A modal opened from inside the top bar (the master eye) would
  // otherwise be positioned against the bar: in dark mode the bar's backdrop-filter makes it the containing block of
  // every fixed child, so the dialog sat centred on a 77px strip and was cut off above the window.
  return createPortal(
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
        <h2>{title}</h2>
        {children}
      </div>
    </div>,
    document.body,
  )
}
