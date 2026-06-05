-- Permitir que product_id sea NULL en la tabla transaction
-- Esto permite que los movimientos se mantengan incluso si se elimina el producto asociado

ALTER TABLE "transaction" 
  ALTER COLUMN product_id DROP NOT NULL;

-- Crear índice para mantener performance en búsquedas
CREATE INDEX IF NOT EXISTS idx_transaction_product_id ON "transaction" (product_id);
