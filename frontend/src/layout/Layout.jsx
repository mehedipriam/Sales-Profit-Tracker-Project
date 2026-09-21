import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const onLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <div className="shell">
      <header className="topbar">
        <strong className="brand">Sales &amp; Profit Tracker</strong>
        <nav>
          <NavLink to="/" end>Dashboard</NavLink>
          <NavLink to="/orders">Orders</NavLink>
          <NavLink to="/reports">Reports</NavLink>
          <NavLink to="/statement">Statement</NavLink>
          <NavLink to="/products">Products</NavLink>
          <NavLink to="/customers">Customers</NavLink>
          <NavLink to="/platforms">Platforms</NavLink>
        </nav>
        <div className="spacer" />
        <span className="who">{user.businessName} · {user.fullName} · {user.role}</span>
        <button className="btn secondary" onClick={onLogout}>Log out</button>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
