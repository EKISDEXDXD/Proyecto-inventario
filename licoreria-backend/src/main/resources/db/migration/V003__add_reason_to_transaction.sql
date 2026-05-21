-- Agregar columna reason a la tabla transaction
-- Esta migración es segura: no elimina ni modifica datos existentes
ALTER TABLE transaction ADD COLUMN reason VARCHAR(50) NULL;

-- Agregar índice para mejorar búsquedas por reason
CREATE INDEX idx_transaction_reason ON transaction(reason);
