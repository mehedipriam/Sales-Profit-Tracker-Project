-- Phase 7b: an Owner can deactivate a Staff account (revoke access) rather than only ever adding them.
-- Existing users all default to active so nobody currently logged in gets locked out by this migration.
ALTER TABLE users
  ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE AFTER role;
