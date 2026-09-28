-- The courier's own parcel / consignment ID for the order, kept apart from the tracking code or link.
ALTER TABLE orders
  ADD COLUMN consignment_id VARCHAR(64) NULL AFTER courier_id;
