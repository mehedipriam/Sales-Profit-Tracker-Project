import { lazy, Suspense } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import OwnerRoute from './auth/OwnerRoute'
import ProtectedRoute from './auth/ProtectedRoute'
import Layout from './layout/Layout'
import AuthForm from './pages/AuthForm'
import Customers from './pages/Customers'
import Dashboard from './pages/Dashboard'
import Expenses from './pages/Expenses'
import OrderForm from './pages/OrderForm'
import Orders from './pages/Orders'
import Platforms from './pages/Platforms'
import Products from './pages/Products'
import Statement from './pages/Statement'
import Stock from './pages/Stock'
import Team from './pages/Team'

// The charting library is the heaviest dependency; load it only when Reports is opened.
const Reports = lazy(() => import('./pages/Reports'))

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<AuthForm mode="login" />} />
          <Route path="/register" element={<AuthForm mode="register" />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<Layout />}>
              <Route path="/products" element={<Products />} />
              <Route path="/stock" element={<Stock />} />
              <Route path="/customers" element={<Customers />} />
              <Route path="/orders" element={<Orders />} />
              <Route path="/orders/new" element={<OrderForm />} />
              <Route path="/orders/:id" element={<OrderForm />} />
              <Route path="/platforms" element={<Platforms />} />
              {/* Financial/admin-only (Phase 7b): Staff never lands here, the api enforces it either way. */}
              <Route element={<OwnerRoute />}>
                <Route path="/" element={<Dashboard />} />
                <Route path="/expenses" element={<Expenses />} />
                <Route path="/statement" element={<Statement />} />
                <Route path="/team" element={<Team />} />
                <Route path="/reports" element={<Suspense fallback={<p className="center-note">Loading…</p>}><Reports /></Suspense>} />
              </Route>
            </Route>
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
