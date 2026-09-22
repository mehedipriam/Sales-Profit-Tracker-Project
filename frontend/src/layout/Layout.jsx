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
        <div className="account">
          <NavLink className="who" to="/settings" title="Store name, profile and password">
            <span className="who-store">{user.businessName}</span>
            <span className="who-user">{user.fullName} · {isOwner ? 'Owner' : 'Staff'}</span>
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
