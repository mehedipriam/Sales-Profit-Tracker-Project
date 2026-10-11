-- An optional 4-6 digit PIN (stored hashed, like the password) that unlocks cost prices and margins on screen instead
-- of typing the login password. pin_failures counts wrong PINs in a row; at 5 the PIN stops working until the login
-- password is entered, so a short PIN can't be guessed by trying them all.
ALTER TABLE users
  ADD COLUMN pin_hash VARCHAR(255) NULL AFTER password_hash,
  ADD COLUMN pin_failures INT NOT NULL DEFAULT 0 AFTER pin_hash;
