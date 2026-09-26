export const ROLE_LABELS = { OWNER: 'Owner', ADMIN: 'Admin', STAFF: 'Staff' }

/** Dashboard, reports, statement, expenses, cost prices, profit figures and commission rates: Owner and Admin. */
export const seesFinancials = (user) => user?.role === 'OWNER' || user?.role === 'ADMIN'
