package com.inventario.licoreria.modules.cash_control.dto;

import java.math.BigDecimal;

public class CashControlSummaryUpdateDTO {

    private Long storeId;
    private BigDecimal moneyReal;
    private BigDecimal moneyPhysical;
    private BigDecimal storeMoney;
    private BigDecimal grossProfitMoney;
    private String verificationPassword;

    public CashControlSummaryUpdateDTO() {
    }

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public BigDecimal getMoneyReal() {
        return moneyReal;
    }

    public void setMoneyReal(BigDecimal moneyReal) {
        this.moneyReal = moneyReal;
    }

    public BigDecimal getMoneyPhysical() {
        return moneyPhysical;
    }

    public void setMoneyPhysical(BigDecimal moneyPhysical) {
        this.moneyPhysical = moneyPhysical;
    }

    public BigDecimal getStoreMoney() {
        return storeMoney;
    }

    public void setStoreMoney(BigDecimal storeMoney) {
        this.storeMoney = storeMoney;
    }

    public BigDecimal getGrossProfitMoney() {
        return grossProfitMoney;
    }

    public void setGrossProfitMoney(BigDecimal grossProfitMoney) {
        this.grossProfitMoney = grossProfitMoney;
    }

    public String getVerificationPassword() {
        return verificationPassword;
    }

    public void setVerificationPassword(String verificationPassword) {
        this.verificationPassword = verificationPassword;
    }
}
