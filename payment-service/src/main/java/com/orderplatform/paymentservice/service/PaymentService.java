package com.orderplatform.paymentservice.service;

import com.orderplatform.paymentservice.domain.Payment;
import com.orderplatform.paymentservice.domain.PaymentStatus;
import com.orderplatform.paymentservice.event.OrderEvent;
import com.orderplatform.paymentservice.event.PaymentEvent;
import com.orderplatform.paymentservice.kafka.PaymentEventProducer;
import com.orderplatform.paymentservice.repository.PaymentRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventProducer eventProducer;
    private final StringRedisTemplate redisTemplate;

    private static final String IDEMPOTENCY_PREFIX = "payment:processed:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private final Random random = new Random();

    @CircuitBreaker(name = "paymentProcessor", fallbackMethod = "processPaymentFallback")
    @Transactional
    public void processPayment(OrderEvent event) {
        String key = IDEMPOTENCY_PREFIX + event.getOrderId();

        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "processing", IDEMPOTENCY_TTL);
        if (!Boolean.TRUE.equals(isNew)) {
            log.info("Duplicate payment request for orderId={} — skipping", event.getOrderId());
            return;
        }

        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .amount(event.getTotalAmount())
                .status(PaymentStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .build();
        paymentRepository.save(payment);

        try {
            boolean success = callPaymentGateway();

            if (success) {
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setProcessedAt(LocalDateTime.now());
                paymentRepository.save(payment);
                redisTemplate.opsForValue().set(key, "completed", IDEMPOTENCY_TTL);
                eventProducer.publish(PaymentEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType("PAYMENT_PROCESSED")
                        .orderId(event.getOrderId())
                        .paymentId(payment.getId().toString())
                        .amount(event.getTotalAmount())
                        .timestamp(LocalDateTime.now().toString())
                        .build());
                log.info("Payment COMPLETED for orderId={}", event.getOrderId());
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Insufficient funds");
                paymentRepository.save(payment);
                redisTemplate.delete(key);
                eventProducer.publish(PaymentEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType("PAYMENT_FAILED")
                        .orderId(event.getOrderId())
                        .paymentId(payment.getId().toString())
                        .amount(event.getTotalAmount())
                        .reason("Insufficient funds")
                        .timestamp(LocalDateTime.now().toString())
                        .build());
                log.warn("Payment FAILED for orderId={}", event.getOrderId());
            }
        } catch (RuntimeException ex) {
            redisTemplate.delete(key);
            throw ex;
        }
    }

    public void processPaymentFallback(OrderEvent event, Exception ex) {
        log.error("Circuit breaker OPEN for orderId={}: {}", event.getOrderId(), ex.getMessage());
        eventProducer.publish(PaymentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("PAYMENT_FAILED")
                .orderId(event.getOrderId())
                .reason("Payment gateway unavailable")
                .timestamp(LocalDateTime.now().toString())
                .build());
    }

    private boolean callPaymentGateway() {
        try {
            Thread.sleep(50 + random.nextInt(100));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (random.nextInt(100) < 5) {
            throw new RuntimeException("Payment gateway timeout");
        }
        return random.nextInt(100) < 90;
    }
}
