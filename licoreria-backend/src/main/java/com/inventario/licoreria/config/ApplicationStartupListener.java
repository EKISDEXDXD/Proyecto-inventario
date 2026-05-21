package com.inventario.licoreria.config;

import com.inventario.licoreria.modules.products.service.ProductImageService;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ejecuta tareas de validación y limpieza al iniciar la aplicación
 */
@Component
public class ApplicationStartupListener {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationStartupListener.class);
    private final ProductImageService productImageService;

    public ApplicationStartupListener(ProductImageService productImageService) {
        this.productImageService = productImageService;
    }

    /**
     * Se ejecuta cuando la aplicación está completamente inicializada
     */
    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationReady() {
        logger.info("================== INICIANDO VALIDACIÓN DE INTEGRIDAD ==================");
        
        try {
            // Validar y limpiar imágenes duplicadas
            logger.info("Validando integridad de imágenes de productos...");
            productImageService.validateAndCleanupDuplicates();
            
            logger.info("================== VALIDACIÓN COMPLETADA EXITOSAMENTE ==================");
        } catch (Exception e) {
            logger.error("Error durante la validación de integridad: {}", e.getMessage(), e);
            // No fallar el startup, solo loguear el error
        }
    }
}
