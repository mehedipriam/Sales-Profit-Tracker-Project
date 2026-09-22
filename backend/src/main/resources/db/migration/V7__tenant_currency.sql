-- The currency a business trades in (ISO 4217 code), used to display every amount. Money is stored as plain numbers,
-- so this only changes how amounts are shown. Existing businesses keep the taka they were built around.
ALTER TABLE tenants
  ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'BDT' AFTER name;
