package com.inventario.licoreria.modules.agenda.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.inventario.licoreria.modules.agenda.model.AgendaEntry;

public interface AgendaEntryRepository extends JpaRepository<AgendaEntry, Long> {
    List<AgendaEntry> findByBoardId(Long boardId);
    List<AgendaEntry> findByBoardIdAndActiveTrueOrderByStartDateTimeAsc(Long boardId);

        @Query("SELECT entry FROM AgendaEntry entry WHERE entry.board.id = :boardId AND entry.active = true "
            + "AND (entry.startDateTime BETWEEN :start AND :end OR entry.weeklyRecurring = true) "
            + "ORDER BY entry.startDateTime ASC")
        List<AgendaEntry> findVisibleEntries(@Param("boardId") Long boardId,
                         @Param("start") LocalDateTime start,
                         @Param("end") LocalDateTime end);
}