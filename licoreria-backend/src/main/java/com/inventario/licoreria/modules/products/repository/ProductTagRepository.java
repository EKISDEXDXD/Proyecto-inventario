package com.inventario.licoreria.modules.products.repository;

import com.inventario.licoreria.modules.products.model.ProductTag;
import com.inventario.licoreria.modules.products.model.Product;
import com.inventario.licoreria.modules.products.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductTagRepository extends JpaRepository<ProductTag, Long> {
    
    List<ProductTag> findByProduct(Product product);
    
    List<ProductTag> findByTag(Tag tag);
    
    Optional<ProductTag> findByProductAndTag(Product product, Tag tag);
    
    void deleteByProductAndTag(Product product, Tag tag);
}
