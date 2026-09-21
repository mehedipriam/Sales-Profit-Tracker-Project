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
        </nav>
        <div className="spacer" />
        <span className="who">{user.fullName} · {user.role}</span>
        <button className="btn secondary" onClick={onLogout}>Log out</button>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
