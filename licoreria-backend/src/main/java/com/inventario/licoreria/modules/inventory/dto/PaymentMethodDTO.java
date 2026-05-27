package com.inventario.licoreria.modules.inventory.dto;

import jakarta.validation.constraints.NotNull;

public class PaymentMethodDTO {

    @NotNull(message = "El ID de la transacción es obligatorio")
    private Long transactionId;

    @NotNull(message = "El ID de la configuración del método de pago es obligatorio")
    private Long paymentMethodConfigId;

    public PaymentMethodDTO() {
    }

    public PaymentMethodDTO(Long transactionId, Long paymentMethodConfigId) {
        this.transactionId = transactionId;
        this.paymentMethodConfigId = paymentMethodConfigId;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public Long getPaymentMethodConfigId() {
        return paymentMethodConfigId;
    }

    public void setPaymentMethodConfigId(Long paymentMethodConfigId) {
        this.paymentMethodConfigId = paymentMethodConfigId;
    }
}
