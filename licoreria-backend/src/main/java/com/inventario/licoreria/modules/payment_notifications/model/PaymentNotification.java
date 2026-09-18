package com.inventario.licoreria.modules.payment_notifications.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_notification")
public class PaymentNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    // Nombre del paquete/app que generó la notificación (ej: com.tigo.money)
    @Column(name = "source_app")
    private String sourceApp;

    @Column(name = "title")
    private String title;

    // Texto completo de la notificación, tal como llegó al celular
    @Column(name = "text", columnDefinition = "TEXT")
    private String text;

    // Monto extraído por regex del texto (null si no se pudo parsear)
    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    // Remitente extraído por regex del texto, si el patrón lo captura
    @Column(name = "sender_info")
    private String senderInfo;

    // Payload JSON crudo recibido en el webhook, para depuración si el parseo falla
    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    // Momento en el que el celular detectó la notificación (si MacroDroid lo manda)
    @Column(name = "detected_at")
    private LocalDateTime detectedAt;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    // PENDING | CONCILIATED | DISCARDED
    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "matched_transaction_id")
    private Long matchedTransactionId;

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "PENDING";
        }
    }

    public PaymentNotification() {
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

    public String getSourceApp() {
        return sourceApp;
    }

    public void setSourceApp(String sourceApp) {
        this.sourceApp = sourceApp;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getSenderInfo() {
        return senderInfo;
    }

    public void setSenderInfo(String senderInfo) {
        this.senderInfo = senderInfo;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(LocalDateTime detectedAt) {
        this.detectedAt = detectedAt;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getMatchedTransactionId() {
        return matchedTransactionId;
    }

    public void setMatchedTransactionId(Long matchedTransactionId) {
        this.matchedTransactionId = matchedTransactionId;
    }
}
