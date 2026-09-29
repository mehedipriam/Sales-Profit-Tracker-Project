-- Who did what to each order: created it, edited it (and which parts), changed its status, deleted it, recorded or
-- undid a courier payout. order_id has no foreign key so a deleted order keeps its history; the customer name and
-- amounts are copies taken at the time for the same reason.
CREATE TABLE order_activity (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  action VARCHAR(20) NOT NULL,
  customer_name VARCHAR(150) NOT NULL,
  amount_before DECIMAL(12,2) NULL,
  amount DECIMAL(12,2) NOT NULL,
  from_status VARCHAR(16) NULL,
  to_status VARCHAR(16) NULL,
  changes VARCHAR(200) NULL,
  created_at DATETIME NOT NULL,
  KEY idx_activity_tenant (tenant_id, created_at),
  KEY idx_activity_order (tenant_id, order_id),
  KEY idx_activity_user (tenant_id, user_id),
  CONSTRAINT fk_activity_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_activity_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;
