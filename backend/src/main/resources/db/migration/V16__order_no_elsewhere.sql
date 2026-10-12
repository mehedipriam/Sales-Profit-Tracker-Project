-- The Activity log keeps the order number as it was when the entry was written, so entries for an order that has since
-- been deleted still name it. Entries older than this change are filled in from the orders that still exist.
ALTER TABLE order_activity ADD COLUMN order_no VARCHAR(20) NULL AFTER order_id;

UPDATE order_activity a
JOIN orders o ON o.id = a.order_id
SET a.order_no = o.order_no;

-- Stock log lines about an order name its number instead of its database ID ("Sold on order ORD-2610-0001").
UPDATE stock_adjustments s
JOIN orders o ON o.id = s.order_id
SET s.note = REPLACE(s.note, CONCAT('order #', o.id), CONCAT('order ', o.order_no))
WHERE s.note IS NOT NULL;
