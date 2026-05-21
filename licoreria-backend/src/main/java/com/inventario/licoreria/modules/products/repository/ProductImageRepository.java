package com.inventario.licoreria.modules.products.repository;

import com.inventario.licoreria.modules.products.model.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    Optional<ProductImage> findByProductId(Long productId);
    
    /**
     * Elimina todos los registros de ProductImage para un producto
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ProductImage p WHERE p.product.id = :productId")
    void deleteByProductId(Long productId);
    
    /**
     * Encuentra todos los productos con múltiples imágenes
     * Retorna lista de product_ids que tienen más de una imagen
     */
    @Query(value = "SELECT product_id FROM product_image GROUP BY product_id HAVING COUNT(*) > 1", nativeQuery = true)
    List<Long> findProductsWithDuplicateImages();
    
    /**
     * Obtiene todas las imágenes de un producto
     */
    List<ProductImage> findAllByProductId(Long productId);
    
    /**
     * Limpia duplicados: mantiene solo la imagen más reciente por producto
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM product_image WHERE id NOT IN (SELECT MAX(id) FROM product_image GROUP BY product_id)", nativeQuery = true)
    int cleanupDuplicateImages();
}
