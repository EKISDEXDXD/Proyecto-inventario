package com.inventario.licoreria.modules.cash_control.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.inventario.licoreria.modules.cash_control.dto.CashControlEntryDTO;
import com.inventario.licoreria.modules.cash_control.model.CashControlEntry;
import com.inventario.licoreria.modules.cash_control.repository.CashControlEntryRepository;
import com.inventario.licoreria.modules.store.model.Store;
import com.inventario.licoreria.modules.store.repository.StoreRepository;
import com.inventario.licoreria.modules.store.service.StoreService;
import com.inventario.licoreria.modules.users.model.User;
import com.inventario.licoreria.modules.users.service.UserService;

@ExtendWith(MockitoExtension.class)
class CashControlServiceTest {

    @Mock
    private CashControlEntryRepository balanceEntryRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private StoreService storeService;

    @Mock
    private UserService userService;

    @InjectMocks
    private CashControlService cashControlService;

    private StoreService realStoreService;

    private Store store;
    private User manager;

    @BeforeEach
    void setUp() {
        manager = new User();
        manager.setId(1L);
        manager.setUsername("admin");

        store = new Store();
        store.setId(10L);
        store.setManager(manager);
        store.setAccessPassword("$2a$10$hashed");

        realStoreService = new StoreService(storeRepository, userService);
    }

    @Test
    void shouldRejectValidationWhenStorePasswordIsNotConfigured() {
        store.setAccessPassword(null);
        when(storeRepository.findById(10L)).thenReturn(Optional.of(store));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> realStoreService.validateAccessPassword(10L, "secret123"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("La tienda no tiene contraseña configurada", ex.getReason());
    }

    @Test
    void shouldCreateExpenseEntryAndComputeSummary() {
        CashControlEntryDTO dto = new CashControlEntryDTO();
        dto.setStoreId(10L);
        dto.setConcept("Gasolina");
        dto.setAmount(new BigDecimal("120"));
        dto.setExpenseType("GASTO_USUARIO");
        dto.setMoneyOrigin("TIENDA");
        dto.setDetail("Reabastecimiento");
        dto.setEntryDate(LocalDate.now());
        dto.setResponsibleName("Erik");
        dto.setVerificationPassword("secret123");

        when(storeService.findStoreEntity(anyLong())).thenReturn(store);
        when(userService.findByUsername("admin")).thenReturn(manager);
        when(balanceEntryRepository.save(any(CashControlEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CashControlEntry saved = cashControlService.createEntry(dto, "admin");

        assertEquals("Gasolina", saved.getConcept());
        assertEquals(new BigDecimal("120"), saved.getAmount());
    }

    @Test
    void shouldAllowDailyFlowClosureWithZeroAmountWhenPasswordIsValid() {
        CashControlEntryDTO dto = new CashControlEntryDTO();
        dto.setStoreId(10L);
        dto.setConcept("Cierre de flujo diario");
        dto.setAmount(BigDecimal.ZERO);
        dto.setExpenseType("GASTO_USUARIO");
        dto.setMoneyOrigin("TIENDA");
        dto.setDetail("Cierre de flujo de 2026-08-29");
        dto.setEntryDate(LocalDate.now());
        dto.setResponsibleName("Erik");
        dto.setStatus("CERRADO");
        dto.setVerificationPassword("secret123");

        when(storeService.findStoreEntity(anyLong())).thenReturn(store);
        when(userService.findByUsername("admin")).thenReturn(manager);
        doAnswer(invocation -> null).when(storeService).validateAccessPassword(anyLong(), any());
        when(balanceEntryRepository.save(any(CashControlEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CashControlEntry saved = cashControlService.createEntry(dto, "admin");

        assertEquals("Cierre de flujo diario", saved.getConcept());
        assertEquals(BigDecimal.ZERO, saved.getAmount());
        assertEquals("CERRADO", saved.getStatus());
    }

    @Test
    void shouldCreateManualExpenseWithoutPassword() {
        CashControlEntryDTO dto = new CashControlEntryDTO();
        dto.setStoreId(10L);
        dto.setConcept("Transporte");
        dto.setAmount(new BigDecimal("15"));
        dto.setEntryDate(LocalDate.now());

        when(storeService.findStoreEntity(anyLong())).thenReturn(store);
        when(userService.findByUsername("admin")).thenReturn(manager);
        when(balanceEntryRepository.save(any(CashControlEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CashControlEntry saved = cashControlService.createEntry(dto, "admin");

        assertEquals("Transporte", saved.getConcept());
        assertEquals(new BigDecimal("15"), saved.getAmount());
    }
}
