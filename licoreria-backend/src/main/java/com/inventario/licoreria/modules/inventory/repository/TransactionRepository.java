package com.inventario.licoreria.modules.inventory.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.inventario.licoreria.modules.inventory.model.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    //Buscar todas las transacciones de un producto (historial de movimientos)
    @Query("SELECT t FROM Transaction t WHERE t.product.id = :productId ORDER BY t.dateTime DESC")
    List<Transaction> findByProductIdOrderByDateTimeDesc(@Param("productId") Long productId);

    //Buscar transacciones por rango de fechas (para reportes diarios, mensuales, etc.)
    List<Transaction> findByDateTimeBetweenOrderByDateTimeDesc(LocalDateTime start, LocalDateTime end);

    // Buscar por tipo (ENTRADA/SALIDA)
    List<Transaction> findByTypeOrderByDateTimeDesc(String type);

    // Buscar por ID de usuario (quién realizó la venta/compra)
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId ORDER BY t.dateTime DESC")
    List<Transaction> findByUserIdOrderByDateTimeDesc(@Param("userId") Long userId);

    // Buscar transacciones por tienda
    @Query("SELECT t FROM Transaction t JOIN t.product p WHERE p.store.id = :storeId ORDER BY t.dateTime DESC")
    List<Transaction> findByStoreIdOrderByDateTimeDesc(@Param("storeId") Long storeId);

    @Query("SELECT t FROM Transaction t JOIN t.product p WHERE p.store.id = :storeId ORDER BY t.dateTime DESC")
    Page<Transaction> findPageByStoreId(@Param("storeId") Long storeId, Pageable pageable);

    // Contar transacciones de un producto (para validar edición)
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.product.id = :productId")
    Long countByProductId(@Param("productId") Long productId);
}
