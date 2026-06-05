package com.inventario.licoreria.modules.store.dto;

import jakarta.validation.constraints.NotBlank;

public class StoreUpdateDTO {

    @NotBlank(message = "El nombre de la tienda es obligatorio")
    private String name;

    private String accessPassword;

    private String address;

    private String description;

    private String color;

    public StoreUpdateDTO() {}

    public StoreUpdateDTO(String name, String accessPassword) {
        this.name = name;
        this.accessPassword = accessPassword;
    }

    public StoreUpdateDTO(String name, String accessPassword, String address, String description) {
        this.name = name;
        this.accessPassword = accessPassword;
        this.address = address;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAccessPassword() {
        return accessPassword;
    }

    public void setAccessPassword(String accessPassword) {
        this.accessPassword = accessPassword;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
