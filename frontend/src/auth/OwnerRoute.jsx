import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from './AuthContext'

/** Wraps the financial/admin-only routes (Phase 7b): Dashboard, Expenses, Reports, Statement, Team. Staff
 * gets bounced to Orders - the api itself also refuses these (403), this just avoids showing a dead end. */
export default function OwnerRoute() {
  const { user } = useAuth()
  if (user.role !== 'OWNER') return <Navigate to="/orders" replace />
  return <Outlet />
}
