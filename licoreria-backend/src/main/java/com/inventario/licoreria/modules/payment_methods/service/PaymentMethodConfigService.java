package com.inventario.licoreria.modules.payment_methods.service;

import com.inventario.licoreria.modules.payment_methods.dto.PaymentMethodConfigDTO;
import com.inventario.licoreria.modules.payment_methods.model.PaymentMethodConfig;
import com.inventario.licoreria.modules.payment_methods.repository.PaymentMethodConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import org.springframework.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PaymentMethodConfigService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentMethodConfigService.class);
    private final PaymentMethodConfigRepository paymentMethodConfigRepository;

    public PaymentMethodConfigService(PaymentMethodConfigRepository paymentMethodConfigRepository) {
        this.paymentMethodConfigRepository = paymentMethodConfigRepository;
    }

    @Transactional
    public PaymentMethodConfig create(final PaymentMethodConfigDTO dto) {
        logger.info("🔄 [CREATE PAYMENT METHOD CONFIG] Iniciando creación: name={}, type={}",
            dto.getName(), dto.getType());
        
        try {
            // Validar que si es QR, debe tener imagen
            if ("QR".equalsIgnoreCase(dto.getType()) && (dto.getImageUrl() == null || dto.getImageUrl().trim().isEmpty())) {
                logger.warn("⚠️ [CREATE PAYMENT METHOD CONFIG] QR sin imagen URL");
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Para un método QR, la imagen es obligatoria");
            }
            
            final PaymentMethodConfig config = new PaymentMethodConfig();
            config.setName(dto.getName().trim());
            config.setType(dto.getType().toUpperCase());
            config.setImageUrl(dto.getImageUrl());
            config.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
            
            logger.info("💾 [CREATE PAYMENT METHOD CONFIG] Guardando configuración de método de pago...");
            final PaymentMethodConfig saved = paymentMethodConfigRepository.save(config);
            logger.info("✅ [CREATE PAYMENT METHOD CONFIG] Guardado exitosamente: ID = {}", saved.getId());
            
            return saved;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("❌ [CREATE PAYMENT METHOD CONFIG] Error: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Error al crear método de pago: " + e.getMessage());
        }
    }

    public PaymentMethodConfig findById(@NonNull Long id) {
        logger.info("🔍 [FIND PAYMENT METHOD CONFIG] Buscando: ID = {}", id);
        return paymentMethodConfigRepository.findById(id)
            .orElseThrow(() -> {
                logger.error("❌ [FIND PAYMENT METHOD CONFIG] No encontrado: ID = {}", id);
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "Método de pago no encontrado");
            });
    }

    public List<PaymentMethodConfig> findAllActive() {
        logger.info("🔍 [FIND ALL ACTIVE] Obteniendo todos los métodos activos globalmente");
        return paymentMethodConfigRepository.findAllActive();
    }

    public List<PaymentMethodConfig> findAll() {
        logger.info("🔍 [FIND ALL] Obteniendo todos los métodos globalmente");
        return paymentMethodConfigRepository.findAllOrdered();
    }

    @Transactional
    public PaymentMethodConfig update(@NonNull Long id, final PaymentMethodConfigDTO dto) {
        logger.info("🔄 [UPDATE PAYMENT METHOD CONFIG] Actualizando: ID = {}, newName = {}, newType = {}", 
            id, dto.getName(), dto.getType());
        
        try {
            final PaymentMethodConfig existing = findById(id);
            
            // Validar que si es QR, debe tener imagen
            if ("QR".equalsIgnoreCase(dto.getType()) && (dto.getImageUrl() == null || dto.getImageUrl().trim().isEmpty())) {
                logger.warn("⚠️ [UPDATE PAYMENT METHOD CONFIG] QR sin imagen URL");
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Para un método QR, la imagen es obligatoria");
            }
            
            existing.setName(dto.getName().trim());
            existing.setType(dto.getType().toUpperCase());
            existing.setImageUrl(dto.getImageUrl());
            if (dto.getIsActive() != null) {
                existing.setIsActive(dto.getIsActive());
            }
            
            logger.info("💾 [UPDATE PAYMENT METHOD CONFIG] Guardando cambios...");
            final PaymentMethodConfig updated = paymentMethodConfigRepository.save(existing);
            logger.info("✅ [UPDATE PAYMENT METHOD CONFIG] Actualizado exitosamente: ID = {}", updated.getId());
            
            return updated;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("❌ [UPDATE PAYMENT METHOD CONFIG] Error: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Error al actualizar método de pago: " + e.getMessage());
        }
    }

    @Transactional
    public void delete(@NonNull Long id) {
        logger.info("🗑️ [DELETE PAYMENT METHOD CONFIG] Desactivando (soft delete): ID = {}", id);
        
        try {
            final PaymentMethodConfig existing = findById(id);
            // Soft delete: marcar como inactivo en lugar de eliminar físicamente
            existing.setIsActive(false);
            paymentMethodConfigRepository.save(existing);
            logger.info("✅ [DELETE PAYMENT METHOD CONFIG] Desactivado exitosamente: ID = {}", id);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("❌ [DELETE PAYMENT METHOD CONFIG] Error: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Error al eliminar método de pago: " + e.getMessage());
        }
    }
}
