package com.inventario.licoreria.modules.products.controller;

import com.inventario.licoreria.modules.products.model.Tag;
import com.inventario.licoreria.modules.products.service.TagService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tags")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:3000"})
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /**
     * Obtener todas las etiquetas de una tienda
     * GET /api/tags/store/{storeId}
     */
    @GetMapping("/store/{storeId}")
    public ResponseEntity<List<Tag>> getTagsByStore(@PathVariable Long storeId, Authentication authentication) {
        String username = authentication.getName();
        List<Tag> tags = tagService.getTagsByStore(storeId, username);
        return ResponseEntity.ok(tags);
    }

    /**
     * Crear una nueva etiqueta
     * POST /api/tags/store/{storeId}
     * Body: {"name": "bebida"}
     */
    @PostMapping("/store/{storeId}")
    public ResponseEntity<Tag> createTag(@PathVariable Long storeId, 
                                         @RequestBody Map<String, String> payload,
                                         Authentication authentication) {
        String username = authentication.getName();
        String name = payload.get("name");
        Tag tag = tagService.createTag(storeId, name, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(tag);
    }

    /**
     * Actualizar una etiqueta
     * PUT /api/tags/{tagId}
     * Body: {"name": "nuevo nombre"}
     */
    @PutMapping("/{tagId}")
    public ResponseEntity<Tag> updateTag(@PathVariable Long tagId,
                                         @RequestBody Map<String, String> payload,
                                         Authentication authentication) {
        String username = authentication.getName();
        String newName = payload.get("name");
        Tag tag = tagService.updateTag(tagId, newName, username);
        return ResponseEntity.ok(tag);
    }

    /**
     * Eliminar una etiqueta
     * DELETE /api/tags/{tagId}
     */
    @DeleteMapping("/{tagId}")
    public ResponseEntity<Void> deleteTag(@PathVariable Long tagId, Authentication authentication) {
        String username = authentication.getName();
        tagService.deleteTag(tagId, username);
        return ResponseEntity.noContent().build();
    }

    /**
     * Agregar una etiqueta a un producto
     * POST /api/tags/product/{productId}/tag/{tagId}
     */
    @PostMapping("/product/{productId}/tag/{tagId}")
    public ResponseEntity<String> addTagToProduct(@PathVariable Long productId,
                                                  @PathVariable Long tagId,
                                                  Authentication authentication) {
        String username = authentication.getName();
        tagService.addTagToProduct(productId, tagId, username);
        return ResponseEntity.status(HttpStatus.CREATED).body("Etiqueta agregada exitosamente");
    }

    /**
     * Remover una etiqueta de un producto
     * DELETE /api/tags/product/{productId}/tag/{tagId}
     */
    @DeleteMapping("/product/{productId}/tag/{tagId}")
    public ResponseEntity<Void> removeTagFromProduct(@PathVariable Long productId,
                                                    @PathVariable Long tagId,
                                                    Authentication authentication) {
        String username = authentication.getName();
        tagService.removeTagFromProduct(productId, tagId, username);
        return ResponseEntity.noContent().build();
    }

    /**
     * Obtener etiquetas de un producto
     * GET /api/tags/product/{productId}
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<Tag>> getTagsByProduct(@PathVariable Long productId) {
        List<Tag> tags = tagService.getTagsByProduct(productId);
        return ResponseEntity.ok(tags);
    }
}
