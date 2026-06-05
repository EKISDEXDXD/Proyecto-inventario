-- Agregar campos para el sistema de lotes
ALTER TABLE products ADD COLUMN parent_id BIGINT;
ALTER TABLE products ADD COLUMN is_active BOOLEAN DEFAULT true;
ALTER TABLE products ADD COLUMN order_index INTEGER DEFAULT 999;

-- Agregar restricción de clave foránea
ALTER TABLE products ADD CONSTRAINT fk_parent_product FOREIGN KEY (parent_id) REFERENCES products(id) ON DELETE CASCADE;
