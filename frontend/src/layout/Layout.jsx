import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ROLE_LABELS, seesFinancials } from '../auth/roles'
import ErrorBoundary from '../components/ErrorBoundary'
import { logoUrl } from '../components/logo'
import { currencySymbol } from '../utils/format'

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
          {/* Dashboard, Expenses, Reports, Statement and Team are Owner/Admin only - never Staff. */}
          {showMoney && <NavLink to="/" end>Dashboard</NavLink>}
          <NavLink to="/orders">Orders</NavLink>
          {showMoney && <NavLink to="/expenses">Expenses</NavLink>}
          {showMoney && <NavLink to="/reports">Reports</NavLink>}
          {showMoney && <NavLink to="/statement">Statement</NavLink>}
          <NavLink to="/products">Products</NavLink>
          <NavLink to="/stock">Stock</NavLink>
          <NavLink to="/customers">Customers</NavLink>
          <NavLink to="/platforms">Platforms</NavLink>
          {showMoney && <NavLink to="/team">Team</NavLink>}
        </nav>
        <div className="account">
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
