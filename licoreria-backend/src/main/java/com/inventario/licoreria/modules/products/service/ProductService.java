package com.inventario.licoreria.modules.products.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.inventario.licoreria.modules.dashboard.service.DashboardSummaryService;
import com.inventario.licoreria.modules.inventory.repository.TransactionRepository;
import com.inventario.licoreria.modules.products.dto.ProductDTO;
import com.inventario.licoreria.modules.products.model.Product;
import com.inventario.licoreria.modules.products.repository.ProductRepository;
import com.inventario.licoreria.modules.store.model.Store;
import com.inventario.licoreria.modules.store.service.StoreService;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final StoreService storeService;
    private final TransactionRepository transactionRepository;
    private final DashboardSummaryService dashboardSummaryService;

    public ProductService(ProductRepository productRepository, StoreService storeService, TransactionRepository transactionRepository, DashboardSummaryService dashboardSummaryService) {
        this.productRepository = productRepository;
        this.storeService = storeService;
        this.transactionRepository = transactionRepository;
        this.dashboardSummaryService = dashboardSummaryService;
    }

    private void validateUserOwnsStore(@NonNull Long storeId, @NonNull String username) {
        Store store = storeService.findStoreEntity(storeId);
        if (store == null || store.getManager() == null || !store.getManager().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "No tienes permiso para acceder a los productos de esta tienda");
        }
    }

    public void validateUserOwnsProduct(@NonNull Long productId, @NonNull String username) {
        Product product = findById(productId);
        if (product.getStore() == null || product.getStore().getManager() == null || 
            !product.getStore().getManager().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "No tienes permiso para modificar este producto");
        }
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public List<Product> findAllByUsername(@NonNull String username) {
        return productRepository.findAll().stream()
                .filter(product -> product.getStore().getManager().getUsername().equals(username))
                .toList();
    }

    public Product create(final ProductDTO dto, @NonNull String username) {
        validateUserOwnsStore(dto.getStoreId(), username);
        final Product product = new Product();
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setCost(dto.getCost());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setInitialStock(dto.getStock());
        product.setIsActive(true);  // Los productos nuevos siempre se crean activos
        product.setStore(storeService.findStoreEntity(dto.getStoreId()));
        Product saved = productRepository.save(product);
        dashboardSummaryService.markStoreDirty(saved.getStore().getId());
        return saved;
    }

    @org.springframework.lang.NonNull
    @SuppressWarnings("null")
    public Product findById(@NonNull Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
    }

    public Product update(@NonNull final Long id, final ProductDTO dto, @NonNull String username) {
        validateUserOwnsProduct(id, username);
        final Product product = findById(id);
        
        // Validar que el producto no tenga transacciones registradas
        Long transactionCount = transactionRepository.countByProductId(id);
        if (transactionCount > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "No se puede editar este producto porque tiene movimientos registrados. Elimina los movimientos primero para poder editarlo.");
        }
        
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setCost(dto.getCost());
        product.setPrice(dto.getPrice());
        product.setStock(0);
        product.setInitialStock(0);
        return productRepository.save(product);
    }

    public Product delete(@NonNull final Long id, String username) {
        final Product product = findById(id);
        System.out.println("\n🔧 DELETE - Producto ID: " + id + ", Nombre: " + product.getName() + ", Parent: " + product.getParentId());
        
        // Verificar que el usuario autenticado es el manager de la tienda
        if (product.getStore() == null || product.getStore().getManager() == null || 
            !product.getStore().getManager().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes eliminar un producto que no es de tu tienda");
        }
        
        // Si es un producto principal (parent_id IS NULL)
        if (product.getParentId() == null) {
            System.out.println("  → Es PRODUCTO PRINCIPAL");
            // Verificar que no tenga lotes activos
            Long loteCount = productRepository.countByParentIdAndIsActiveTrue(id);
            System.out.println("  → Lotes activos: " + loteCount);
            if (loteCount > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "No se puede eliminar este producto porque tiene " + loteCount + " lote(s) asociado(s). Elimina todos los lotes primero.");
            }
            
            // Si tiene transacciones, deshabilitar en lugar de borrar
            Long transactionCount = transactionRepository.countByProductId(id);
            System.out.println("  → Transacciones: " + transactionCount);
            if (transactionCount > 0) {
                System.out.println("  → Desactivando producto (tiene transacciones)");
                product.setIsActive(false);
                Product saved = productRepository.save(product);
                dashboardSummaryService.markStoreDirty(saved.getStore().getId());
                return saved;
            }
        } else {
            System.out.println("  → Es LOTE. Parent ID: " + product.getParentId());
            // Si es un LOTE (parent_id != NULL), SIEMPRE desactivar sin borrar
            System.out.println("  → Estrategia: desactivar siempre (es un LOTE)");
            product.setIsActive(false);
            Product saved = productRepository.save(product);
            System.out.println("  ✅ Lote desactivado con isActive: " + saved.getIsActive());
            dashboardSummaryService.markStoreDirty(saved.getStore().getId());
            return saved;
        }
        
        // Solo borrar si no tiene transacciones
        System.out.println("  → Borrando producto (no tiene transacciones)");
        product.setIsActive(false);  // Marcar como desactivado por si acaso
        productRepository.delete(product);
        System.out.println("  ✅ Producto borrado");
        dashboardSummaryService.markStoreDirty(product.getStore().getId());
        return product;
    }

    public Product save(@NonNull Product product) {
        return productRepository.save(product);
    }

    public Product adjustStock(@NonNull final Long id, final int stockDelta, @NonNull String username) {
        validateUserOwnsProduct(id, username);
        final Product product = findById(id);
        final int nuevoStock = product.getStock() + stockDelta;
        if (nuevoStock < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Stock insuficiente para el producto: " + product.getName());
        }
        product.setStock(nuevoStock);
        return productRepository.save(product);
    }

    public Product adjustStock(@NonNull final Long id, final int stockDelta) {
        final Product product = findById(id);
        final int nuevoStock = product.getStock() + stockDelta;
        if (nuevoStock < 0) {
            throw new RuntimeException("Stock insuficiente para el producto: " + product.getName());
        }
        product.setStock(nuevoStock);
        return productRepository.save(product);
    }

    public List<Product> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return findAll();
        }
        return productRepository.searchByName(query.trim());
    }

    public List<Product> getSuggestions(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        return productRepository.searchSuggestions(query.trim());
    }

    public List<Product> findByStoreId(@NonNull Long storeId, @NonNull String username) {
        // Permitir que cualquier usuario autenticado vea los productos de cualquier tienda (acceso en lectura)
        return productRepository.findByStoreId(storeId).stream()
            .filter(p -> p.getIsActive() == null || p.getIsActive())
            .toList();
    }

    public List<Product> findByStoreId(@NonNull Long storeId) {
        return productRepository.findByStoreId(storeId).stream()
            .filter(p -> p.getIsActive() == null || p.getIsActive())
            .toList();
    }

    /**
     * Buscar productos con paginación y filtro opcional de etiquetas
     * Si tagIds está vacío, busca solo por nombre
     * Si tagIds tiene valores, busca productos que tengan TODAS las etiquetas
     */
    public Page<Product> searchWithPagination(@NonNull Long storeId, String searchQuery, 
                                              List<Long> tagIds, Pageable pageable) {
        String query = searchQuery != null ? searchQuery.trim() : "";
        List<Long> tags = (tagIds != null && !tagIds.isEmpty()) ? tagIds : List.of();
        
        if (tags.isEmpty()) {
            return productRepository.searchByNameAndStore(query, storeId, pageable);
        } else {
            return productRepository.searchByNameAndTags(query, storeId, tags, pageable);
        }
    }

    /**
     * Verificar si un producto puede ser editado (no tiene transacciones registradas)
     */
    public boolean canEditProduct(@NonNull Long productId) {
        Long transactionCount = transactionRepository.countByProductId(productId);
        return transactionCount == 0;
    }

    /**
     * Obtener el lote activo de un producto principal
     */
    public Product getActiveLote(@NonNull Long parentId) {
        return productRepository.findByParentIdAndIsActiveTrue(parentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    "No hay lote activo para este producto"));
    }

    /**
     * Obtener todos los lotes de un producto principal
     */
    public List<Product> getLotesByProduct(@NonNull Long parentId) {
        return productRepository.findByParentIdOrderByOrderIndex(parentId);
    }

    /**
     * Establecer un lote como activo (desactiva los demás)
     */
    public Product setActiveLote(@NonNull Long loteId, @NonNull String username) {
        final Product lote = findById(loteId);
        
        // Validar que el usuario es propietario
        validateUserOwnsProduct(loteId, username);
        
        // Si no es un lote (parent_id es null), no permitir
        if (lote.getParentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Este producto no es un lote");
        }
        
        // Desactivar todos los lotes del producto padre
        Long parentId = lote.getParentId();
        List<Product> allLotes = getLotesByProduct(parentId);
        for (Product p : allLotes) {
            if (!p.getId().equals(loteId)) {
                p.setIsActive(false);
                productRepository.save(p);
            }
        }
        
        // Activar este lote
        lote.setIsActive(true);
        return productRepository.save(lote);
    }

    /**
     * Establecer un producto/lote como activo para venta (desactiva los demás del mismo padre)
     */
    public Product setActiveForSale(@NonNull Long id, @NonNull Boolean active, @NonNull String username) {
        final Product product = findById(id);
        
        // Validar que el usuario es propietario
        validateUserOwnsProduct(id, username);
        
        if (active) {
            // Si es un lote, desactivar otros lotes del mismo padre Y desactivar el padre
            if (product.getParentId() != null) {
                Long parentId = product.getParentId();
                
                // Desactivar el padre
                Product parent = findById(parentId);
                parent.setIsActiveForSale(false);
                productRepository.save(parent);
                
                // Desactivar todos los lotes hermanos
                List<Product> allLotes = getLotesByProduct(parentId);
                for (Product p : allLotes) {
                    if (!p.getId().equals(id)) {
                        p.setIsActiveForSale(false);
                        productRepository.save(p);
                    }
                }
            } else {
                // Si es un padre, desactivar todos sus lotes (pero NO otros padres)
                List<Product> allLotes = getLotesByProduct(id);
                for (Product p : allLotes) {
                    p.setIsActiveForSale(false);
                    productRepository.save(p);
                }
            }
        } else {
            // DESACTIVAR: Validar que no quede todo desactivado
            // Si es un lote que intenta desactivarse
            if (product.getParentId() != null) {
                Long parentId = product.getParentId();
                List<Product> allLotes = getLotesByProduct(parentId);
                
                // Contar cuántos lotes estarían activos después de desactivar este
                long activeLotsAfter = allLotes.stream()
                    .filter(l -> !l.getId().equals(id) && l.getIsActiveForSale())
                    .count();
                
                // Si este es el único lote activo, activar el padre en su lugar
                if (activeLotsAfter == 0) {
                    Product parent = findById(parentId);
                    parent.setIsActiveForSale(true);
                    productRepository.save(parent);
                    System.out.println("✅ Auto-activando padre ID: " + parentId);
                }
            } else {
                // Si es el padre que intenta desactivarse
                List<Product> allLotes = getLotesByProduct(id);
                
                // Si hay lotes, activar el primero (que es activo=true)
                Product firstActiveLote = allLotes.stream()
                    .filter(l -> l.getIsActive())
                    .findFirst()
                    .orElse(null);
                
                if (firstActiveLote != null) {
                    firstActiveLote.setIsActiveForSale(true);
                    productRepository.save(firstActiveLote);
                    System.out.println("✅ Auto-activando lote ID: " + firstActiveLote.getId());
                }
            }
        }
        
        // Establecer el estado del producto actual
        product.setIsActiveForSale(active);
        Product saved = productRepository.save(product);
        dashboardSummaryService.markStoreDirty(saved.getStore().getId());
        return saved;
}

    public Integer getTotalStockForProduct(@NonNull Long parentId) {
        List<Product> lotes = getLotesByProduct(parentId);
        return lotes.stream()
                .map(Product::getStock)
                .reduce(0, Integer::sum);
    }

    /**
     * Crear un nuevo lote para un producto
     */
    public Product createLote(@NonNull Long parentId, final ProductDTO dto, @NonNull String username) {
        final Product parentProduct = findById(parentId);
        
        // Validar que el usuario es propietario del producto principal
        validateUserOwnsProduct(parentId, username);
        
        // Validar que el producto padre no es un lote
        if (parentProduct.getParentId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "No se puede crear un lote de un lote");
        }
        
        // Crear el nuevo lote
        final Product lote = new Product();
        List<Product> existingLotes = getLotesByProduct(parentId);
        String loteName = dto.getName() != null ? dto.getName().trim() : "";
        if (loteName.isBlank()) {
            loteName = parentProduct.getName() + " - Lote " + (existingLotes.size() + 1);
        }
        lote.setName(loteName);
        lote.setDescription(dto.getDescription());
        lote.setCost(dto.getCost());
        lote.setPrice(dto.getPrice());
        lote.setStock(0);
        lote.setInitialStock(0);
        lote.setStore(parentProduct.getStore());
        lote.setParentId(parentId);
        lote.setIsActive(true);  // Nuevo lote activo y visible inmediatamente
        
        // Calcular el order_index
        Integer maxOrderIndex = existingLotes.stream()
                .map(Product::getOrderIndex)
                .max(Integer::compareTo)
                .orElse(0);
        lote.setOrderIndex(maxOrderIndex + 1);
        
        Product saved = productRepository.save(lote);
        dashboardSummaryService.markStoreDirty(saved.getStore().getId());
        return saved;
    }

}
