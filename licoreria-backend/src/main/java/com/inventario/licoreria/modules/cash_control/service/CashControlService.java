package com.inventario.licoreria.modules.cash_control.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.inventario.licoreria.modules.cash_control.dto.CashControlEntryDTO;
import com.inventario.licoreria.modules.cash_control.dto.CashControlSummaryUpdateDTO;
import com.inventario.licoreria.modules.cash_control.model.CashControlEntry;
import com.inventario.licoreria.modules.cash_control.model.CashControlSummary;
import com.inventario.licoreria.modules.cash_control.repository.CashControlEntryRepository;
import com.inventario.licoreria.modules.cash_control.repository.CashControlSummaryRepository;
import com.inventario.licoreria.modules.store.model.Store;
import com.inventario.licoreria.modules.store.service.StoreService;
import com.inventario.licoreria.modules.users.model.User;
import com.inventario.licoreria.modules.users.service.UserService;

@Service
public class CashControlService {

    private final CashControlEntryRepository cashControlEntryRepository;
    private final CashControlSummaryRepository cashControlSummaryRepository;
    private final StoreService storeService;
    private final UserService userService;

    public CashControlService(
            CashControlEntryRepository cashControlEntryRepository,
            CashControlSummaryRepository cashControlSummaryRepository,
            StoreService storeService,
            UserService userService) {
        this.cashControlEntryRepository = cashControlEntryRepository;
        this.cashControlSummaryRepository = cashControlSummaryRepository;
        this.storeService = storeService;
        this.userService = userService;
    }

    public List<CashControlEntry> findByStoreId(Long storeId, String username) {
        Store store = storeService.findStoreEntity(storeId);
        if (!store.getManager().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para acceder al control de caja de esta tienda");
        }
        return cashControlEntryRepository.findByStoreId(storeId);
    }

    @Transactional
    public CashControlEntry createEntry(CashControlEntryDTO dto, String username) {
        Store store = storeService.findStoreEntity(dto.getStoreId());
        if (!store.getManager().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para agregar gastos a esta tienda");
        }

        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto no puede ser negativo");
        }

        boolean isDailyFlowClosure = "CERRADO".equalsIgnoreCase(dto.getStatus())
                || "Cierre de flujo diario".equalsIgnoreCase(dto.getConcept());
        if (isDailyFlowClosure) {
            if (dto.getVerificationPassword() == null || dto.getVerificationPassword().isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere la contraseña para cerrar el flujo diario");
            }
            storeService.validateAccessPassword(store.getId(), dto.getVerificationPassword());
        }

        if (dto.getAmount().compareTo(BigDecimal.ZERO) == 0 && !isDailyFlowClosure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto debe ser mayor que cero para un gasto manual");
        }

