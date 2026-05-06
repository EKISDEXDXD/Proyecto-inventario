package com.inventario.licoreria.modules.products.model;

import com.inventario.licoreria.modules.store.model.Store;
import com.inventario.licoreria.modules.inventory.model.Transaction;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.CascadeType;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.lang.NonNull;

@Entity
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String description;
    private BigDecimal cost;
    private BigDecimal price;
    private Integer stock;
    private Integer initialStock;

    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @JsonIgnore
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Transaction> transactions;

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private ProductAlert alert;

    public Product() {
    }

    @NonNull
    @SuppressWarnings("null")
    public Long getId() {
        return (Long) id;
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

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<Transaction> transactions) {
        this.transactions = transactions;
    }

    public ProductAlert getAlert() {
        return alert;
    }

    public void setAlert(ProductAlert alert) {
        this.alert = alert;
    }
}
