-- Crear tabla independiente para comentarios de transacciones
CREATE TABLE transaction_comment (
    id SERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL UNIQUE,
    comment VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_transaction_comment_transaction FOREIGN KEY (transaction_id) REFERENCES "transaction" (id) ON DELETE CASCADE
);

CREATE INDEX idx_transaction_comment_transaction_id ON transaction_comment(transaction_id);
