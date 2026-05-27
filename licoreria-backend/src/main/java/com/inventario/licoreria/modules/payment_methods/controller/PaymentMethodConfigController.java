package com.inventario.licoreria.modules.payment_methods.controller;

import com.inventario.licoreria.modules.payment_methods.dto.PaymentMethodConfigDTO;
import com.inventario.licoreria.modules.payment_methods.model.PaymentMethodConfig;
import com.inventario.licoreria.modules.payment_methods.service.PaymentMethodConfigService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/payment-method-configs")
public class PaymentMethodConfigController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentMethodConfigController.class);
    private final PaymentMethodConfigService paymentMethodConfigService;

    public PaymentMethodConfigController(PaymentMethodConfigService paymentMethodConfigService) {
        this.paymentMethodConfigService = paymentMethodConfigService;
    }

    @GetMapping
    public List<PaymentMethodConfig> getAll() {
        logger.info("📨 [API] GET /api/payment-method-configs - Obteniendo todos los métodos");
        return paymentMethodConfigService.findAll();
    }

    @GetMapping("/active")
    public List<PaymentMethodConfig> getAllActive() {
        logger.info("📨 [API] GET /api/payment-method-configs/active - Obteniendo métodos activos");
        return paymentMethodConfigService.findAllActive();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentMethodConfig> getById(@PathVariable @NonNull Long id) {
        logger.info("📨 [API] GET /api/payment-method-configs/{} - Obteniendo configuración", id);
        return ResponseEntity.ok(paymentMethodConfigService.findById(id));
    }

    @PostMapping
    public ResponseEntity<PaymentMethodConfig> create(@Valid @RequestBody PaymentMethodConfigDTO dto) {
        logger.info("📨 [API] POST /api/payment-method-configs - Creando: name={}, type={}", 
            dto.getName(), dto.getType());
        try {
            PaymentMethodConfig created = paymentMethodConfigService.create(dto);
            logger.info("✅ [API] Configuración creada exitosamente: ID = {}", created.getId());
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            logger.error("❌ [API] Error al crear configuración: {}", e.getMessage());
            throw e;
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<PaymentMethodConfig> update(
        @PathVariable @NonNull Long id, 
        @Valid @RequestBody PaymentMethodConfigDTO dto) {
        logger.info("📨 [API] PUT /api/payment-method-configs/{} - Actualizando: newName={}, newType={}", 
            id, dto.getName(), dto.getType());
        try {
            PaymentMethodConfig updated = paymentMethodConfigService.update(id, dto);
            logger.info("✅ [API] Configuración actualizada exitosamente: ID = {}", updated.getId());
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            logger.error("❌ [API] Error al actualizar configuración: {}", e.getMessage());
            throw e;
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
        logger.info("📨 [API] DELETE /api/payment-method-configs/{} - Eliminando", id);
        try {
            paymentMethodConfigService.delete(id);
            logger.info("✅ [API] Configuración eliminada exitosamente: ID = {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("❌ [API] Error al eliminar configuración: {}", e.getMessage());
            throw e;
        }
    }
}
