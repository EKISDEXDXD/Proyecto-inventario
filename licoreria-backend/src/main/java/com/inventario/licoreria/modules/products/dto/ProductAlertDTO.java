package com.inventario.licoreria.modules.products.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public class ProductAlertDTO {
    
    @NotNull(message = "El umbral es obligatorio")
    @PositiveOrZero(message = "El umbral debe ser mayor o igual a cero")
    private Integer threshold;
    
    @NotNull(message = "El estado de la alerta es obligatorio")
    private Boolean isEnabled;
    
    public ProductAlertDTO() {
    }
    
    public ProductAlertDTO(Integer threshold, Boolean isEnabled) {
        this.threshold = threshold;
        this.isEnabled = isEnabled;
    }
    
    public Integer getThreshold() {
        return threshold;
    }
    
    public void setThreshold(Integer threshold) {
        this.threshold = threshold;
    }
    
    public Boolean getIsEnabled() {
        return isEnabled;
    }
    
    public void setIsEnabled(Boolean isEnabled) {
        this.isEnabled = isEnabled;
    }
}
