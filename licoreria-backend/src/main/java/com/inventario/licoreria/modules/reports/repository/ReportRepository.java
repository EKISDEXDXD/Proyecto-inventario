package com.inventario.licoreria.modules.reports.repository;

import com.inventario.licoreria.modules.reports.model.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    Page<Report> findByStoreIdAndActiveOrderByReportDateDesc(Long storeId, Boolean active, Pageable pageable);
    List<Report> findByStoreIdAndReportDateBetweenAndActive(Long storeId, LocalDate startDate, LocalDate endDate, Boolean active);
    List<Report> findByStoreIdAndActive(Long storeId, Boolean active);
}
