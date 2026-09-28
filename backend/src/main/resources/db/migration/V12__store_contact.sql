-- Contact details printed on invoices and packing slips, plus an optional footer note for invoices.
ALTER TABLE tenants
  ADD COLUMN phone VARCHAR(32) NULL AFTER currency,
  ADD COLUMN address VARCHAR(500) NULL AFTER phone,
  ADD COLUMN invoice_note VARCHAR(500) NULL AFTER address;
