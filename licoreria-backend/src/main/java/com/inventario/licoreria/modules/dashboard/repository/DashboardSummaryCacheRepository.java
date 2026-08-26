package com.inventario.licoreria.modules.dashboard.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.inventario.licoreria.modules.dashboard.model.DashboardSummaryCache;

public interface DashboardSummaryCacheRepository extends JpaRepository<DashboardSummaryCache, Long> {
    Optional<DashboardSummaryCache> findByStoreId(Long storeId);
}
