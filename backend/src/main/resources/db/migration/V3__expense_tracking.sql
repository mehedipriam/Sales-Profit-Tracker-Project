-- The platform's commission rate at the time of sale, snapshotted on the order (like the cost snapshot on items),
-- so changing a platform's rate later never rewrites history. Existing orders get 0: no retroactive commissions.
ALTER TABLE orders
  ADD COLUMN commission_pct DECIMAL(5,2) NOT NULL DEFAULT 0 AFTER status;

-- Automatic commission expenses are maintained by the app; users can't edit or delete them directly.
ALTER TABLE expenses
  ADD COLUMN auto_generated BOOLEAN NOT NULL DEFAULT FALSE AFTER description;
