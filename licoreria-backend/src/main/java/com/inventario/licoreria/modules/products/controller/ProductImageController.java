package com.inventario.licoreria.modules.products.controller;

import com.inventario.licoreria.modules.products.dto.ProductImageDTO;
import com.inventario.licoreria.modules.products.service.ProductImageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/product-images")
public class ProductImageController {

    private final ProductImageService productImageService;

    @Value("${product.image.path:${user.home}/product-images}")
    private String imageBasePath;

    public ProductImageController(ProductImageService productImageService) {
        this.productImageService = productImageService;
    }

    /**
     * Sube una imagen para un producto
     * POST /api/product-images/{productId}
     */
    @PostMapping("/{productId}")
    public ResponseEntity<Map<String, Object>> uploadProductImage(
            @PathVariable Long productId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        try {
            ProductImageDTO imageDTO = productImageService.uploadProductImage(file, productId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Imagen comprimida a " + formatFileSize(imageDTO.getCompressedFileSize()));
            response.put("image", imageDTO);
            
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Error al procesar la imagen: " + e.getMessage());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Obtiene la imagen de un producto
     * GET /api/product-images/{productId}
     */
    @GetMapping("/{productId}")
    public ResponseEntity<ProductImageDTO> getProductImage(@PathVariable Long productId) {
        Optional<ProductImageDTO> image = productImageService.getProductImage(productId);
        return image.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Sirve la imagen física
     * GET /api/product-images/file/{productId}
     */
    @GetMapping("/file/{productId}")
    public ResponseEntity<Resource> getImageFile(@PathVariable Long productId) throws MalformedURLException {
        Optional<ProductImageDTO> image = productImageService.getProductImage(productId);
        
        if (image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagen no encontrada para el producto");
        }

        try {
            String imagePath = image.get().getImagePath();
            
            // Construir ruta segura usando el imagePath relativo
            Path filePath = Paths.get(imageBasePath, imagePath).toAbsolutePath().normalize();
            
            // Validar que la ruta está dentro de imageBasePath (seguridad)
            Path baseAbsPath = Paths.get(imageBasePath).toAbsolutePath().normalize();
            if (!filePath.startsWith(baseAbsPath)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso a archivo no permitido");
            }
            
            if (!Files.exists(filePath)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Archivo de imagen no encontrado en el sistema");
            }
            
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso de imagen no accesible");
            }

            String contentType = determineContentType(imagePath);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Error al servir la imagen: " + e.getMessage());
        }
    }

    /**
     * Elimina la imagen de un producto
     * DELETE /api/product-images/{productId}
     */
    @DeleteMapping("/{productId}")
    public ResponseEntity<Map<String, Object>> deleteProductImage(@PathVariable Long productId) {
        try {
            productImageService.deleteProductImage(productId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Imagen eliminada correctamente");
            
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Error al eliminar la imagen: " + e.getMessage());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Determina el tipo de contenido basado en la extensión del archivo
     */
    private String determineContentType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "image/jpeg"; // Por defecto JPEG
        }

        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> "image/jpeg"; // Por defecto JPEG para extensiones desconocidas
        };
    }

    /**
     * Formatea el tamaño del archivo a formato legible
     */
    private String formatFileSize(Long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
