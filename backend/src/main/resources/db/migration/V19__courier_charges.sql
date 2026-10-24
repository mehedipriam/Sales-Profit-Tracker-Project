-- What a courier takes out of the cash it collects: a delivery fee (different inside and outside Dhaka) and a
-- percentage COD charge on what is left after that fee. Zero means the courier charges nothing automatically.
ALTER TABLE couriers
  ADD COLUMN fee_inside DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER tracking_url,
  ADD COLUMN fee_outside DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER fee_inside,
  ADD COLUMN cod_pct DECIMAL(5,2) NOT NULL DEFAULT 0 AFTER fee_outside;

-- Steadfast takes 1% of the cash after its delivery fee; the owner only has to enter the delivery fees.
UPDATE couriers SET cod_pct = 1 WHERE LOWER(name) = 'steadfast';

-- The charges worked out for each order when it is saved (like the platform commission rate), so changing a
-- courier's rates later never rewrites old orders. Existing orders get 0: no retroactive charges.
ALTER TABLE orders
  ADD COLUMN delivery_zone VARCHAR(10) NULL AFTER cod_amount,
  ADD COLUMN courier_fee DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER delivery_zone,
  ADD COLUMN courier_cod_fee DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER courier_fee;