        User createdBy = userService.findByUsername(username);
        CashControlEntry entry = new CashControlEntry();
        entry.setStore(store);
        entry.setCreatedBy(createdBy);
        entry.setEntryDate(dto.getEntryDate() == null ? LocalDate.now() : dto.getEntryDate());
        entry.setConcept(dto.getConcept());
        entry.setAmount(dto.getAmount());
        entry.setExpenseType(normalizeExpenseType(dto.getExpenseType()));
        entry.setMoneyOrigin(normalizeMoneyOrigin(dto.getMoneyOrigin()));
        entry.setDetail(dto.getDetail());
        entry.setResponsibleName(dto.getResponsibleName() == null || dto.getResponsibleName().isBlank()
                ? createdBy.getUsername() : dto.getResponsibleName());
        entry.setStatus(dto.getStatus() == null || dto.getStatus().isBlank() ? "PAGADO" : dto.getStatus());
        return cashControlEntryRepository.save(entry);
    }

    public CashControlSummary getSummary(Long storeId, String username) {
        Store store = storeService.findStoreEntity(storeId);
        if (!store.getManager().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para consultar el control de caja");
        }

        List<CashControlEntry> entries = cashControlEntryRepository.findByStoreId(storeId);
        BigDecimal totalExpenses = totalExpenses(entries);
        BigDecimal grossProfitExpenses = expensesByOrigin(entries, "GANANCIA_BRUTA");

        CashControlSummary summary = cashControlSummaryRepository.findByStoreIdAndDate(storeId, LocalDate.now())
                .orElseGet(CashControlSummary::new);

        summary.setStoreId(storeId);
        summary.setSummaryDate(LocalDate.now());
        summary.setMoneyReal(summary.getMoneyReal() == null ? BigDecimal.ZERO : summary.getMoneyReal());
        summary.setMoneyPhysical(summary.getMoneyPhysical() == null ? BigDecimal.ZERO : summary.getMoneyPhysical());
        summary.setStoreMoney(summary.getStoreMoney() == null ? BigDecimal.ZERO : summary.getStoreMoney());
        summary.setGrossProfitMoney(summary.getGrossProfitMoney() == null ? BigDecimal.ZERO : summary.getGrossProfitMoney());
        summary.setTotalExpenses(totalExpenses);
        summary.setBalanceAvailable(summary.getGrossProfitMoney().subtract(grossProfitExpenses));
        return summary;
    }

    @Transactional
    public CashControlSummary updateSummary(Long storeId, CashControlSummaryUpdateDTO dto, String username) {
        Store store = storeService.findStoreEntity(storeId);
        if (!store.getManager().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para editar el control de caja");
        }

        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La información del resumen es obligatoria");
        }
        if (dto.getVerificationPassword() == null || dto.getVerificationPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere la contraseña de la tienda para editar el dinero manual");
        }
        storeService.validateAccessPassword(storeId, dto.getVerificationPassword());

        CashControlSummary summary = cashControlSummaryRepository.findByStoreIdAndDate(storeId, LocalDate.now())
                .orElseGet(CashControlSummary::new);

        BigDecimal previousMoneyReal = summary.getMoneyReal();
        BigDecimal previousMoneyPhysical = summary.getMoneyPhysical();
        BigDecimal previousStoreMoney = summary.getStoreMoney();
        BigDecimal previousGrossProfitMoney = summary.getGrossProfitMoney();

        summary.setStoreId(storeId);
        summary.setSummaryDate(LocalDate.now());
        summary.setMoneyReal(dto.getMoneyReal() == null ? BigDecimal.ZERO : dto.getMoneyReal());
        summary.setMoneyPhysical(dto.getMoneyPhysical() == null ? BigDecimal.ZERO : dto.getMoneyPhysical());
        summary.setStoreMoney(dto.getStoreMoney() == null ? BigDecimal.ZERO : dto.getStoreMoney());
        summary.setGrossProfitMoney(dto.getGrossProfitMoney() == null ? BigDecimal.ZERO : dto.getGrossProfitMoney());
        boolean moneyChanged = !sameMoney(previousMoneyReal, summary.getMoneyReal())
            || !sameMoney(previousMoneyPhysical, summary.getMoneyPhysical())
            || !sameMoney(previousStoreMoney, summary.getStoreMoney())
            || !sameMoney(previousGrossProfitMoney, summary.getGrossProfitMoney());

        List<CashControlEntry> entries = cashControlEntryRepository.findByStoreId(storeId);
        BigDecimal totalExpenses = totalExpenses(entries);
        BigDecimal grossProfitExpenses = expensesByOrigin(entries, "GANANCIA_BRUTA");

        summary.setTotalExpenses(totalExpenses);
        summary.setBalanceAvailable(summary.getGrossProfitMoney().subtract(grossProfitExpenses));
        CashControlSummary savedSummary = cashControlSummaryRepository.save(summary);

        if (moneyChanged) {
            User updatedBy = userService.findByUsername(username);
            CashControlEntry historyEntry = new CashControlEntry();
            historyEntry.setStore(store);
            historyEntry.setCreatedBy(updatedBy);
            historyEntry.setEntryDate(LocalDate.now());
            historyEntry.setConcept("Actualización de dinero manual");
            historyEntry.setAmount(BigDecimal.ZERO);
            historyEntry.setExpenseType("AJUSTE_MANUAL");
            historyEntry.setMoneyOrigin("TIENDA");
            historyEntry.setDetail(String.format(
                "Tienda: %s -> %s; Ganancia bruta: %s -> %s; Físico: %s -> %s; Digital: %s -> %s",
                valueOrZero(previousStoreMoney), savedSummary.getStoreMoney(),
                valueOrZero(previousGrossProfitMoney), savedSummary.getGrossProfitMoney(),
                valueOrZero(previousMoneyPhysical), savedSummary.getMoneyPhysical(),
                valueOrZero(previousMoneyReal), savedSummary.getMoneyReal()));
            historyEntry.setResponsibleName(updatedBy.getUsername());
            historyEntry.setStatus("ACTUALIZACION_MANUAL");
            cashControlEntryRepository.save(historyEntry);
        }

        return savedSummary;
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal totalExpenses(List<CashControlEntry> entries) {
        return entries.stream()
                .filter(entry -> !entry.isDeleted())
                .map(entry -> valueOrZero(entry.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal expensesByOrigin(List<CashControlEntry> entries, String origin) {
        return entries.stream()
                .filter(entry -> !entry.isDeleted() && origin.equalsIgnoreCase(entry.getMoneyOrigin()))
                .map(entry -> valueOrZero(entry.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean sameMoney(BigDecimal first, BigDecimal second) {
        return valueOrZero(first).compareTo(valueOrZero(second)) == 0;
    }

    @Transactional
    public void deleteEntry(Long entryId, String username) {
        CashControlEntry entry = cashControlEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro no encontrado"));

        if (!entry.getStore().getManager().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para eliminar este registro");
        }

        // La eliminación también requiere validación de contraseña porque afecta el dinero manual.
        if (entry.getStore().getAccessPassword() == null || entry.getStore().getAccessPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "La tienda no tiene contraseña configurada");
        }

        entry.setDeleted(true);
        cashControlEntryRepository.save(entry);
    }

    private String normalizeExpenseType(String value) {
        if (value == null) {
            return "GASTO_USUARIO";
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.equals("GASTO_TIENDA") || normalized.equals("TIENDA")) {
            return "GASTO_TIENDA";
        }
        return "GASTO_USUARIO";
    }

    private String normalizeMoneyOrigin(String value) {
        if (value == null) {
            return "TIENDA";
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.equals("GANANCIA_BRUTA") || normalized.equals("GANANCIA")) {
            return "GANANCIA_BRUTA";
        }
        return "TIENDA";
    }
}
