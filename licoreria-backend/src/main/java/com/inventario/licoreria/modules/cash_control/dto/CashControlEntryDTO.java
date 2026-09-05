package com.inventario.licoreria.modules.cash_control.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public class CashControlEntryDTO {

    @NotNull(message = "El ID de la tienda es obligatorio")
    private Long storeId;

    @NotNull(message = "La fecha del gasto es obligatoria")
    private LocalDate entryDate;

    @NotBlank(message = "El concepto es obligatorio")
    private String concept;

    @NotNull(message = "El monto es obligatorio")
    private BigDecimal amount;

    @NotBlank(message = "El tipo de gasto es obligatorio")
    private String expenseType;

    @NotBlank(message = "El origen del dinero es obligatorio")
    private String moneyOrigin;

    private String detail;
    private String responsibleName;
    private String status;
    private String verificationPassword;

    public CashControlEntryDTO() {
    }

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public String getConcept() {
        return concept;
    }

    public void setConcept(String concept) {
        this.concept = concept;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getExpenseType() {
        return expenseType;
    }

    public void setExpenseType(String expenseType) {
        this.expenseType = expenseType;
    }

    public String getMoneyOrigin() {
        return moneyOrigin;
    }

    public void setMoneyOrigin(String moneyOrigin) {
        this.moneyOrigin = moneyOrigin;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getResponsibleName() {
        return responsibleName;
    }

    public void setResponsibleName(String responsibleName) {
        this.responsibleName = responsibleName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVerificationPassword() {
        return verificationPassword;
    }

    public void setVerificationPassword(String verificationPassword) {
        this.verificationPassword = verificationPassword;
    }
}
