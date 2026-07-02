package com.inventario.licoreria.modules.inventory.repository;

import com.inventario.licoreria.modules.inventory.model.TransactionComment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionCommentRepository extends JpaRepository<TransactionComment, Long> {
    Optional<TransactionComment> findByTransactionId(Long transactionId);
    void deleteByTransactionId(Long transactionId);
}
