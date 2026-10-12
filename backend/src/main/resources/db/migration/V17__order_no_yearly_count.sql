-- The count in an order number (the NNNN of ORD-YYMM-NNNN) now runs through the whole year instead of restarting each
-- month, so order numbers keep rising from January to December. Orders are renumbered per business and year, in the
-- order they were placed.
-- Moved aside first so a renumbered order can never clash with another order's old number.
UPDATE orders SET order_no = CONCAT('TMP-', id);

UPDATE orders o
JOIN (
  SELECT id,
         CONCAT('ORD-', DATE_FORMAT(ordered_at, '%y%m'), '-',
                LPAD(ROW_NUMBER() OVER (PARTITION BY tenant_id, YEAR(ordered_at) ORDER BY ordered_at, id), 4, '0')) AS no
  FROM orders
) n ON n.id = o.id
SET o.order_no = n.no;

UPDATE order_activity a
JOIN orders o ON o.id = a.order_id
SET a.order_no = o.order_no;

UPDATE stock_adjustments s
JOIN orders o ON o.id = s.order_id
SET s.note = CONCAT(IF(s.quantity_change < 0, 'Sold on order ', 'Returned from order '), o.order_no)
WHERE s.note IS NOT NULL;
