package com.inventario.licoreria.modules.products.repository;

import com.inventario.licoreria.modules.products.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    
    @Query("SELECT p FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY p.name ASC")
    List<Product> searchByName(@Param("query") String query);
    
    @Query("SELECT p FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT(:query, '%')) ORDER BY p.name ASC LIMIT 10")
    List<Product> searchSuggestions(@Param("query") String query);

    @Query("SELECT p FROM Product p WHERE p.store.id = :storeId ORDER BY p.name ASC")
    List<Product> findByStoreId(@Param("storeId") Long storeId);

    @Query("SELECT DISTINCT p FROM Product p " +
           "WHERE p.store.id = :storeId " +
           "AND LOWER(p.name) LIKE LOWER(CONCAT('%', :searchQuery, '%')) " +
           "ORDER BY p.name ASC")
    Page<Product> searchByNameAndStore(@Param("searchQuery") String searchQuery, 
                                       @Param("storeId") Long storeId, 
                                       Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN p.tags pt " +
           "WHERE p.store.id = :storeId " +
           "AND LOWER(p.name) LIKE LOWER(CONCAT('%', :searchQuery, '%')) " +
           "AND (pt.tag.id IN (:tagIds)) " +
           "ORDER BY p.name ASC")
    Page<Product> searchByNameAndTags(@Param("searchQuery") String searchQuery,
                                      @Param("storeId") Long storeId,
                                      @Param("tagIds") List<Long> tagIds,
                                      Pageable pageable);
}
