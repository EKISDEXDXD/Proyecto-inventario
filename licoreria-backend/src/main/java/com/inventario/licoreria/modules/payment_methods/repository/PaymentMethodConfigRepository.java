package com.inventario.licoreria.modules.payment_methods.repository;

import com.inventario.licoreria.modules.payment_methods.model.PaymentMethodConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentMethodConfigRepository extends JpaRepository<PaymentMethodConfig, Long> {
    
    // Métodos globales (sin filtro de tienda)
    @Query("SELECT pmc FROM PaymentMethodConfig pmc WHERE pmc.isActive = true ORDER BY pmc.createdAt DESC")
    List<PaymentMethodConfig> findAllActive();

    @Query("SELECT pmc FROM PaymentMethodConfig pmc ORDER BY pmc.createdAt DESC")
    List<PaymentMethodConfig> findAllOrdered();
}
