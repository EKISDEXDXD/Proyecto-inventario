package com.inventario.licoreria.modules.products.repository;

import com.inventario.licoreria.modules.products.model.ProductAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ProductAlertRepository extends JpaRepository<ProductAlert, Long> {
    
    Optional<ProductAlert> findByProductId(Long productId);
    
    @Query("SELECT pa FROM ProductAlert pa WHERE pa.product.id = :productId")
    Optional<ProductAlert> findAlertByProductId(@Param("productId") Long productId);
    
    boolean existsByProductId(Long productId);
}
