CREATE TABLE tenants (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(150) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Every table below carries tenant_id (the tenants table's own id is its tenant identity).

CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  email VARCHAR(190) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  full_name VARCHAR(150) NOT NULL,
  role ENUM('OWNER','STAFF') NOT NULL DEFAULT 'OWNER',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_users_email (email),
  KEY idx_users_tenant (tenant_id),
  CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB;

CREATE TABLE platforms (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  commission_pct DECIMAL(5,2) NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE KEY uk_platform_name (tenant_id, name),
  CONSTRAINT fk_platforms_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB;

CREATE TABLE products (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  name VARCHAR(190) NOT NULL,
  sku VARCHAR(64),
  category VARCHAR(100),
  cost_price DECIMAL(12,2) NOT NULL,
  selling_price DECIMAL(12,2) NOT NULL,
  stock_qty INT,
  low_stock_threshold INT,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_product_sku (tenant_id, sku),
  KEY idx_products_tenant (tenant_id),
  CONSTRAINT fk_products_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB;

CREATE TABLE customers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  name VARCHAR(150) NOT NULL,
  phone VARCHAR(32),
  address VARCHAR(500),
  source_platform_id BIGINT,
  notes TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_customers_tenant (tenant_id, name),
  KEY idx_customers_phone (tenant_id, phone),
  CONSTRAINT fk_customers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_customers_platform FOREIGN KEY (source_platform_id) REFERENCES platforms(id)
) ENGINE=InnoDB;

CREATE TABLE orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  platform_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  status ENUM('PAID','PENDING','RETURNED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  ordered_at DATETIME NOT NULL,
  notes TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_orders_report (tenant_id, ordered_at, platform_id),
  CONSTRAINT fk_orders_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_orders_platform FOREIGN KEY (platform_id) REFERENCES platforms(id),
  CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB;

CREATE TABLE order_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  cost_price_snapshot DECIMAL(12,2) NOT NULL,  -- cost at time of sale
  sold_price DECIMAL(12,2) NOT NULL,           -- actual price charged per unit
  KEY idx_items_order (order_id),
  KEY idx_items_tenant (tenant_id),
  CONSTRAINT fk_items_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB;

CREATE TABLE expenses (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  order_id BIGINT,
  type ENUM('DELIVERY','PACKAGING','ADS','PLATFORM_COMMISSION','MISC') NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  expense_date DATE NOT NULL,
  description VARCHAR(255),
  KEY idx_expenses_tenant_date (tenant_id, expense_date),
  CONSTRAINT fk_expenses_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_expenses_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB;
