package com.inventario.licoreria.modules.cash_control.controller;

import com.inventario.licoreria.modules.cash_control.dto.CashControlEntryDTO;
import com.inventario.licoreria.modules.cash_control.dto.CashControlSummaryUpdateDTO;
import com.inventario.licoreria.modules.cash_control.model.CashControlEntry;
import com.inventario.licoreria.modules.cash_control.model.CashControlSummary;
import com.inventario.licoreria.modules.cash_control.service.CashControlService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cash-control")
public class CashControlController {

    private final CashControlService cashControlService;

    public CashControlController(CashControlService cashControlService) {
        this.cashControlService = cashControlService;
    }

    @GetMapping("/store/{storeId}")
    public List<CashControlEntry> getEntries(@PathVariable Long storeId, Authentication authentication) {
        return cashControlService.findByStoreId(storeId, authentication.getName());
    }

    @GetMapping("/summary/{storeId}")
    public CashControlSummary getSummary(@PathVariable Long storeId, Authentication authentication) {
        return cashControlService.getSummary(storeId, authentication.getName());
    }

    @PostMapping("/entries")
    public CashControlEntry createEntry(@Valid @RequestBody CashControlEntryDTO dto, Authentication authentication) {
        return cashControlService.createEntry(dto, authentication.getName());
    }

    @PostMapping("/summary")
    public CashControlSummary updateSummary(@RequestBody CashControlSummaryUpdateDTO dto, Authentication authentication) {
        return cashControlService.updateSummary(dto.getStoreId(), dto, authentication.getName());
    }

    @DeleteMapping("/entries/{entryId}")
    public ResponseEntity<Void> deleteEntry(@PathVariable Long entryId, Authentication authentication) {
        cashControlService.deleteEntry(entryId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
