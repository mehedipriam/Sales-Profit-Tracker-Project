-- Couriers: each business keeps its own list (any courier service, not a fixed set), like platforms.
-- tracking_url is an optional link template with {tracking} where the tracking number goes.
CREATE TABLE couriers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  tracking_url VARCHAR(300),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE KEY uk_courier_name (tenant_id, name),
  CONSTRAINT fk_couriers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB;

-- Existing workspaces get the common Bangladeshi couriers to start with; new workspaces are seeded at registration.
INSERT INTO couriers (tenant_id, name)
SELECT t.id, c.name
FROM tenants t
CROSS JOIN (SELECT 'Pathao' AS name UNION ALL SELECT 'Steadfast' UNION ALL SELECT 'RedX'
            UNION ALL SELECT 'Paperfly' UNION ALL SELECT 'Sundarban Courier') c;

-- Which courier carries the order, its tracking number, the cash the courier collects from the customer
-- (cash on delivery), and the day the courier paid that cash out to the business. All empty for existing orders
-- and for orders delivered without a courier.
ALTER TABLE orders
  ADD COLUMN courier_id BIGINT NULL AFTER delivery_charge,
  ADD COLUMN tracking_number VARCHAR(64) NULL AFTER courier_id,
  ADD COLUMN cod_amount DECIMAL(12,2) NULL AFTER tracking_number,
  ADD COLUMN courier_paid_on DATE NULL AFTER cod_amount,
  ADD KEY idx_orders_courier (tenant_id, courier_id, courier_paid_on),
  ADD CONSTRAINT fk_orders_courier FOREIGN KEY (courier_id) REFERENCES couriers(id);
