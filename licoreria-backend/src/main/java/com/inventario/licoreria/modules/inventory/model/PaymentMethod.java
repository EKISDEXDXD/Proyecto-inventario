package com.inventario.licoreria.modules.inventory.model;

import com.inventario.licoreria.modules.payment_methods.model.PaymentMethodConfig;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.springframework.lang.NonNull;

@Entity
@Table(name = "payment_method")
public class PaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    @JsonIgnore
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "payment_method_config_id", nullable = false)
    private PaymentMethodConfig paymentMethodConfig;

    public PaymentMethod() {
    }

    public PaymentMethod(Transaction transaction, PaymentMethodConfig paymentMethodConfig) {
        this.transaction = transaction;
        this.paymentMethodConfig = paymentMethodConfig;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @NonNull
    @SuppressWarnings("null")
    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    @NonNull
    @SuppressWarnings("null")
    public PaymentMethodConfig getPaymentMethodConfig() {
        return paymentMethodConfig;
    }

    public void setPaymentMethodConfig(PaymentMethodConfig paymentMethodConfig) {
        this.paymentMethodConfig = paymentMethodConfig;
    }

    // Helper method for backwards compatibility
    @NonNull
    @SuppressWarnings("null")
    public Long getTransactionId() {
        return transaction != null ? transaction.getId() : null;
    }

    public void setTransactionId(Long transactionId) {
        // This is handled by the transaction relationship
        // But kept for backwards compatibility
    }
}
