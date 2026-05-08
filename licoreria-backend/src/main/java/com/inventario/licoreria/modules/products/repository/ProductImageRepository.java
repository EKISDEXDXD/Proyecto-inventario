package com.inventario.licoreria.modules.products.repository;

import com.inventario.licoreria.modules.products.model.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    Optional<ProductImage> findByProductId(Long productId);
    void deleteByProductId(Long productId);
}
