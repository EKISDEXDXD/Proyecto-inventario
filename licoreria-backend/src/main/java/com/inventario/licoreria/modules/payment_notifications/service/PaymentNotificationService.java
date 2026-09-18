package com.inventario.licoreria.modules.payment_notifications.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.licoreria.modules.payment_notifications.dto.WebhookPaymentNotificationDTO;
import com.inventario.licoreria.modules.payment_notifications.model.PaymentNotification;
import com.inventario.licoreria.modules.payment_notifications.repository.PaymentNotificationRepository;

@Service
public class PaymentNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentNotificationService.class);

    // Captura montos con el formato "Bs. 150.00", "Bs 150", "150.00 Bs", "S/ 150.00", etc.
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?:Bs\\.?|BOB|S/\\.?)\\s*([0-9]+(?:[.,][0-9]{1,2})?)|([0-9]+(?:[.,][0-9]{1,2})?)\\s*(?:Bs\\.?|BOB)",
            Pattern.CASE_INSENSITIVE);

    private final PaymentNotificationRepository repository;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public PaymentNotificationService(PaymentNotificationRepository repository, ObjectMapper objectMapper,
            SimpMessagingTemplate messagingTemplate) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public PaymentNotification registerFromWebhook(WebhookPaymentNotificationDTO dto) {
        logger.info("📩 [WEBHOOK] Notificación recibida: storeId={}, app={}", dto.getStoreId(), dto.getApp());

        PaymentNotification notification = new PaymentNotification();
        notification.setStoreId(dto.getStoreId());
        notification.setSourceApp(dto.getApp());
        notification.setTitle(dto.getTitle());
        notification.setText(dto.getText());
        notification.setDetectedAt(parseTimestamp(dto.getTimestamp()));
        notification.setAmount(extractAmount(dto.getText()));
        notification.setRawPayload(toRawJson(dto));
        notification.setStatus("PENDING");

        PaymentNotification saved = repository.save(notification);
        logger.info("✅ [WEBHOOK] Notificación guardada: id={}, montoDetectado={}", saved.getId(), saved.getAmount());

        // Empuja la notificación en tiempo real a quien esté suscrito a esa tienda
        messagingTemplate.convertAndSend("/topic/payment-notifications/" + saved.getStoreId(), saved);

        return saved;
    }

    public List<PaymentNotification> findByStore(Long storeId, String status) {
        if (status != null && !status.isBlank()) {
            return repository.findByStoreIdAndStatus(storeId, status.toUpperCase());
        }
        return repository.findByStoreIdOrderByReceivedAtDesc(storeId);
    }

    @Transactional
    public PaymentNotification reconcile(Long id, Long transactionId) {
        PaymentNotification notification = findById(id);
        notification.setStatus("CONCILIATED");
        notification.setMatchedTransactionId(transactionId);
        return repository.save(notification);
    }

    @Transactional
    public PaymentNotification discard(Long id) {
        PaymentNotification notification = findById(id);
        notification.setStatus("DISCARDED");
        return repository.save(notification);
    }

    private PaymentNotification findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificación de pago no encontrada"));
    }

    // Intenta extraer el monto del texto de la notificación con el patrón definido arriba
    private BigDecimal extractAmount(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        if (matcher.find()) {
            String rawAmount = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            try {
                return new BigDecimal(rawAmount.replace(",", "."));
            } catch (NumberFormatException e) {
                logger.warn("⚠️ [WEBHOOK] No se pudo convertir el monto detectado: {}", rawAmount);
                return null;
            }
        }
        logger.warn("⚠️ [WEBHOOK] No se detectó ningún monto en el texto: {}", text);
        return null;
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(timestamp);
        } catch (DateTimeParseException e) {
            logger.warn("⚠️ [WEBHOOK] timestamp con formato inesperado, se usa la hora del servidor: {}", timestamp);
            return LocalDateTime.now();
        }
    }

    private String toRawJson(WebhookPaymentNotificationDTO dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            return String.valueOf(dto);
        }
    }
}
