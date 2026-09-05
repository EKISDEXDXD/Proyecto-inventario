package com.inventario.licoreria.modules.cash_control.repository;

import com.inventario.licoreria.modules.cash_control.model.CashControlEntry;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashControlEntryRepository extends JpaRepository<CashControlEntry, Long> {

    @Query("SELECT e FROM CashControlEntry e WHERE e.store.id = :storeId AND e.deleted = false ORDER BY e.entryDate DESC, e.createdAt DESC")
    List<CashControlEntry> findByStoreId(@Param("storeId") Long storeId);

    @Query("SELECT e FROM CashControlEntry e WHERE e.store.id = :storeId AND e.entryDate = :date AND e.deleted = false ORDER BY e.createdAt DESC")
    List<CashControlEntry> findByStoreIdAndDate(@Param("storeId") Long storeId, @Param("date") LocalDate date);
}
