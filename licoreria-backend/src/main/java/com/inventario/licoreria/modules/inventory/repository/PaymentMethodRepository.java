package com.inventario.licoreria.modules.inventory.repository;

import com.inventario.licoreria.modules.inventory.model.PaymentMethod;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    
    @Query("SELECT pm FROM PaymentMethod pm WHERE pm.transaction.id = :transactionId")
    Optional<PaymentMethod> findByTransactionId(@Param("transactionId") Long transactionId);
}
