package com.inventario.licoreria.modules.cash_control.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "cash_control_summary")
public class CashControlSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private LocalDate summaryDate;

    private BigDecimal moneyReal = BigDecimal.ZERO;
    private BigDecimal moneyPhysical = BigDecimal.ZERO;
    private BigDecimal storeMoney = BigDecimal.ZERO;
    private BigDecimal grossProfitMoney = BigDecimal.ZERO;
    private BigDecimal totalExpenses = BigDecimal.ZERO;
    private BigDecimal balanceAvailable = BigDecimal.ZERO;
    private boolean dailyFlowClosed = false;

    public CashControlSummary() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public BigDecimal getTotalExpenses() {
        return totalExpenses;
    }

    public void setTotalExpenses(BigDecimal totalExpenses) {
        this.totalExpenses = totalExpenses;
    }

    public BigDecimal getBalanceAvailable() {
        return balanceAvailable;
    }

    public void setBalanceAvailable(BigDecimal balanceAvailable) {
        this.balanceAvailable = balanceAvailable;
    }

    public boolean isDailyFlowClosed() {
        return dailyFlowClosed;
    }

    public void setDailyFlowClosed(boolean dailyFlowClosed) {
        this.dailyFlowClosed = dailyFlowClosed;
    }
}
