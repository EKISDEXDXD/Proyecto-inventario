package com.inventario.licoreria.modules.agenda.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.inventario.licoreria.modules.agenda.model.AgendaBoard;

public interface AgendaBoardRepository extends JpaRepository<AgendaBoard, Long> {
    List<AgendaBoard> findByStoreIdOrderByIdAsc(Long storeId);
    boolean existsByStoreIdAndPillarAndDefaultBoardTrue(Long storeId, String pillar);
}