package com.inventario.licoreria.modules.cash_control.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

public class DailyFlowCloseDTO {

    @NotNull(message = "La tienda es obligatoria")
    private Long storeId;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate summaryDate;

    @NotNull(message = "El monto ganado es obligatorio")
    @PositiveOrZero(message = "El monto ganado no puede ser negativo")
    private BigDecimal earnedAmount;

    private String verificationPassword;

    public DailyFlowCloseDTO() {
    }

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public LocalDate getSummaryDate() {
        return summaryDate;
    }

    public void setSummaryDate(LocalDate summaryDate) {
        this.summaryDate = summaryDate;
    }

    public BigDecimal getEarnedAmount() {
        return earnedAmount;
    }

    public void setEarnedAmount(BigDecimal earnedAmount) {
        this.earnedAmount = earnedAmount;
    }

    public String getVerificationPassword() {
        return verificationPassword;
    }

    public void setVerificationPassword(String verificationPassword) {
        this.verificationPassword = verificationPassword;
    }
}
