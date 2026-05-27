package com.inventario.licoreria.modules.inventory.service;

import com.inventario.licoreria.modules.inventory.dto.PaymentMethodDTO;
import com.inventario.licoreria.modules.inventory.model.PaymentMethod;
import com.inventario.licoreria.modules.inventory.model.Transaction;
import com.inventario.licoreria.modules.inventory.repository.PaymentMethodRepository;
import com.inventario.licoreria.modules.inventory.repository.TransactionRepository;
import com.inventario.licoreria.modules.payment_methods.model.PaymentMethodConfig;
import com.inventario.licoreria.modules.payment_methods.repository.PaymentMethodConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import org.springframework.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PaymentMethodService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentMethodService.class);
    private final PaymentMethodRepository paymentMethodRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentMethodConfigRepository paymentMethodConfigRepository;

    public PaymentMethodService(PaymentMethodRepository paymentMethodRepository, 
                                TransactionRepository transactionRepository,
                                PaymentMethodConfigRepository paymentMethodConfigRepository) {
        this.paymentMethodRepository = paymentMethodRepository;
        this.transactionRepository = transactionRepository;
        this.paymentMethodConfigRepository = paymentMethodConfigRepository;
    }

    public PaymentMethod create(final PaymentMethodDTO dto) {
        logger.info("🔄 [CREATE PAYMENT METHOD] Iniciando creación: transactionId={}, paymentMethodConfigId={}",
            dto.getTransactionId(), dto.getPaymentMethodConfigId());
        
        try {
            final Transaction transaction = transactionRepository.findById(dto.getTransactionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transacción no encontrada"));
            logger.info("✅ [CREATE PAYMENT METHOD] Transacción encontrada: ID = {}", transaction.getId());
            
            final PaymentMethodConfig config = paymentMethodConfigRepository.findById(dto.getPaymentMethodConfigId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Configuración de método de pago no encontrada"));
            logger.info("✅ [CREATE PAYMENT METHOD] Configuración encontrada: {} ({})", config.getName(), config.getType());
            
            // Validar que la transacción no tenga ya un método de pago
            if (transaction.getPaymentMethod() != null) {
                logger.warn("⚠️ [CREATE PAYMENT METHOD] La transacción {} ya tiene un método de pago asignado", transaction.getId());
                throw new ResponseStatusException(HttpStatus.CONFLICT, 
                    "Esta transacción ya tiene un método de pago asignado");
            }
            
            final PaymentMethod paymentMethod = new PaymentMethod();
            paymentMethod.setTransaction(transaction);
            paymentMethod.setPaymentMethodConfig(config);
            
            logger.info("💾 [CREATE PAYMENT METHOD] Guardando método de pago en BD...");
            final PaymentMethod saved = paymentMethodRepository.save(paymentMethod);
            logger.info("✅ [CREATE PAYMENT METHOD] Método de pago guardado exitosamente: ID = {}", saved.getId());
            
            return saved;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("❌ [CREATE PAYMENT METHOD] Error: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error al crear método de pago: " + e.getMessage());
        }
    }

    public PaymentMethod findById(@NonNull Long id) {
        logger.info("🔍 [FIND PAYMENT METHOD] Buscando método de pago: ID = {}", id);
        return paymentMethodRepository.findById(id)
            .orElseThrow(() -> {
                logger.error("❌ [FIND PAYMENT METHOD] Método de pago no encontrado: ID = {}", id);
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "Método de pago no encontrado");
            });
    }

    public PaymentMethod findByTransactionId(@NonNull Long transactionId) {
        logger.info("🔍 [FIND BY TRANSACTION] Buscando método de pago para transacción: ID = {}", transactionId);
        return paymentMethodRepository.findByTransactionId(transactionId)
            .orElseThrow(() -> {
                logger.error("❌ [FIND BY TRANSACTION] Método de pago no encontrado para transacción: ID = {}", transactionId);
                return new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    "Método de pago no encontrado para esta transacción");
            });
    }

    public List<PaymentMethod> findAll() {
        logger.info("🔍 [FIND ALL] Obteniendo todos los métodos de pago");
        return paymentMethodRepository.findAll();
    }

    public PaymentMethod update(@NonNull Long id, final PaymentMethodDTO dto) {
        logger.info("🔄 [UPDATE PAYMENT METHOD] Actualizando: ID = {}, newConfigId = {}", id, dto.getPaymentMethodConfigId());
        
        try {
            final PaymentMethod existing = findById(id);
            
            final PaymentMethodConfig newConfig = paymentMethodConfigRepository.findById(dto.getPaymentMethodConfigId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Configuración de método de pago no encontrada"));
            
            existing.setPaymentMethodConfig(newConfig);
            
            logger.info("💾 [UPDATE PAYMENT METHOD] Guardando cambios...");
            final PaymentMethod updated = paymentMethodRepository.save(existing);
            logger.info("✅ [UPDATE PAYMENT METHOD] Actualizado exitosamente: ID = {}", updated.getId());
            
            return updated;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("❌ [UPDATE PAYMENT METHOD] Error: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error al actualizar método de pago: " + e.getMessage());
        }
    }

    public void delete(@NonNull Long id) {
        logger.info("🗑️ [DELETE PAYMENT METHOD] Eliminando: ID = {}", id);
        
        try {
            if (!paymentMethodRepository.existsById(id)) {
                logger.error("❌ [DELETE PAYMENT METHOD] Método de pago no encontrado: ID = {}", id);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Método de pago no encontrado");
            }
            
            paymentMethodRepository.deleteById(id);
            logger.info("✅ [DELETE PAYMENT METHOD] Eliminado exitosamente: ID = {}", id);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("❌ [DELETE PAYMENT METHOD] Error: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Error al eliminar método de pago: " + e.getMessage());
        }
    }
}
