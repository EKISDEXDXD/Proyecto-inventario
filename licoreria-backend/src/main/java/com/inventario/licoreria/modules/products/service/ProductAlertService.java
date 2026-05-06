package com.inventario.licoreria.modules.products.service;

import com.inventario.licoreria.modules.products.dto.ProductAlertDTO;
import com.inventario.licoreria.modules.products.model.Product;
import com.inventario.licoreria.modules.products.model.ProductAlert;
import com.inventario.licoreria.modules.products.repository.ProductAlertRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.lang.NonNull;
import java.util.Optional;

@Service
public class ProductAlertService {
    
    private final ProductAlertRepository productAlertRepository;
    private final ProductService productService;
    
    public ProductAlertService(ProductAlertRepository productAlertRepository, ProductService productService) {
        this.productAlertRepository = productAlertRepository;
        this.productService = productService;
    }
    
    @Transactional
    public ProductAlert saveOrUpdate(@NonNull Long productId, @NonNull ProductAlertDTO dto, @NonNull String username) {
        // Validar que el usuario es propietario del producto
        Product product = productService.findById(productId);
        productService.validateUserOwnsProduct(productId, username);
        
        Optional<ProductAlert> existingAlert = productAlertRepository.findByProductId(productId);
        
        ProductAlert alert;
        if (existingAlert.isPresent()) {
            alert = existingAlert.get();
            alert.setThreshold(dto.getThreshold());
            alert.setIsEnabled(dto.getIsEnabled());
        } else {
            alert = new ProductAlert(product, dto.getThreshold(), dto.getIsEnabled());
        }
        
        return productAlertRepository.save(alert);
    }
    
    public Optional<ProductAlert> findByProductId(@NonNull Long productId) {
        return productAlertRepository.findByProductId(productId);
    }
    
    @Transactional
    public void deleteByProductId(@NonNull Long productId) {
        Optional<ProductAlert> alert = productAlertRepository.findByProductId(productId);
        if (alert.isPresent()) {
            productAlertRepository.delete(alert.get());
        }
    }
    
    public boolean existsByProductId(@NonNull Long productId) {
        return productAlertRepository.existsByProductId(productId);
    }
}
