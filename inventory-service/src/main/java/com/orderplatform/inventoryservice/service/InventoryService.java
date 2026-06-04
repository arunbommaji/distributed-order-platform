package com.orderplatform.inventoryservice.service;

import com.orderplatform.inventoryservice.domain.InventoryReservation;
import com.orderplatform.inventoryservice.domain.Product;
import com.orderplatform.inventoryservice.event.InventoryEvent;
import com.orderplatform.inventoryservice.event.OrderEvent;
import com.orderplatform.inventoryservice.event.OrderItemDto;
import com.orderplatform.inventoryservice.kafka.InventoryEventProducer;
import com.orderplatform.inventoryservice.repository.InventoryReservationRepository;
import com.orderplatform.inventoryservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryEventProducer eventProducer;
    private final StringRedisTemplate redisTemplate;

    private static final String IDEMPOTENCY_PREFIX = "inventory:processed:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 200, multiplier = 2.0)
    )
    @Transactional
    public void reserveInventory(OrderEvent event) {
        String key = IDEMPOTENCY_PREFIX + event.getOrderId();

        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "processing", IDEMPOTENCY_TTL);
        if (!Boolean.TRUE.equals(isNew)) {
            log.info("Duplicate inventory request for orderId={} — skipping", event.getOrderId());
            return;
        }

        List<OrderItemDto> items = event.getItems();
        if (items == null || items.isEmpty()) {
            redisTemplate.delete(key);
            publishFailed(event.getOrderId(), "No items in order");
            return;
        }

        // ── Phase 1: validate all items available (fail fast) ──────────────
        for (OrderItemDto item : items) {
            Product product = productRepository.findByProductId(item.getProductId())
                    .orElse(null);
            if (product == null) {
                redisTemplate.delete(key);
                publishFailed(event.getOrderId(), "Product not found: " + item.getProductId());
                return;
            }
            if (product.getAvailableQuantity() < item.getQuantity()) {
                redisTemplate.delete(key);
                publishFailed(event.getOrderId(),
                        "Insufficient stock for " + item.getProductId() +
                        " (need=" + item.getQuantity() + " available=" + product.getAvailableQuantity() + ")");
                return;
            }
        }

        // ── Phase 2: reserve atomically (optimistic locking via @Version) ──
        List<InventoryReservation> reservations = new ArrayList<>();
        try {
            for (OrderItemDto item : items) {
                Product product = productRepository.findByProductIdForUpdate(item.getProductId()).get();
                product.setAvailableQuantity(product.getAvailableQuantity() - item.getQuantity());
                product.setReservedQuantity(product.getReservedQuantity() + item.getQuantity());
                productRepository.save(product);

                reservations.add(InventoryReservation.builder()
                        .id(UUID.randomUUID())
                        .orderId(event.getOrderId())
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .status("RESERVED")
                        .createdAt(LocalDateTime.now())
                        .build());
            }
            reservationRepository.saveAll(reservations);
        } catch (ObjectOptimisticLockingFailureException ex) {
            redisTemplate.delete(key);   // let retry re-enter
            throw ex;
        }

        redisTemplate.opsForValue().set(key, "reserved", IDEMPOTENCY_TTL);
        eventProducer.publish(InventoryEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("INVENTORY_RESERVED")
                .orderId(event.getOrderId())
                .timestamp(LocalDateTime.now().toString())
                .build());
        log.info("Inventory RESERVED for orderId={}", event.getOrderId());
    }

    @Transactional
    public void releaseInventory(String orderId) {
        List<InventoryReservation> reservations = reservationRepository.findByOrderId(orderId);
        for (InventoryReservation res : reservations) {
            if ("RESERVED".equals(res.getStatus())) {
                productRepository.findByProductId(res.getProductId()).ifPresent(product -> {
                    product.setAvailableQuantity(product.getAvailableQuantity() + res.getQuantity());
                    product.setReservedQuantity(Math.max(0, product.getReservedQuantity() - res.getQuantity()));
                    productRepository.save(product);
                });
                res.setStatus("RELEASED");
                reservationRepository.save(res);
            }
        }
        log.info("Inventory RELEASED for orderId={} ({} reservations)", orderId, reservations.size());
    }

    private void publishFailed(String orderId, String reason) {
        log.warn("Inventory FAILED for orderId={}: {}", orderId, reason);
        eventProducer.publish(InventoryEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("INVENTORY_FAILED")
                .orderId(orderId)
                .reason(reason)
                .timestamp(LocalDateTime.now().toString())
                .build());
    }
}
