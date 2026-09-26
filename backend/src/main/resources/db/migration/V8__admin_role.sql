-- Admin: a trusted manager with the Owner's access to the money and to Staff accounts, but not to the business
-- settings or to Owner/Admin accounts. Adding a value to the ENUM leaves every existing row as it is.
ALTER TABLE users
  MODIFY COLUMN role ENUM('OWNER','ADMIN','STAFF') NOT NULL DEFAULT 'OWNER';
