package com.inventario.licoreria.modules.products.dto;

import java.math.BigDecimal;

public class ProductResponseDTO {
    
    private Long id;
    private String name;
    private String description;
    private BigDecimal cost;
    private BigDecimal price;
    private Integer stock;
    private Integer initialStock;
    private Long storeId;
    private ProductAlertDTO alert;
    
    public ProductResponseDTO() {
    }
    
    public ProductResponseDTO(Long id, String name, String description, BigDecimal cost, 
                             BigDecimal price, Integer stock, Integer initialStock, Long storeId) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.cost = cost;
        this.price = price;
        this.stock = stock;
        this.initialStock = initialStock;
        this.storeId = storeId;
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public BigDecimal getCost() {
        return cost;
    }
    
    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    
    public Integer getStock() {
        return stock;
    }
    
    public void setStock(Integer stock) {
        this.stock = stock;
    }
    
    public Integer getInitialStock() {
        return initialStock;
    }
    
    public void setInitialStock(Integer initialStock) {
        this.initialStock = initialStock;
    }
    
    public Long getStoreId() {
        return storeId;
    }
    
    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }
    
    public ProductAlertDTO getAlert() {
        return alert;
    }
    
    public void setAlert(ProductAlertDTO alert) {
        this.alert = alert;
    }
}
