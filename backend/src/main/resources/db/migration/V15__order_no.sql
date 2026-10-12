-- The number shown on invoices and in the Orders list: ORD-YYMM-NNNN, where NNNN counts that business's orders in the
-- month the order was placed. It is stored when the order is created so editing the order later never changes a
-- number a customer already holds. Existing orders are numbered by month in the order they were placed.
ALTER TABLE orders ADD COLUMN order_no VARCHAR(20) NULL AFTER id;

UPDATE orders o
JOIN (
  SELECT id,
         CONCAT('ORD-', DATE_FORMAT(ordered_at, '%y%m'), '-',
                LPAD(ROW_NUMBER() OVER (PARTITION BY tenant_id, DATE_FORMAT(ordered_at, '%y%m')
                                        ORDER BY ordered_at, id), 4, '0')) AS no
  FROM orders
) n ON n.id = o.id
SET o.order_no = n.no;

ALTER TABLE orders
  MODIFY order_no VARCHAR(20) NOT NULL,
  ADD UNIQUE KEY uq_orders_tenant_order_no (tenant_id, order_no);
