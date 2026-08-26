package com.inventario.licoreria.modules.inventory.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.inventario.licoreria.modules.dashboard.service.DashboardSummaryService;
import com.inventario.licoreria.modules.inventory.dto.PaymentMethodDTO;
import com.inventario.licoreria.modules.inventory.dto.TransactionDTO;
import com.inventario.licoreria.modules.inventory.model.Transaction;
import com.inventario.licoreria.modules.inventory.repository.TransactionRepository;
import com.inventario.licoreria.modules.products.dto.ProductDTO;
import com.inventario.licoreria.modules.products.dto.StockTransformationRequestDTO;
import com.inventario.licoreria.modules.products.model.Product;
import com.inventario.licoreria.modules.products.service.ProductService;
import com.inventario.licoreria.modules.users.model.User;
import com.inventario.licoreria.modules.users.service.UserService;

@Service
public class TransactionService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);
    private final TransactionRepository transactionRepository;
    private final ProductService productService; // Inyectar para actualizar stock
    private final UserService userService;
    private final PaymentMethodService paymentMethodService;
    private final DashboardSummaryService dashboardSummaryService;

    public TransactionService(TransactionRepository transactionRepository, ProductService productService, UserService userService, PaymentMethodService paymentMethodService, DashboardSummaryService dashboardSummaryService) {
        this.transactionRepository = transactionRepository;
        this.productService = productService;
        this.userService = userService;
        this.paymentMethodService = paymentMethodService;
        this.dashboardSummaryService = dashboardSummaryService;
    }

    public List<Transaction> findAll() {
        return transactionRepository.findAll();
    }

    @Transactional 
    public Transaction create(final TransactionDTO dto) {
        logger.info("🔄 [CREATE TRANSACTION] Iniciando creación de transacción: productId={}, type={}, quantity={}, reason={}, userId={}",
            dto.getProductId(), dto.getType(), dto.getQuantity(), dto.getReason(), dto.getUserId());
        
        try {
            final Product product = productService.findById(dto.getProductId());
            logger.info("✅ [CREATE TRANSACTION] Producto encontrado: {} (ID: {})", product.getName(), product.getId());
            
            final User user = userService.findById(dto.getUserId());
            logger.info("✅ [CREATE TRANSACTION] Usuario encontrado: {} (ID: {})", user.getUsername(), user.getId());

            final String tipo = dto.getType();
            if (!"ENTRADA".equalsIgnoreCase(tipo) && !"SALIDA".equalsIgnoreCase(tipo)) {
                logger.error("❌ [CREATE TRANSACTION] Tipo inválido: {}", tipo);
                throw new RuntimeException("Tipo de transacción inválido: " + tipo);
            }
            
            // Validar reason según el tipo
            final String reason = dto.getReason();
            validateReason(tipo, reason);
            logger.info("✅ [CREATE TRANSACTION] Reason validado: {}", reason);
            
            final int stockDelta;
            if ("ENTRADA".equalsIgnoreCase(tipo)) {
                stockDelta = dto.getQuantity();
                logger.info("📦 [CREATE TRANSACTION] ENTRADA: stock delta = +{}", stockDelta);
            } else {
                stockDelta = -dto.getQuantity();
                logger.info("📤 [CREATE TRANSACTION] SALIDA: stock delta = {}", stockDelta);
            }
            
            productService.adjustStock(product.getId(), stockDelta);
            logger.info("✅ [CREATE TRANSACTION] Stock actualizado de {} a {}", product.getStock(), product.getStock() + stockDelta);
            
            final Transaction transaction = new Transaction();
            transaction.setProduct(product);
            transaction.setType(tipo.toUpperCase());
            transaction.setQuantity(dto.getQuantity());
            transaction.setReason(reason.toUpperCase());
            transaction.setDateTime(dto.getDateTime() != null ? dto.getDateTime() : LocalDateTime.now());
            transaction.setUser(user);
            
            logger.info("💾 [CREATE TRANSACTION] Guardando transacción en BD...");
            final Transaction saved = transactionRepository.save(transaction);
            logger.info("✅ [CREATE TRANSACTION] Transacción guardada exitosamente: ID = {}", saved.getId());
            
            // Crear PaymentMethod si se proporciona en el DTO
            if (dto.getPaymentMethodConfigId() != null) {
                logger.info("💳 [CREATE TRANSACTION] Creando método de pago: configId = {}", dto.getPaymentMethodConfigId());
                try {
                    PaymentMethodDTO paymentMethodDTO = new PaymentMethodDTO(saved.getId(), dto.getPaymentMethodConfigId());
                    paymentMethodService.create(paymentMethodDTO);
                    logger.info("✅ [CREATE TRANSACTION] Método de pago creado exitosamente");
                } catch (Exception e) {
                    logger.error("⚠️ [CREATE TRANSACTION] Error al crear método de pago (no crítico): {}", e.getMessage());
                    // No lanzamos excepción para no romper la transacción
                }
            }

            dashboardSummaryService.markStoreDirty(product.getStore().getId());
            
            return saved;
        } catch (RuntimeException e) {
            logger.error("❌ [CREATE TRANSACTION] Error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("❌ [CREATE TRANSACTION] Error inesperado: {}", e.getMessage(), e);
            throw new RuntimeException("Error al crear transacción: " + e.getMessage());
        }
    }

    /**
     * Valida que el motivo (reason) sea válido según el tipo de transacción
     */
    private void validateReason(String type, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new RuntimeException("El motivo es obligatorio");
        }
        
        final String reasonUpper = reason.toUpperCase();
        
        if ("ENTRADA".equalsIgnoreCase(type)) {
            // ENTRADA: solo COMPRA y AJUSTE
            if (!reasonUpper.equals("COMPRA") && !reasonUpper.equals("AJUSTE")) {
                logger.error("❌ [VALIDATE REASON] Motivo inválido para ENTRADA: {}. Opciones válidas: COMPRA, AJUSTE", reason);
                throw new RuntimeException("Para una ENTRADA, el motivo debe ser COMPRA o AJUSTE. Recibido: " + reason);
            }
        } else if ("SALIDA".equalsIgnoreCase(type)) {
            // SALIDA: VENTA, DEVOLUCIÓN, PÉRDIDA, AJUSTE
            if (!reasonUpper.equals("VENTA") && !reasonUpper.equals("DEVOLUCION") && 
                !reasonUpper.equals("PERDIDA") && !reasonUpper.equals("AJUSTE")) {
                logger.error("❌ [VALIDATE REASON] Motivo inválido para SALIDA: {}. Opciones válidas: VENTA, DEVOLUCION, PERDIDA, AJUSTE", reason);
                throw new RuntimeException("Para una SALIDA, el motivo debe ser VENTA, DEVOLUCION, PERDIDA o AJUSTE. Recibido: " + reason);
            }
        }
    }

    @org.springframework.lang.NonNull
    @SuppressWarnings("null")
    public Transaction findById(@NonNull Long id) {
        return transactionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transacción no encontrada"));
    }

    // Buscar transacciones por producto
    public List<Transaction> findByProductId(@NonNull Long productId) {
        return transactionRepository.findByProductIdOrderByDateTimeDesc(productId);
    }

    // Buscar transacciones por rango de fechas
    public List<Transaction> findByDateRange(LocalDateTime start, LocalDateTime end) {
        return transactionRepository.findByDateTimeBetweenOrderByDateTimeDesc(start, end);
    }

    // Buscar transacciones por tienda
    public List<Transaction> findByStoreId(@NonNull Long storeId) {
        return transactionRepository.findByStoreIdOrderByDateTimeDesc(storeId);
    }

    // Buscar transacciones por tienda desde una fecha (carga inicial liviana del dashboard)
    public List<Transaction> findByStoreId(@NonNull Long storeId, LocalDateTime desde) {
        if (desde == null) {
            return findByStoreId(storeId);
        }
        return transactionRepository.findByStoreIdAndDateTimeAfter(storeId, desde);
    }

    public Page<Transaction> findPageByStoreId(@NonNull Long storeId, Pageable pageable) {
        return transactionRepository.findPageByStoreId(storeId, pageable);
    }

    @Transactional
    public Transaction update(@NonNull final Long id, final TransactionDTO dto) {
    final Transaction existing = findById(id);
    final Long storeId = existing.getProduct().getStore().getId();

    final int existingQuantity = existing.getQuantity();
    final int oldDelta = "ENTRADA".equalsIgnoreCase(existing.getType())
        ? existingQuantity
        : -existingQuantity;

    final int newQuantity = dto.getQuantity();
    final int newDelta = "ENTRADA".equalsIgnoreCase(dto.getType())
        ? newQuantity
        : -newQuantity;

    final int delta = newDelta - oldDelta;
    productService.adjustStock(existing.getProductId(), delta);

    existing.setType(dto.getType().toUpperCase());
    existing.setQuantity(newQuantity);
    existing.setDateTime(dto.getDateTime() != null ? dto.getDateTime() : existing.getDateTime());
    existing.setUserId(dto.getUserId());
    Transaction updated = transactionRepository.save(existing);
    dashboardSummaryService.markStoreDirty(storeId);
    return updated;
    }

    @Transactional
    public void delete(@NonNull final Long id) {
        final Transaction transaction = findById(id);
        final Long storeId = transaction.getProduct().getStore().getId();
        final int quantity = transaction.getQuantity();
        int revertDelta = "ENTRADA".equalsIgnoreCase(transaction.getType()) 
            ? -quantity 
            : quantity;
        productService.adjustStock(transaction.getProductId(), revertDelta);
        transactionRepository.delete(transaction);
        dashboardSummaryService.markStoreDirty(storeId);
    }

    @Transactional
    public List<Transaction> createBatch(final List<TransactionDTO> dtos) {
        logger.info("🔄 [CREATE BATCH TRANSACTIONS] Iniciando creación de {} transacciones en lote", dtos.size());
        
        try {
            List<Transaction> createdTransactions = new java.util.ArrayList<>();
            
            for (TransactionDTO dto : dtos) {
                Transaction created = create(dto);
                createdTransactions.add(created);
            }
            
            logger.info("✅ [CREATE BATCH TRANSACTIONS] {} transacciones creadas exitosamente en lote", createdTransactions.size());
            return createdTransactions;
        } catch (Exception e) {
            logger.error("❌ [CREATE BATCH TRANSACTIONS] Error al crear transacciones en lote: {}", e.getMessage());
            throw new RuntimeException("Error al crear transacciones en lote: " + e.getMessage());
        }
    }

    /**
     * Ajuste de precio / promo: saca (SALIDA-AJUSTE) uno o varios productos origen y mete
     * (ENTRADA-AJUSTE) un producto destino (lote nuevo del mismo producto raíz, o producto
     * nuevo independiente). Todo en una sola transacción: si algo falla, no queda nada aplicado.
     */
    @Transactional
    public Product applyStockTransformation(@NonNull final StockTransformationRequestDTO dto, @NonNull final String username) {
        final User user = userService.findByUsername(username);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }

        final List<StockTransformationRequestDTO.SourceItemDTO> sources = dto.getSources();
        if (sources == null || sources.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe indicar al menos un producto de origen");
        }

        // 1) Salidas: documentan y descuentan stock de cada producto origen
        for (StockTransformationRequestDTO.SourceItemDTO source : sources) {
            if (source == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hay un producto origen vacío");
            }

            final Long sourceProductId = source.getProductId();
            final Integer sourceQuantity = source.getQuantity();
            if (sourceProductId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID del producto origen es obligatorio");
            }
            if (sourceQuantity == null || sourceQuantity <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad del producto origen debe ser mayor a 0");
            }

            productService.validateUserOwnsProduct(sourceProductId, username);
            final Product sourceProduct = productService.findById(sourceProductId);
            if (sourceProduct.getStock() == null || sourceProduct.getStock() < sourceQuantity) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Stock insuficiente en " + sourceProduct.getName() + " (disponible: " + sourceProduct.getStock() + ")");
            }

            final TransactionDTO salida = new TransactionDTO();
            salida.setProductId(sourceProductId);
            salida.setType("SALIDA");
            salida.setReason("AJUSTE");
            salida.setQuantity(sourceQuantity);
            salida.setUserId(user.getId());
            salida.setDateTime(LocalDateTime.now());
            create(salida);
        }

        // 2) Producto destino: lote nuevo del mismo producto raíz, o producto nuevo independiente
        final StockTransformationRequestDTO.TargetDTO target = dto.getTarget();
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe indicar el producto destino");
        }

        final ProductDTO targetDto = new ProductDTO();
        final String targetMode = target.getMode();
        if (targetMode == null || targetMode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El modo del destino es obligatorio");
        }

        targetDto.setName(target.getName());
        targetDto.setDescription(target.getDescription());
        targetDto.setCost(target.getCost());
        targetDto.setPrice(target.getPrice());
        targetDto.setStock(0);

        final Product targetProduct;
        if ("LOTE".equalsIgnoreCase(targetMode)) {
            final Long parentProductId = target.getParentProductId();
            if (parentProductId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el producto raíz para crear el lote");
            }
            targetProduct = productService.createLote(parentProductId, targetDto, username);
        } else if ("PRODUCTO_NUEVO".equalsIgnoreCase(targetMode)) {
            final Long storeId = target.getStoreId();
            if (storeId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta la tienda para crear el producto nuevo");
            }
            targetDto.setStoreId(storeId);
            targetProduct = productService.create(targetDto, username);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modo de destino inválido: " + targetMode);
        }

        // 3) Entrada: documenta el ingreso de stock al producto destino
        final Integer targetQuantity = target.getQuantity();
        if (targetQuantity == null || targetQuantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad del producto destino debe ser mayor a 0");
        }

        final TransactionDTO entrada = new TransactionDTO();
        entrada.setProductId(targetProduct.getId());
        entrada.setType("ENTRADA");
        entrada.setReason("AJUSTE");
        entrada.setQuantity(targetQuantity);
        entrada.setUserId(user.getId());
        entrada.setDateTime(LocalDateTime.now());
        create(entrada);

        // El producto destino queda activo para venta de inmediato
        return productService.setActiveForSale(targetProduct.getId(), true, username);
    }
}