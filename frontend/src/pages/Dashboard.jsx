import { useAuth } from '../auth/AuthContext'

export default function Dashboard() {
  const { user } = useAuth()
  return (
    <section>
      <h1>Welcome, {user.fullName}</h1>
      <p>You are signed in as <b>{user.role}</b> (workspace #{user.tenantId}, {user.email}).</p>
      <p className="muted">Products, customers, sales and profit reports arrive in the next phases.</p>
    </section>
  )
}
