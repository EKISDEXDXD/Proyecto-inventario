package com.inventario.licoreria.modules.cash_control.repository;

import com.inventario.licoreria.modules.cash_control.model.CashControlSummary;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashControlSummaryRepository extends JpaRepository<CashControlSummary, Long> {

    @Query("SELECT s FROM CashControlSummary s WHERE s.storeId = :storeId AND s.summaryDate = :date")
    Optional<CashControlSummary> findByStoreIdAndDate(@Param("storeId") Long storeId, @Param("date") LocalDate date);
}
