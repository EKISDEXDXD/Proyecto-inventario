package com.inventario.licoreria.modules.products.repository;

import com.inventario.licoreria.modules.products.model.Tag;
import com.inventario.licoreria.modules.store.model.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {
    
    List<Tag> findByStore(Store store);
    
    List<Tag> findByStoreAndNameContainingIgnoreCase(Store store, String name);
    
    Optional<Tag> findByNameAndStore(String name, Store store);
}
