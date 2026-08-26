package com.inventario.licoreria.modules.products.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.inventario.licoreria.modules.inventory.dto.TransactionDTO;
import com.inventario.licoreria.modules.inventory.service.TransactionService;
import com.inventario.licoreria.modules.products.dto.AdjustStockDTO;
import com.inventario.licoreria.modules.products.dto.ProductAlertDTO;
import com.inventario.licoreria.modules.products.dto.ProductDTO;
import com.inventario.licoreria.modules.products.dto.StockTransformationRequestDTO;
import com.inventario.licoreria.modules.products.model.Product;
import com.inventario.licoreria.modules.products.model.ProductAlert;
import com.inventario.licoreria.modules.products.service.ProductAlertService;
import com.inventario.licoreria.modules.products.service.ProductService;
import com.inventario.licoreria.security.JwtUtil;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final ProductAlertService productAlertService;
    private final TransactionService transactionService;
    private final JwtUtil jwtUtil;

    public ProductController(ProductService productService, ProductAlertService productAlertService, TransactionService transactionService, JwtUtil jwtUtil) {
        this.productService = productService;
        this.productAlertService = productAlertService;
        this.transactionService = transactionService;
        this.jwtUtil = jwtUtil;
    }

    private void validateNotExternal(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.isExternalAccess(token)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                    "Los usuarios externos no tienen acceso a inventario. Solo pueden acceder a movimientos.");
            }
        }
    }

    @NonNull
    private String getUsername(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }
        return username;
    }

    @GetMapping
    public List<Product> getAll(Authentication authentication) {
        return productService.findAllByUsername(getUsername(authentication));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable @NonNull Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @GetMapping("/search")
    public List<Product> search(@RequestParam String query) {
        return productService.search(query);
    }

    @GetMapping("/search/suggestions")
    public List<Product> getSuggestions(@RequestParam String query) {
        return productService.getSuggestions(query);
    }

    @GetMapping("/store/external/{storeId}")
    public List<Product> getByStoreExternal(@PathVariable @NonNull Long storeId) {
        return productService.findByStoreId(storeId);
    }

    @GetMapping("/store/{storeId}")
    public List<Product> getByStore(
            @PathVariable @NonNull Long storeId, 
            Authentication authentication,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        validateNotExternal(authHeader);
        String username = getUsername(authentication);
        return productService.findByStoreId(storeId, username);
    }

    @PostMapping
    public Product create(@Valid @RequestBody ProductDTO dto, Authentication authentication, 
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        validateNotExternal(authHeader);
        return productService.create(dto, getUsername(authentication));
    }

    @PutMapping("/{id}")
    public Product update(@PathVariable @NonNull Long id, @Valid @RequestBody ProductDTO dto, Authentication authentication,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        validateNotExternal(authHeader);
        return productService.update(id, dto, getUsername(authentication));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Product> delete(@PathVariable @NonNull Long id, Authentication authentication,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        validateNotExternal(authHeader);
        Product result = productService.delete(id, getUsername(authentication));
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/adjust-stock")
    public Product adjustStock(
        @PathVariable @NonNull Long id, 
        @Valid @RequestBody AdjustStockDTO request,
        Authentication authentication,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validateNotExternal(authHeader);

        final Integer deltaObj = request.getDelta();
        if (deltaObj == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El delta de stock es obligatorio");
        }
        final int delta = deltaObj;

        // Ajustar el stock con validación de permisos
        Product updated = productService.adjustStock(id, delta, getUsername(authentication));
        
        // Registrar la transacción automáticamente
        try {
            Long userId = request.getUserId();
            if (userId == null) {
                userId = 1L;
            }
            final String transactionType = delta > 0 ? "ENTRADA" : "SALIDA";
            
            TransactionDTO transactionDTO = new TransactionDTO();
            transactionDTO.setProductId(id);
            transactionDTO.setType(transactionType);
            transactionDTO.setQuantity(Math.abs(delta));
            transactionDTO.setUserId(userId);
            transactionDTO.setDateTime(LocalDateTime.now());
            
            transactionService.create(transactionDTO);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error registrando la transacción del ajuste de stock: " + e.getMessage(), e);
        }
        
        return updated;
    }

    @PutMapping("/{id}/alert")
    public ProductAlert saveOrUpdateAlert(
        @PathVariable @NonNull Long id,
        @Valid @RequestBody ProductAlertDTO dto,
        Authentication authentication,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validateNotExternal(authHeader);
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ProductAlertDTO no puede ser nulo");
        }
        return productAlertService.saveOrUpdate(id, dto, getUsername(authentication));
    }

    @GetMapping("/{id}/alert")
    public ResponseEntity<ProductAlert> getAlert(
        @PathVariable @NonNull Long id,
        Authentication authentication,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validateNotExternal(authHeader);
        return productAlertService.findByProductId(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/alert")
    public ResponseEntity<Void> deleteAlert(
        @PathVariable @NonNull Long id,
        Authentication authentication,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validateNotExternal(authHeader);
        productService.validateUserOwnsProduct(id, getUsername(authentication));
        productAlertService.deleteByProductId(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Búsqueda con paginación y filtro de etiquetas para la galería visual
     * GET /api/products/gallery/search?storeId=1&search=coca&page=0&size=20&tags=1,2
     */
    @GetMapping("/gallery/search")
    public ResponseEntity<Page<Product>> searchGallery(
        @RequestParam @NonNull Long storeId,
        @RequestParam(defaultValue = "") String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) List<Long> tags,
        Authentication authentication,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validateNotExternal(authHeader);
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> result = productService.searchWithPagination(storeId, search, tags, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * Verificar si un producto puede ser editado (no tiene transacciones)
     * GET /api/products/{id}/can-edit
     */
    @GetMapping("/{id}/can-edit")
    public ResponseEntity<java.util.Map<String, Object>> canEditProduct(
        @PathVariable @NonNull Long id,
        Authentication authentication,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validateNotExternal(authHeader);
        productService.validateUserOwnsProduct(id, getUsername(authentication));
        
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        boolean canEdit = productService.canEditProduct(id);
        
        response.put("canEdit", canEdit);
        response.put("hasTransactions", !canEdit);
        
        if (canEdit) {
            response.put("message", "El producto puede ser editado");
            return ResponseEntity.ok(response);
        } else {
            response.put("message", "No se puede editar este producto porque tiene movimientos registrados");
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Obtener todos los lotes de un producto principal
     * GET /api/products/{parentId}/lotes
     */
    @GetMapping("/{parentId}/lotes")
    public ResponseEntity<List<Product>> getLotes(
        @PathVariable @NonNull Long parentId,
        Authentication authentication,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validateNotExternal(authHeader);
        productService.validateUserOwnsProduct(parentId, getUsername(authentication));
        
        List<Product> lotes = productService.getLotesByProduct(parentId);
        return ResponseEntity.ok(lotes);
    }

    /**
     * Obtener el lote activo de un producto principal
     * GET /api/products/{parentId}/active-lote
     */
    @GetMapping("/{parentId}/active-lote")
    public ResponseEntity<Product> getActiveLote(
        @PathVariable @NonNull Long parentId,
        Authentication authentication,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validateNotExternal(authHeader);
        productService.validateUserOwnsProduct(parentId, getUsername(authentication));
        
        Product activeLote = productService.getActiveLote(parentId);
        return ResponseEntity.ok(activeLote);
    }

    /**
     * Crear un nuevo lote para un producto principal
     * POST /api/products/{parentId}/create-lote
     */
    @PostMapping("/{parentId}/create-lote")
    public ResponseEntity<Product> createLote(
        @PathVariable @NonNull Long parentId,
        @Valid @RequestBody ProductDTO dto,
        Authentication authentication,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validateNotExternal(authHeader);
        
        Product newLote = productService.createLote(parentId, dto, getUsername(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(newLote);
    }

    /**
     * Establecer un lote como activo
     * PUT /api/products/{loteId}/activate
     */
    @PutMapping("/{loteId}/activate")
    public ResponseEntity<Product> activateLote(
        @PathVariable @NonNull Long loteId,
        Authentication authentication,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validateNotExternal(authHeader);
        
        Product activeLote = productService.setActiveLote(loteId, getUsername(authentication));
        return ResponseEntity.ok(activeLote);
    }

    /**
     * Cambiar isActiveForSale de un producto/lote
     * PUT /api/products/{id}/active-for-sale?active=true
     * Si se activa, desactiva automáticamente los hermanos (solo uno puede estar activo para venta)
     */
    @PutMapping("/{id}/active-for-sale")
    public ResponseEntity<Product> setActiveForSale(
        @PathVariable @NonNull Long id,
        @RequestParam @NonNull Boolean active,
        Authentication authentication,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validateNotExternal(authHeader);
        
        Product product = productService.setActiveForSale(id, active, getUsername(authentication));
        return ResponseEntity.ok(product);
    }

    /**
     * Ajuste de precio / promo: N salidas (AJUSTE) de productos origen -> 1 entrada (AJUSTE)
     * de un producto destino (lote nuevo del mismo producto raíz, o producto nuevo independiente).
     * POST /api/products/transformations
     */
    @PostMapping("/transformations")
    public ResponseEntity<Product> applyTransformation(
        @NonNull @Valid @RequestBody StockTransformationRequestDTO dto,
        Authentication authentication,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validateNotExternal(authHeader);
        Product result = transactionService.applyStockTransformation(dto, getUsername(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}