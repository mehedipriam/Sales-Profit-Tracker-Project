-- What the owner typed as the amount earned from the courier charge on an order (the part of the customer's delivery
-- charge the courier does not keep). When set it replaces the automatic fee and COD calculation for that order.
ALTER TABLE orders
  ADD COLUMN courier_earning DECIMAL(10,2) NULL AFTER courier_cod_fee;
