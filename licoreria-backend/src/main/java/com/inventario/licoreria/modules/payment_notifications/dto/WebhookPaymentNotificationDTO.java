package com.inventario.licoreria.modules.payment_notifications.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo JSON que MacroDroid (o Automate/Tasker) debe enviar por HTTP POST
 * cuando detecta una notificación de pago en el celular.
 */
public class WebhookPaymentNotificationDTO {

    @NotNull(message = "storeId es obligatorio")
    private Long storeId;

    // Paquete o nombre de la app que generó la notificación (ej: com.tigo.money)
    private String app;

    private String title;

    @NotBlank(message = "text es obligatorio (es el cuerpo de la notificación a parsear)")
    private String text;

    // Fecha/hora en la que el celular detectó la notificación, en formato ISO-8601 (opcional)
    private String timestamp;

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
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

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
