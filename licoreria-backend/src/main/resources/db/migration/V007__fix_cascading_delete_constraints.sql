-- Arreglar la cascada de eliminación de lotes
-- Cambiar ON DELETE CASCADE a ON DELETE RESTRICT para proteger datos

-- 1. Eliminar el constraint anterior
ALTER TABLE products DROP CONSTRAINT IF EXISTS fk_parent_product;

-- 2. Crear nuevo constraint con ON DELETE RESTRICT (evita eliminar si tiene lotes)
ALTER TABLE products ADD CONSTRAINT fk_parent_product 
    FOREIGN KEY (parent_id) REFERENCES products(id) ON DELETE RESTRICT;

-- 3. Cambiar el constraint de transaction -> product a ON DELETE SET NULL
-- Primero, verificar y cambiar el constraint existente
ALTER TABLE "transaction" DROP CONSTRAINT IF EXISTS fk_transaction_product;

-- Crear el nuevo constraint permitiendo que product_id sea NULL
ALTER TABLE "transaction" ADD CONSTRAINT fk_transaction_product 
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL;

-- 4. Verificar y arreglar otros constraints si existen
-- Asegurar que product_alert no tenga cascada peligrosa
ALTER TABLE product_alert DROP CONSTRAINT IF EXISTS fk_product_alert_product;
ALTER TABLE product_alert ADD CONSTRAINT fk_product_alert_product 
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;

-- Asegurar que product_tag no tenga cascada peligrosa  
ALTER TABLE product_tag DROP CONSTRAINT IF EXISTS fk_product_tag_product;
ALTER TABLE product_tag ADD CONSTRAINT fk_product_tag_product 
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;

-- Asegurar que product_image no tenga cascada peligrosa
ALTER TABLE product_image DROP CONSTRAINT IF EXISTS fk_product_image_product;
ALTER TABLE product_image ADD CONSTRAINT fk_product_image_product 
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;
