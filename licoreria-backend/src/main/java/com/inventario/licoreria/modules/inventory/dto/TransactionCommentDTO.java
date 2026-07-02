package com.inventario.licoreria.modules.inventory.dto;

import java.time.LocalDateTime;

public class TransactionCommentDTO {

    private Long id;
    private Long transactionId;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public TransactionCommentDTO() {
    }

    public TransactionCommentDTO(Long id, Long transactionId, String comment, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.transactionId = transactionId;
        this.comment = comment;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
