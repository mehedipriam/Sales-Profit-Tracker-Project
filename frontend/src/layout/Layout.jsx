import { useEffect, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ROLE_LABELS, seesFinancials } from '../auth/roles'
import ErrorBoundary from '../components/ErrorBoundary'
import ThemeToggle from '../components/ThemeToggle'
import { logoUrl } from '../components/logo'
import { currencySymbol } from '../utils/format'

/**
 * The set-up and team pages, used now and then rather than every day, tucked behind one "More" button so the
 * page links stay on a single line. Opens on hover with a mouse, or on click / tap / Enter; closes on picking a
 * page, moving the mouse away, clicking elsewhere or Escape.
 */
function MoreMenu({ links }) {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)
  const hovering = useRef(false) // the mouse is over the menu, so a click there keeps it open rather than toggling
  const closeTimer = useRef(null)
  const { pathname } = useLocation()
  const current = links.some((l) => pathname.startsWith(l.to))

  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { setOpen(false) }, [pathname])
  useEffect(() => {
    if (!open) return undefined
    const outside = (e) => { if (!ref.current?.contains(e.target)) setOpen(false) }
    const escape = (e) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', outside)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('mousedown', outside)
      document.removeEventListener('keydown', escape)
    }
  }, [open])

  useEffect(() => () => clearTimeout(closeTimer.current), [])

  // Touch screens fire pointer events too; only a real mouse hovering opens it, taps go through onClick.
  const enter = (e) => {
    if (e.pointerType !== 'mouse') return
    hovering.current = true
    clearTimeout(closeTimer.current)
    setOpen(true)
  }
  // A short grace period, so crossing the gap between the button and the menu doesn't close it.
  const leave = (e) => {
    if (e.pointerType !== 'mouse') return
    hovering.current = false
    closeTimer.current = setTimeout(() => setOpen(false), 200)
  }

  return (
    <div className="more" ref={ref} onPointerEnter={enter} onPointerLeave={leave}>
      <button type="button" className={`more-btn${current ? ' active' : ''}`} aria-expanded={open} aria-haspopup="true"
              onClick={() => setOpen(hovering.current || !open)}>
        More
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2.5"
             strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M6 9l6 6 6-6" /></svg>
      </button>
      {open && (
        <div className="more-menu" role="menu">
          {links.map((l) => <NavLink key={l.to} to={l.to} role="menuitem">{l.label}</NavLink>)}
        </div>
      )}
    </div>
  )
}

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const showMoney = seesFinancials(user)

  const onLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <div className="shell">
      <header className="topbar">
        <strong className="brand"><img src={logoUrl(currencySymbol())} alt="" />Sales &amp; Profit Tracker</strong>
        <nav>
          {/* Dashboard, Expenses, Reports, Statement, Team and Activity are Owner/Admin only - never Staff. */}
          {showMoney && <NavLink to="/" end>Dashboard</NavLink>}
          <NavLink to="/orders">Orders</NavLink>
          {showMoney && <NavLink to="/expenses">Expenses</NavLink>}
          {showMoney && <NavLink to="/reports">Reports</NavLink>}
          {showMoney && <NavLink to="/statement">Statement</NavLink>}
          <NavLink to="/products">Products</NavLink>
          <NavLink to="/stock">Stock</NavLink>
          <NavLink to="/customers">Customers</NavLink>
          <MoreMenu links={[
            { to: '/couriers', label: 'Couriers' },
            { to: '/platforms', label: 'Platforms' },
            ...(showMoney ? [{ to: '/team', label: 'Team' }, { to: '/activity', label: 'Activity' }] : []),
          ]} />
        </nav>
        <div className="account">
          <ThemeToggle />
          <NavLink className="who" to="/settings" title="Store name, profile and password">
            <span className="who-store">{user.businessName}</span>
            <span className="who-user">{user.fullName} · {ROLE_LABELS[user.role]}</span>
          </NavLink>
          <NavLink className="btn secondary small" to="/settings">Settings</NavLink>
          <button className="btn secondary small" onClick={onLogout}>Log out</button>
        </div>
      </header>
      <main className="content">
        <ErrorBoundary key={pathname}>
          <Outlet />
        </ErrorBoundary>
      </main>
    </div>
  )
}
