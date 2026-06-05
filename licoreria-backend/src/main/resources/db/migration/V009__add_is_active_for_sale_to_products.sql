-- Agregar columna is_active_for_sale para distinguir entre eliminado y activo para venta
ALTER TABLE products ADD COLUMN is_active_for_sale BOOLEAN DEFAULT FALSE NOT NULL;

-- Por defecto, los productos existentes (sin parent_id) están activos para venta
UPDATE products SET is_active_for_sale = TRUE WHERE parent_id IS NULL;

-- Comentario de explicación:
-- is_active = false: Producto está eliminado (soft delete) - no debe verse en ningún lado
-- is_active = true: Producto existe
-- is_active_for_sale = true: Producto está listo para la venta (aparece en movimientos)
-- is_active_for_sale = false: Producto existe pero no está disponible para venta (no aparece en movimientos)
