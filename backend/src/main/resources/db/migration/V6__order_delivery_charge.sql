-- What the customer paid for delivery on top of the products. It is income that offsets the delivery expense, so
-- a sale whose delivery the customer covers keeps its full profit. Existing orders get 0: nothing changes for them.
ALTER TABLE orders
  ADD COLUMN delivery_charge DECIMAL(12,2) NOT NULL DEFAULT 0 AFTER commission_pct;
