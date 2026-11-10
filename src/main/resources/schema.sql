CREATE TABLE IF NOT EXISTS products (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku        VARCHAR(40)  NOT NULL UNIQUE,
    name       VARCHAR(120) NOT NULL,
    category   VARCHAR(60),
    price      DECIMAL(10, 2) NOT NULL CHECK (price >= 0),
    stock      INT NOT NULL DEFAULT 0 CHECK (stock >= 0),
    min_stock  INT NOT NULL DEFAULT 0 CHECK (min_stock >= 0),
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS stock_movements (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT NOT NULL REFERENCES products (id),
    type        VARCHAR(12) NOT NULL CHECK (type IN ('ENTRY', 'EXIT', 'ADJUSTMENT')),
    quantity    INT NOT NULL CHECK (quantity >= 0),
    stock_after INT NOT NULL CHECK (stock_after >= 0),
    reason      VARCHAR(200),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_movements_product ON stock_movements (product_id, id);
CREATE INDEX IF NOT EXISTS idx_products_category ON products (category);
