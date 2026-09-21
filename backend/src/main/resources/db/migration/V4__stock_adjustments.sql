-- Append-only log of every stock movement. quantity_change is signed; stock_after is the product's stock right after it.
-- order_id is set on the automatic rows written when an order takes stock or gives it back; deleting the order keeps
-- the history (the row just loses its link).
CREATE TABLE stock_adjustments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  order_id BIGINT,
  reason ENUM('INITIAL','RESTOCK','DAMAGE','CORRECTION','ORDER') NOT NULL,
  quantity_change INT NOT NULL,
  stock_after INT NOT NULL,
  note VARCHAR(255),
  created_at DATETIME NOT NULL,
  KEY idx_stock_tenant_created (tenant_id, created_at),
  KEY idx_stock_product (tenant_id, product_id, created_at),
  KEY idx_stock_order (order_id),
  CONSTRAINT fk_stock_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT fk_stock_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL
) ENGINE=InnoDB;
