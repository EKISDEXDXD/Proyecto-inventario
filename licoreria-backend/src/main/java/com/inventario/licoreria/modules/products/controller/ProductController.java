package com.inventario.licoreria.modules.products.controller;

import com.inventario.licoreria.modules.products.dto.AdjustStockDTO;
import com.inventario.licoreria.modules.products.dto.ProductDTO;
import com.inventario.licoreria.modules.products.dto.ProductAlertDTO;
import com.inventario.licoreria.modules.products.model.Product;
import com.inventario.licoreria.modules.products.model.ProductAlert;
import com.inventario.licoreria.modules.products.service.ProductService;
import com.inventario.licoreria.modules.products.service.ProductAlertService;
import com.inventario.licoreria.modules.inventory.service.TransactionService;
import com.inventario.licoreria.modules.inventory.dto.TransactionDTO;
import com.inventario.licoreria.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.lang.NonNull;

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

    @GetMapping
    public List<Product> getAll(Authentication authentication) {
        return productService.findAllByUsername(authentication.getName());
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
        try {
            validateNotExternal(authHeader);
            System.out.println("=== getByStore START - storeId: " + storeId + ", user: " + authentication.getName());
            List<Product> products = productService.findByStoreId(storeId, authentication.getName());
            System.out.println("=== getByStore SUCCESS - productos encontrados: " + (products != null ? products.size() : 0));
            return products;
        } catch (Exception e) {
            System.err.println("=== getByStore ERROR ===");
            System.err.println("Error type: " + e.getClass().getName());
            System.err.println("Error message: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @PostMapping
    public Product create(@Valid @RequestBody ProductDTO dto, Authentication authentication, 
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        validateNotExternal(authHeader);
        return productService.create(dto, authentication.getName());
    }

    @PutMapping("/{id}")
    public Product update(@PathVariable @NonNull Long id, @Valid @RequestBody ProductDTO dto, Authentication authentication,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        validateNotExternal(authHeader);
        return productService.update(id, dto, authentication.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long id, Authentication authentication,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        validateNotExternal(authHeader);
        productService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/adjust-stock")
    public Product adjustStock(
        @PathVariable @NonNull Long id, 
        @Valid @RequestBody AdjustStockDTO request,
        Authentication authentication,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validateNotExternal(authHeader);
        // Ajustar el stock con validación de permisos
        Product updated = productService.adjustStock(id, request.getDelta(), authentication.getName());
        
        // Registrar la transacción automáticamente
        try {
            Long userId = request.getUserId() != null ? request.getUserId() : 1L; // Usar ID 1 como usuario por defecto
            
            TransactionDTO transactionDTO = new TransactionDTO();
            transactionDTO.setProductId(id);
            transactionDTO.setType(request.getDelta() > 0 ? "ENTRADA" : "SALIDA");
            transactionDTO.setQuantity(Math.abs(request.getDelta()));
            transactionDTO.setUserId(userId);
            transactionDTO.setDateTime(LocalDateTime.now());
            
            transactionService.create(transactionDTO);
        } catch (Exception e) {
            // Log the error pero no fallar la solicitud de ajuste de stock
            System.err.println("Error registrando transacción: " + e.getMessage());
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
        return productAlertService.saveOrUpdate(id, dto, authentication.getName());
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
        productService.validateUserOwnsProduct(id, authentication.getName());
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
}