package com.inventario.licoreria.modules.inventory.controller;

import com.inventario.licoreria.modules.inventory.dto.PaymentMethodDTO;
import com.inventario.licoreria.modules.inventory.model.PaymentMethod;
import com.inventario.licoreria.modules.inventory.service.PaymentMethodService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/payment-methods")
public class PaymentMethodController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentMethodController.class);
    private final PaymentMethodService paymentMethodService;

    public PaymentMethodController(PaymentMethodService paymentMethodService) {
        this.paymentMethodService = paymentMethodService;
    }

    @GetMapping
    public List<PaymentMethod> getAll() {
        logger.info("📨 [API] GET /api/payment-methods");
        return paymentMethodService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentMethod> getById(@PathVariable @NonNull Long id) {
        logger.info("📨 [API] GET /api/payment-methods/{} - Obteniendo método de pago", id);
        return ResponseEntity.ok(paymentMethodService.findById(id));
    }

    @GetMapping("/transaction/{transactionId}")
    public ResponseEntity<PaymentMethod> getByTransactionId(@PathVariable @NonNull Long transactionId) {
        logger.info("📨 [API] GET /api/payment-methods/transaction/{} - Obteniendo método de pago", transactionId);
        return ResponseEntity.ok(paymentMethodService.findByTransactionId(transactionId));
    }

    @PostMapping
    public ResponseEntity<PaymentMethod> create(@Valid @RequestBody PaymentMethodDTO dto) {
        logger.info("📨 [API] POST /api/payment-methods - Creando método de pago: transactionId={}, configId={}", 
            dto.getTransactionId(), dto.getPaymentMethodConfigId());
        try {
            PaymentMethod created = paymentMethodService.create(dto);
            logger.info("✅ [API] Método de pago creado exitosamente: ID = {}", created.getId());
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            logger.error("❌ [API] Error al crear método de pago: {}", e.getMessage());
            throw e;
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<PaymentMethod> update(@PathVariable @NonNull Long id, @Valid @RequestBody PaymentMethodDTO dto) {
        logger.info("📨 [API] PUT /api/payment-methods/{} - Actualizando método de pago: newConfigId={}", id, dto.getPaymentMethodConfigId());
        try {
            PaymentMethod updated = paymentMethodService.update(id, dto);
            logger.info("✅ [API] Método de pago actualizado exitosamente: ID = {}", updated.getId());
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            logger.error("❌ [API] Error al actualizar método de pago: {}", e.getMessage());
            throw e;
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
        logger.info("📨 [API] DELETE /api/payment-methods/{} - Eliminando método de pago", id);
        try {
            paymentMethodService.delete(id);
            logger.info("✅ [API] Método de pago eliminado exitosamente: ID = {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("❌ [API] Error al eliminar método de pago: {}", e.getMessage());
            throw e;
        }
    }
}
