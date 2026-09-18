package com.inventario.licoreria.modules.payment_notifications.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.inventario.licoreria.modules.payment_notifications.dto.WebhookPaymentNotificationDTO;
import com.inventario.licoreria.modules.payment_notifications.model.PaymentNotification;
import com.inventario.licoreria.modules.payment_notifications.service.PaymentNotificationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payment-notifications")
public class PaymentNotificationController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentNotificationController.class);
    private static final String SECRET_HEADER = "X-Webhook-Secret";

    private final PaymentNotificationService paymentNotificationService;

    @Value("${app.webhook.payment-notifications.secret}")
    private String webhookSecret;

    public PaymentNotificationController(PaymentNotificationService paymentNotificationService) {
        this.paymentNotificationService = paymentNotificationService;
    }

    // Endpoint público (sin JWT): lo llama MacroDroid/Automate desde el celular.
    // Se protege únicamente con el secreto compartido en el header.
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> receiveWebhook(
            @RequestHeader(value = SECRET_HEADER, required = false) String providedSecret,
            @Valid @RequestBody WebhookPaymentNotificationDTO dto) {

        if (webhookSecret == null || webhookSecret.isBlank() || !webhookSecret.equals(providedSecret)) {
            logger.warn("🚫 [WEBHOOK] Intento de acceso con secreto inválido");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Secreto de webhook inválido");
        }

        PaymentNotification saved = paymentNotificationService.registerFromWebhook(dto);
        return ResponseEntity.ok(Map.of(
                "id", saved.getId(),
                "amountDetected", saved.getAmount() != null ? saved.getAmount() : "no-detectado",
                "status", saved.getStatus()
        ));
    }

    // A partir de aquí, endpoints protegidos por JWT (igual que el resto de la API)
    @GetMapping("/store/{storeId}")
    public List<PaymentNotification> getByStore(
            @PathVariable @NonNull Long storeId,
            @RequestParam(required = false) String status) {
        return paymentNotificationService.findByStore(storeId, status);
    }

    @PatchMapping("/{id}/reconcile")
    public ResponseEntity<PaymentNotification> reconcile(
            @PathVariable @NonNull Long id,
            @RequestBody Map<String, Long> body) {
        Long transactionId = body.get("transactionId");
        return ResponseEntity.ok(paymentNotificationService.reconcile(id, transactionId));
    }

    @PatchMapping("/{id}/discard")
    public ResponseEntity<PaymentNotification> discard(@PathVariable @NonNull Long id) {
        return ResponseEntity.ok(paymentNotificationService.discard(id));
    }
}
