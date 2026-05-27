package com.inventario.licoreria.modules.payment_methods.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class PaymentMethodConfigDTO {

    @NotBlank(message = "El nombre del método de pago es obligatorio")
    private String name;

    @NotBlank(message = "El tipo de método de pago es obligatorio")
    @Pattern(regexp = "^(EFECTIVO|QR)$", message = "El tipo debe ser EFECTIVO o QR")
    private String type;

    private String imageUrl; // URL de la imagen del QR (solo requerida si type es QR)

    private Boolean isActive = true;

    public PaymentMethodConfigDTO() {
    }

    public PaymentMethodConfigDTO(String name, String type) {
        this.name = name;
        this.type = type;
        this.isActive = true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
