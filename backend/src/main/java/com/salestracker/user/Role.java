package com.salestracker.user;

/**
 * OWNER has everything. ADMIN matches the Owner on day-to-day work and the money (dashboard, reports, expenses,
 * cost/profit, commission rates) and can manage Staff, but not the business settings or Owner/Admin accounts.
 * STAFF runs sales day to day with no financial visibility.
 */
public enum Role {
    OWNER, ADMIN, STAFF;

    /** Dashboard, reports, statement, expenses, cost prices, profit figures and commission rates. */
    public boolean seesFinancials() { return this != STAFF; }
}
