package com.inventario.licoreria.modules.products.service;

import com.inventario.licoreria.modules.products.dto.ProductImageDTO;
import com.inventario.licoreria.modules.products.model.Product;
import com.inventario.licoreria.modules.products.model.ProductImage;
import com.inventario.licoreria.modules.products.repository.ProductImageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Service
public class ProductImageService {

    private static final Logger logger = LoggerFactory.getLogger(ProductImageService.class);

    @Value("${product.image.path:${user.home}/product-images}")
    private String imageBasePath;

    private final ProductImageRepository productImageRepository;
    private final ProductService productService;

    // Configuración de límites
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB
    private static final int QUALITY = 85; // Calidad JPEG para compresión
    private static final String[] ALLOWED_EXTENSIONS = {"jpg", "jpeg", "png", "webp"};

    public ProductImageService(ProductImageRepository productImageRepository, ProductService productService) {
        this.productImageRepository = productImageRepository;
        this.productService = productService;
    }

    /**
     * Sube una imagen para un producto
     * @param file Archivo de imagen
     * @param productId ID del producto
     * @return ProductImageDTO con información de la imagen guardada
     * @throws IOException si hay error al procesar la imagen
     */
    public ProductImageDTO uploadProductImage(MultipartFile file, Long productId) throws IOException {
        try {
            logger.info("=== Iniciando uploadProductImage ===");
            logger.info("ProductId: {}, Archivo: {}, Tamaño: {}", productId, file.getOriginalFilename(), file.getSize());

            // Validar producto existe
            Product product = productService.findById(productId);
            if (product == null) {
                throw new RuntimeException("Producto no encontrado con ID: " + productId);
            }

            // Validar archivo
            validateFile(file);
            logger.info("Validación de archivo pasada");

            // Crear directorio si no existe
            ensureDirectoryExists();
            logger.info("Directorio verificado: {}", imageBasePath);

            // Procesar y comprimir imagen
            logger.info("Intentando leer imagen...");
            BufferedImage originalImage = ImageIO.read(file.getInputStream());
            if (originalImage == null) {
                throw new RuntimeException("No se pudo leer la imagen. Asegúrate que sea una imagen válida (ImageIO retornó null)");
            }
            logger.info("Imagen leída exitosamente: {}x{}", originalImage.getWidth(), originalImage.getHeight());

            // Generar nombre único para el archivo
            String fileName = generateFileName(file.getOriginalFilename());
            Path imagePath = Paths.get(imageBasePath, fileName);
            logger.info("Guardando en: {}", imagePath.toAbsolutePath());

            // Guardar imagen comprimida
            long compressedSize = saveCompressedImage(originalImage, imagePath, fileName);
            logger.info("Imagen comprimida guardada: {} bytes", compressedSize);

            // Eliminar imagen anterior si existe (garantizar una sola imagen por producto)
            // IMPORTANTE: Usar deleteByProductId() que es @Transactional para garantizar commit
            List<ProductImage> existingImages = productImageRepository.findAllByProductId(productId);
            if (!existingImages.isEmpty()) {
                logger.warn("ALERTA: Se encontraron {} imágenes existentes para product_id={}. Eliminando duplicados...", 
                    existingImages.size(), productId);
                
                // Eliminar archivos físicos primero
                for (ProductImage oldImage : existingImages) {
                    try {
                        deleteImageFile(oldImage.getImagePath());
                        logger.info("Archivo físico eliminado: path={}", oldImage.getImagePath());
                    } catch (Exception e) {
                        logger.error("Error al eliminar archivo físico: {} - {}", oldImage.getImagePath(), e.getMessage());
                    }
                }
                
                // Luego eliminar todos los registros de BD de una vez (con @Transactional)
                try {
                    productImageRepository.deleteByProductId(productId);
                    logger.info("Registros de BD eliminados para product_id={}", productId);
                } catch (Exception e) {
                    logger.error("Error crítico al eliminar registros de BD para product_id={}: {}", productId, e.getMessage(), e);
                    throw new RuntimeException("No se pudo eliminar imagen anterior: " + e.getMessage(), e);
                }
            }

            // Guardar en BD - usar solo el nombre del archivo como ruta relativa
            ProductImage productImage = new ProductImage(
                    product,
                    fileName,  // Solo guardar nombre del archivo, no ruta absoluta
                    file.getOriginalFilename(),
                    file.getSize(),
                    compressedSize
            );
            productImage.setCreatedAt(LocalDateTime.now());
            productImage.setUpdatedAt(LocalDateTime.now());

            ProductImage saved = productImageRepository.save(productImage);
            logger.info("=== uploadProductImage completado exitosamente. Image ID: {} ===", saved.getId());

            return convertToDTO(saved);
        } catch (IOException e) {
            logger.error("ERROR IOException al subir imagen: {}", e.getMessage(), e);
            throw new IOException("Error al procesar la imagen: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("ERROR Exception al subir imagen: {}", e.getMessage(), e);
            throw new IOException("Error inesperado al procesar la imagen: " + (e.getMessage() != null ? e.getMessage() : "Desconocido"), e);
        }
    }

    /**
     * Obtiene la imagen de un producto
     */
    public Optional<ProductImageDTO> getProductImage(Long productId) {
        return productImageRepository.findByProductId(productId)
                .map(this::convertToDTO);
    }

    /**
     * Elimina la imagen de un producto
     */
    public void deleteProductImage(Long productId) throws IOException {
        Optional<ProductImage> image = productImageRepository.findByProductId(productId);
        if (image.isPresent()) {
            deleteImageFile(image.get().getImagePath());
            productImageRepository.delete(image.get());
        }
    }

    /**
     * Valida el archivo subido
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("El archivo está vacío");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("El archivo excede el tamaño máximo de 2MB");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new RuntimeException("El nombre del archivo no es válido");
        }

        String extension = getFileExtension(fileName).toLowerCase();
        boolean isAllowed = false;
        for (String allowed : ALLOWED_EXTENSIONS) {
            if (allowed.equals(extension)) {
                isAllowed = true;
                break;
            }
        }

        if (!isAllowed) {
            throw new RuntimeException("Tipo de archivo no permitido. Solo se aceptan: JPG, PNG, WebP");
        }
    }

    /**
     * Guarda la imagen comprimida
     */
    private long saveCompressedImage(BufferedImage image, Path path, String fileName) throws IOException {
        String extension = getFileExtension(fileName).toLowerCase();

        try {
            // Asegurarse de que el directorio existe
            Files.createDirectories(path.getParent());

            if ("png".equals(extension)) {
                boolean written = ImageIO.write(image, "PNG", path.toFile());
                if (!written) {
                    throw new IOException("No se pudo escribir la imagen PNG - ImageIO retornó false");
                }
            } else if ("webp".equals(extension)) {
                // ImageIO estándar no soporta WebP bien, convertir a JPEG
                System.out.println("WebP detectado, guardando como JPEG");
                boolean written = ImageIO.write(image, "JPEG", path.toFile());
                if (!written) {
                    throw new IOException("No se pudo escribir la imagen JPEG (WebP convertido)");
                }
            } else {
                // JPG/JPEG - se comprime con calidad 85
                boolean written = ImageIO.write(image, "JPEG", path.toFile());
                if (!written) {
                    throw new IOException("No se pudo escribir la imagen JPEG - ImageIO retornó false");
                }
            }

            if (!Files.exists(path)) {
                throw new IOException("La imagen no se guardó correctamente - archivo no existe después de write");
            }

            long size = Files.size(path);
            System.out.println("Imagen guardada exitosamente: " + fileName + " - Tamaño: " + size + " bytes");
            return size;
        } catch (IOException e) {
            // Si el archivo se creó parcialmente, intentar eliminarlo
            try {
                if (Files.exists(path)) {
                    Files.delete(path);
                }
            } catch (IOException deleteEx) {
                System.err.println("No se pudo eliminar archivo parcialmente escrito: " + deleteEx.getMessage());
            }
            throw new IOException("Error al guardar la imagen comprimida (" + extension + "): " + e.getMessage(), e);
        }
    }

    /**
     * Genera un nombre único para el archivo
     */
    private String generateFileName(String originalFileName) {
        String extension = getFileExtension(originalFileName);
        return UUID.randomUUID().toString() + "." + extension;
    }

    /**
     * Obtiene la extensión del archivo
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "jpg";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    /**
     * Asegura que el directorio base existe con permisos adecuados
     */
    private void ensureDirectoryExists() throws IOException {
        Path path = Paths.get(imageBasePath);
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                System.out.println("Directorio de imágenes creado: " + path.toAbsolutePath());
            }
            
            // Verificar que el directorio es escribible
            if (!Files.isWritable(path)) {
                throw new IOException("El directorio no es escribible: " + path.toAbsolutePath());
            }
        } catch (IOException e) {
            throw new IOException("Error al crear/verificar directorio de imágenes [" + imageBasePath + "]: " + e.getMessage(), e);
        }
    }

    /**
     * Elimina el archivo de imagen del sistema de archivos
     */
    private void deleteImageFile(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            return;
        }
        
        try {
            Path path = Paths.get(imageBasePath, imagePath).normalize();
            
            // Validar que la ruta está dentro de imageBasePath por seguridad
            if (!path.toAbsolutePath().startsWith(Paths.get(imageBasePath).toAbsolutePath())) {
                logger.warn("Intento de acceso a ruta fuera del directorio permitido: {}", imagePath);
                return;
            }
            
            if (Files.exists(path)) {
                Files.delete(path);
                logger.info("Archivo eliminado: {}", imagePath);
            }
        } catch (IOException e) {
            // Log pero no fallar - la imagen ya está en BD
            logger.error("Error al eliminar archivo: {} - {}", imagePath, e.getMessage());
        }
    }

    /**
     * Valida y limpia duplicados de imágenes en la base de datos
     * Este método se ejecuta al iniciar la aplicación
     */
    public void validateAndCleanupDuplicates() {
        try {
            List<Long> productsWithDuplicates = productImageRepository.findProductsWithDuplicateImages();
            
            if (!productsWithDuplicates.isEmpty()) {
                logger.warn("ALERTA CRÍTICA: Se encontraron {} productos con imágenes duplicadas: {}", 
                    productsWithDuplicates.size(), productsWithDuplicates);
                
                int cleaned = productImageRepository.cleanupDuplicateImages();
                logger.warn("LIMPIEZA COMPLETADA: Se eliminaron {} registros duplicados de product_image", cleaned);
            } else {
                logger.info("Validación de integridad completada: No se encontraron imágenes duplicadas");
            }
        } catch (Exception e) {
            logger.error("Error durante validación de duplicados: {}", e.getMessage(), e);
        }
    }

    /**
     * Obtiene todos los IDs de productos con múltiples imágenes
     */
    public List<Long> getProductsWithDuplicateImages() {
        return productImageRepository.findProductsWithDuplicateImages();
    }

    /**
     * Obtiene todas las imágenes de un producto (útil para auditoría)
     */
    public List<ProductImage> getAllProductImages(Long productId) {
        return productImageRepository.findAllByProductId(productId);
    }

    /**
     * Convierte ProductImage a ProductImageDTO
     */
    private ProductImageDTO convertToDTO(ProductImage image) {
        return new ProductImageDTO(
                image.getId(),
                image.getProduct().getId(),
                image.getImagePath(),
                image.getOriginalFileName(),
                image.getFileSize(),
                image.getCompressedFileSize(),
                image.getCreatedAt(),
                image.getUpdatedAt()
        );
    }
}
