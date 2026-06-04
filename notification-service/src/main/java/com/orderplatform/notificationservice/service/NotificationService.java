package com.orderplatform.notificationservice.service;

import com.orderplatform.notificationservice.domain.NotificationLog;
import com.orderplatform.notificationservice.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationLogRepository logRepository;

    @Transactional
    public void sendOrderConfirmed(String orderId, String customerEmail) {
        String msg = "🎉 Your order #" + orderId + " has been confirmed! It will be shipped within 2 business days.";
        send(orderId, "ORDER_CONFIRMED", "EMAIL", customerEmail, msg);
    }

    @Transactional
    public void sendOrderCancelled(String orderId, String customerEmail, String reason) {
        String msg = "We're sorry, your order #" + orderId + " was cancelled. Reason: " + reason +
                     ". Any charges will be refunded within 3-5 business days.";
        send(orderId, "ORDER_CANCELLED", "EMAIL", customerEmail, msg);
    }

    @Transactional
    public void sendPaymentFailed(String orderId, String customerEmail, String reason) {
        String msg = "Payment failed for order #" + orderId + ": " + reason +
                     ". Please update your payment details and try again.";
        send(orderId, "PAYMENT_FAILED", "EMAIL", customerEmail, msg);
        send(orderId, "PAYMENT_FAILED", "SMS", customerEmail, "Payment failed for order " + orderId + ". Check email.");
    }

    @Transactional
    public void sendInventoryFailed(String orderId, String customerEmail, String reason) {
        String msg = "Unfortunately we couldn't fulfill order #" + orderId + ": " + reason;
        send(orderId, "INVENTORY_FAILED", "EMAIL", customerEmail, msg);
    }

    private void send(String orderId, String type, String channel, String recipient, String message) {
        // Simulate channel dispatch
        if ("EMAIL".equals(channel)) {
            log.info("[EMAIL → {}] {} | {}", recipient, type, message);
        } else if ("SMS".equals(channel)) {
            log.info("[SMS  → {}] {} | {}", recipient, type, message);
        }

        NotificationLog entry = NotificationLog.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .notificationType(type)
                .channel(channel)
                .recipient(recipient)
                .message(message)
                .status("SENT")
                .sentAt(LocalDateTime.now())
                .build();
        logRepository.save(entry);
    }
}
