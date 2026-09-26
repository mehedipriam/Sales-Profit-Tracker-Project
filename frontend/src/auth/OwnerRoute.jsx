import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { seesFinancials } from './roles'

/** Wraps the Owner/Admin-only routes: Dashboard, Expenses, Reports, Statement, Team. Staff
 * gets bounced to Orders - the api itself also refuses these (403), this just avoids showing a dead end. */
export default function OwnerRoute() {
  const { user } = useAuth()
  if (!seesFinancials(user)) return <Navigate to="/orders" replace />
  return <Outlet />
}
