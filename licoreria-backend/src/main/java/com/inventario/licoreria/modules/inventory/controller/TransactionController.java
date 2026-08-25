package com.inventario.licoreria.modules.inventory.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.inventario.licoreria.modules.inventory.dto.BatchTransactionDTO;
import com.inventario.licoreria.modules.inventory.dto.TransactionDTO;
import com.inventario.licoreria.modules.inventory.model.Transaction;
import com.inventario.licoreria.modules.inventory.service.TransactionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);
    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public List<Transaction> getAll() {
        return transactionService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getById(@PathVariable @NonNull Long id) {
        return ResponseEntity.ok(transactionService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Transaction> create(@Valid @RequestBody TransactionDTO dto) {
        logger.info("📨 [API] POST /api/transactions - Recibida solicitud: productId={}, type={}, quantity={}, userId={}", 
            dto.getProductId(), dto.getType(), dto.getQuantity(), dto.getUserId());
        try {
            Transaction created = transactionService.create(dto);
            logger.info("✅ [API] Transacción creada exitosamente: ID = {}", created.getId());
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            logger.error("❌ [API] Error al crear transacción: {}", e.getMessage());
            throw e;
        }
    }

    @PostMapping("/batch")
    public ResponseEntity<List<Transaction>> createBatch(@Valid @RequestBody BatchTransactionDTO request) {
        logger.info("📦 [API] POST /api/transactions/batch - Recibida solicitud para crear {} transacciones", 
            request.getTransactions() != null ? request.getTransactions().size() : 0);
        try {
            List<TransactionDTO> transactions = request.getTransactions();
            if (transactions == null || transactions.isEmpty()) {
                logger.warn("⚠️ [API] Se recibió una solicitud batch vacía");
                return ResponseEntity.ok(List.of());
            }
            List<Transaction> created = transactionService.createBatch(transactions);
            logger.info("✅ [API] {} transacciones creadas exitosamente en lote", created.size());
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            logger.error("❌ [API] Error al crear transacciones en lote: {}", e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/product/{productId}")
    public List<Transaction> getByProduct(@PathVariable @NonNull Long productId) {
        return transactionService.findByProductId(productId);
    }

    @GetMapping("/store/{storeId}")
    public List<Transaction> getByStore(@PathVariable @NonNull Long storeId) {
        return transactionService.findByStoreId(storeId);
    }

    @GetMapping("/store/{storeId}/page")
    public Page<Transaction> getPageByStore(@PathVariable @NonNull Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        return transactionService.findPageByStoreId(storeId, PageRequest.of(safePage, safeSize));
    }

    @GetMapping("/range")
    public List<Transaction> getByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return transactionService.findByDateRange(start, end);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
        transactionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}