package com.inventario.licoreria.modules.agenda.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.inventario.licoreria.modules.agenda.dto.AgendaBoardDTO;
import com.inventario.licoreria.modules.agenda.dto.AgendaEntryDTO;
import com.inventario.licoreria.modules.agenda.service.AgendaService;

@RestController
@RequestMapping("/api/agenda")
public class AgendaController {

    private final AgendaService agendaService;

    public AgendaController(AgendaService agendaService) {
        this.agendaService = agendaService;
    }

    @GetMapping("/store/{storeId}")
    public ResponseEntity<List<AgendaBoardDTO>> getBoards(
            @PathVariable Long storeId,
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end) {
        LocalDate rangeStart = start != null ? start : LocalDate.now().withDayOfYear(1);
        LocalDate rangeEnd = end != null ? end : rangeStart.withDayOfYear(rangeStart.lengthOfYear());
        if (rangeEnd.isBefore(rangeStart)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(agendaService.getBoards(storeId, rangeStart, rangeEnd));
    }

    @PostMapping("/store/{storeId}/boards")
    public ResponseEntity<AgendaBoardDTO> createBoard(
            @PathVariable Long storeId, @RequestBody AgendaBoardDTO dto, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(agendaService.createBoard(storeId, dto, authentication.getName()));
    }

    @PutMapping("/boards/{boardId}")
    public ResponseEntity<AgendaBoardDTO> updateBoard(
            @PathVariable Long boardId, @RequestBody AgendaBoardDTO dto, Authentication authentication) {
        return ResponseEntity.ok(agendaService.updateBoard(boardId, dto, authentication.getName()));
    }

    @DeleteMapping("/boards/{boardId}")
    public ResponseEntity<Void> deleteBoard(@PathVariable Long boardId, Authentication authentication) {
        agendaService.deleteBoard(boardId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/boards/{boardId}/entries")
    public ResponseEntity<AgendaEntryDTO> createEntry(
            @PathVariable Long boardId, @RequestBody AgendaEntryDTO dto, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(agendaService.createEntry(boardId, dto, authentication.getName()));
    }

    @PutMapping("/entries/{entryId}")
    public ResponseEntity<AgendaEntryDTO> updateEntry(
            @PathVariable Long entryId, @RequestBody AgendaEntryDTO dto, Authentication authentication) {
        return ResponseEntity.ok(agendaService.updateEntry(entryId, dto, authentication.getName()));
    }

    @DeleteMapping("/entries/{entryId}")
    public ResponseEntity<Void> deleteEntry(@PathVariable Long entryId, Authentication authentication) {
        agendaService.deleteEntry(entryId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}