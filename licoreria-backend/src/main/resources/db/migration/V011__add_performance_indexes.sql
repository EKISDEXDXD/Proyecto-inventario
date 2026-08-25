-- Índices para acelerar la carga del dashboard analítico (evita full scans en tablas grandes)

CREATE INDEX IF NOT EXISTS idx_products_store_id ON products (store_id);
CREATE INDEX IF NOT EXISTS idx_transaction_date_time ON "transaction" (date_time);
CREATE INDEX IF NOT EXISTS idx_transaction_user_id ON "transaction" (user_id);
CREATE INDEX IF NOT EXISTS idx_product_tag_product_id ON product_tag (product_id);
CREATE INDEX IF NOT EXISTS idx_product_tag_tag_id ON product_tag (tag_id);
CREATE INDEX IF NOT EXISTS idx_payment_method_transaction_id ON payment_method (transaction_id);
CREATE INDEX IF NOT EXISTS idx_payment_method_config_id ON payment_method (payment_method_config_id);
CREATE INDEX IF NOT EXISTS idx_administrative_cost_movement_admin_cost_id ON administrative_cost_movement (administrative_cost_id);
CREATE INDEX IF NOT EXISTS idx_administrative_cost_store_id ON administrative_cost (store_id);
