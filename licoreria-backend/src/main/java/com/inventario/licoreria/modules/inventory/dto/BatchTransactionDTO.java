package com.inventario.licoreria.modules.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class BatchTransactionDTO {

    @NotEmpty(message = "La lista de transacciones no puede estar vacía")
    @Valid
    private List<TransactionDTO> transactions;

    public BatchTransactionDTO() {
    }

    public BatchTransactionDTO(List<TransactionDTO> transactions) {
        this.transactions = transactions;
    }

    public List<TransactionDTO> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<TransactionDTO> transactions) {
        this.transactions = transactions;
    }
}
