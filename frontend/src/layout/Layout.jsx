import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import ErrorBoundary from '../components/ErrorBoundary'

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const isOwner = user.role === 'OWNER'

  const onLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <div className="shell">
      <header className="topbar">
        <strong className="brand">Sales &amp; Profit Tracker</strong>
        <nav>
          {/* Dashboard, Expenses, Reports and Statement are financial detail - Owner only (Phase 7b). */}
          {isOwner && <NavLink to="/" end>Dashboard</NavLink>}
          <NavLink to="/orders">Orders</NavLink>
          {isOwner && <NavLink to="/expenses">Expenses</NavLink>}
          {isOwner && <NavLink to="/reports">Reports</NavLink>}
          {isOwner && <NavLink to="/statement">Statement</NavLink>}
          <NavLink to="/products">Products</NavLink>
          <NavLink to="/stock">Stock</NavLink>
          <NavLink to="/customers">Customers</NavLink>
          <NavLink to="/platforms">Platforms</NavLink>
          {isOwner && <NavLink to="/team">Team</NavLink>}
        </nav>
        <div className="spacer" />
        <span className="who">{user.businessName} · {user.fullName} · {user.role}</span>
        <button className="btn secondary" onClick={onLogout}>Log out</button>
      </header>
      <main className="content">
        <ErrorBoundary key={pathname}>
          <Outlet />
        </ErrorBoundary>
      </main>
    </div>
  )
}
