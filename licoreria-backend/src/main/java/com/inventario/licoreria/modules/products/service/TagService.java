package com.inventario.licoreria.modules.products.service;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.inventario.licoreria.modules.dashboard.service.DashboardSummaryService;
import com.inventario.licoreria.modules.products.model.Product;
import com.inventario.licoreria.modules.products.model.ProductTag;
import com.inventario.licoreria.modules.products.model.Tag;
import com.inventario.licoreria.modules.products.repository.ProductTagRepository;
import com.inventario.licoreria.modules.products.repository.TagRepository;
import com.inventario.licoreria.modules.store.model.Store;
import com.inventario.licoreria.modules.store.service.StoreService;

@Service
public class TagService {

    private final TagRepository tagRepository;
    private final ProductTagRepository productTagRepository;
    private final StoreService storeService;
    private final ProductService productService;
    private final DashboardSummaryService dashboardSummaryService;

    public TagService(TagRepository tagRepository, ProductTagRepository productTagRepository, 
                      StoreService storeService, ProductService productService,
                      DashboardSummaryService dashboardSummaryService) {
        this.tagRepository = tagRepository;
        this.productTagRepository = productTagRepository;
        this.storeService = storeService;
        this.productService = productService;
        this.dashboardSummaryService = dashboardSummaryService;
    }

    private void validateUserOwnsStore(@NonNull Long storeId, @NonNull String username) {
        Store store = storeService.findStoreEntity(storeId);
        if (store == null || store.getManager() == null || !store.getManager().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "No tienes permiso para acceder a las etiquetas de esta tienda");
        }
    }

    /**
     * Obtener todas las etiquetas de una tienda
     */
    public List<Tag> getTagsByStore(@NonNull Long storeId, @NonNull String username) {
        validateUserOwnsStore(storeId, username);
        Store store = storeService.findStoreEntity(storeId);
        return tagRepository.findByStore(store);
    }

    /**
     * Crear una nueva etiqueta
     */
    public Tag createTag(@NonNull Long storeId, @NonNull String name, @NonNull String username) {
        validateUserOwnsStore(storeId, username);
        
        Store store = storeService.findStoreEntity(storeId);
        
        // Validar que el nombre no esté vacío
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre de la etiqueta no puede estar vacío");
        }
        
        // Validar que no exista una etiqueta con el mismo nombre en esta tienda
        Optional<Tag> existingTag = tagRepository.findByNameAndStore(name.trim(), store);
        if (existingTag.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una etiqueta con este nombre en tu tienda");
        }
        
        Tag tag = new Tag(name.trim(), store);
        return tagRepository.save(tag);
    }

    /**
     * Actualizar nombre de una etiqueta
     */
    public Tag updateTag(@NonNull Long tagId, @NonNull String newName, @NonNull String username) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etiqueta no encontrada"));
        
        validateUserOwnsStore(tag.getStore().getId(), username);
        
        if (newName == null || newName.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre de la etiqueta no puede estar vacío");
        }
        
        tag.setName(newName.trim());
        Tag saved = tagRepository.save(tag);
        dashboardSummaryService.markStoreDirty(saved.getStore().getId());
        return saved;
    }

    /**
     * Eliminar una etiqueta (y sus asociaciones con productos)
     */
    public void deleteTag(@NonNull Long tagId, @NonNull String username) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etiqueta no encontrada"));
        
        validateUserOwnsStore(tag.getStore().getId(), username);
        tagRepository.delete(tag);
        dashboardSummaryService.markStoreDirty(tag.getStore().getId());
    }

    /**
     * Agregar una etiqueta a un producto
     */
    public ProductTag addTagToProduct(@NonNull Long productId, @NonNull Long tagId, @NonNull String username) {
        // Validar que el usuario es propietario del producto
        productService.validateUserOwnsProduct(productId, username);
        
        Product product = productService.findById(productId);
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etiqueta no encontrada"));
        
        // Validar que la etiqueta pertenece a la misma tienda del producto
        if (!tag.getStore().getId().equals(product.getStore().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "La etiqueta no pertenece a tu tienda");
        }
        
        // Validar que la etiqueta no esté ya asociada al producto
        Optional<ProductTag> existingProductTag = productTagRepository.findByProductAndTag(product, tag);
        if (existingProductTag.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                "Este producto ya tiene esta etiqueta");
        }
        
        ProductTag productTag = new ProductTag(product, tag);
        ProductTag saved = productTagRepository.save(productTag);
        dashboardSummaryService.markStoreDirty(product.getStore().getId());
        return saved;
    }

    /**
     * Remover una etiqueta de un producto
     */
    public void removeTagFromProduct(@NonNull Long productId, @NonNull Long tagId, @NonNull String username) {
        productService.validateUserOwnsProduct(productId, username);
        
        Product product = productService.findById(productId);
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etiqueta no encontrada"));
        
        // Remover la etiqueta de la lista de tags del producto
        if (product.getTags() != null) {
            product.getTags().removeIf(pt -> pt.getTag().getId().equals(tagId));
            // Guardar el producto (esto eliminará el ProductTag por orphanRemoval)
            productService.save(product);
            dashboardSummaryService.markStoreDirty(product.getStore().getId());
        }
    }

    /**
     * Obtener etiquetas de un producto
     */
    public List<Tag> getTagsByProduct(@NonNull Long productId) {
        Product product = productService.findById(productId);
        List<ProductTag> productTags = productTagRepository.findByProduct(product);
        return productTags.stream().map(ProductTag::getTag).toList();
    }
}
