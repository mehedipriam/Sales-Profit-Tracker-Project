-- The tracking field also takes the courier's full tracking link (pasted from its SMS or app), not just a code.
ALTER TABLE orders
  MODIFY COLUMN tracking_number VARCHAR(300) NULL;
