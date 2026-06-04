package com.orderplatform.orderservice.service;

import com.orderplatform.orderservice.domain.*;
import com.orderplatform.orderservice.dto.*;
import com.orderplatform.orderservice.event.*;
import com.orderplatform.orderservice.exception.OrderNotFoundException;
import com.orderplatform.orderservice.kafka.OrderEventProducer;
import com.orderplatform.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventProducer eventProducer;

    // ── Create ───────────────────────────────────────────────────────────────
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .customerId(request.getCustomerId())
                .customerEmail(request.getCustomerEmail())
                .status(OrderStatus.PENDING)
                .items(mapItems(request.getItems()))
                .totalAmount(calculateTotal(request.getItems()))
                .paymentProcessed(false)
                .inventoryReserved(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Order saved = orderRepository.save(order);
        eventProducer.publishOrderEvent(buildEvent(saved, "ORDER_CREATED", null));

        log.info("Order created: id={} customer={} total={}",
                saved.getId(), saved.getCustomerId(), saved.getTotalAmount());
        return OrderResponse.from(saved);
    }

    // ── Saga: payment callback ────────────────────────────────────────────────
    @Transactional
    public void handlePaymentProcessed(String orderId) {
        Order order = findOrder(orderId);
        if (isTerminal(order)) return;

        order.setPaymentProcessed(true);
        order.setUpdatedAt(LocalDateTime.now());

        if (order.isInventoryReserved()) {
            confirmOrder(order);
        }
        orderRepository.save(order);
        log.info("Payment processed for orderId={}  inventoryReserved={}", orderId, order.isInventoryReserved());
    }

    @Transactional
    public void handlePaymentFailed(String orderId, String reason) {
        Order order = findOrder(orderId);
        if (isTerminal(order)) return;

        order.setStatus(OrderStatus.CANCELLED);
        order.setFailureReason("Payment failed: " + reason);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        eventProducer.publishOrderEvent(buildEvent(order, "ORDER_CANCELLED", order.getFailureReason()));
        log.warn("Order {} cancelled — payment failed: {}", orderId, reason);
    }

    // ── Saga: inventory callback ──────────────────────────────────────────────
    @Transactional
    public void handleInventoryReserved(String orderId) {
        Order order = findOrder(orderId);
        if (isTerminal(order)) return;

        order.setInventoryReserved(true);
        order.setUpdatedAt(LocalDateTime.now());

        if (order.isPaymentProcessed()) {
            confirmOrder(order);
        }
        orderRepository.save(order);
        log.info("Inventory reserved for orderId={}  paymentProcessed={}", orderId, order.isPaymentProcessed());
    }

    @Transactional
    public void handleInventoryFailed(String orderId, String reason) {
        Order order = findOrder(orderId);
        if (isTerminal(order)) return;

        order.setStatus(OrderStatus.CANCELLED);
        order.setFailureReason("Inventory failed: " + reason);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        // ORDER_CANCELLED triggers inventory rollback in inventory-service if stock was partially reserved
        eventProducer.publishOrderEvent(buildEvent(order, "ORDER_CANCELLED", order.getFailureReason()));
        log.warn("Order {} cancelled — inventory failed: {}", orderId, reason);
    }

    // ── Queries ──────────────────────────────────────────────────────────────
    public OrderResponse getOrder(String orderId) {
        return OrderResponse.from(findOrder(orderId));
    }

    public List<OrderResponse> getOrdersByCustomer(String customerId) {
        return orderRepository.findByCustomerId(customerId)
                .stream().map(OrderResponse::from).collect(Collectors.toList());
    }

    // ── Private helpers ───────────────────────────────────────────────────────
    private void confirmOrder(Order order) {
        order.setStatus(OrderStatus.CONFIRMED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        eventProducer.publishOrderEvent(buildEvent(order, "ORDER_CONFIRMED", null));
        log.info("Order CONFIRMED: id={}", order.getId());
    }

    private boolean isTerminal(Order order) {
        boolean terminal = order.getStatus() == OrderStatus.CONFIRMED
                || order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.FAILED;
        if (terminal) log.warn("Ignoring event for order {} — already in terminal state {}", order.getId(), order.getStatus());
        return terminal;
    }

    private Order findOrder(String orderId) {
        return orderRepository.findById(UUID.fromString(orderId))
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private OrderEvent buildEvent(Order order, String eventType, String reason) {
        return OrderEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .orderId(order.getId().toString())
                .customerId(order.getCustomerId())
                .customerEmail(order.getCustomerEmail())
                .items(order.getItems().stream()
                        .map(i -> OrderItemDto.builder()
                                .productId(i.getProductId())
                                .productName(i.getProductName())
                                .quantity(i.getQuantity())
                                .unitPrice(i.getUnitPrice())
                                .build())
                        .collect(Collectors.toList()))
                .totalAmount(order.getTotalAmount())
                .reason(reason)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    private List<OrderItem> mapItems(List<OrderItemRequest> requests) {
        return requests.stream()
                .map(r -> OrderItem.builder()
                        .id(UUID.randomUUID())
                        .productId(r.getProductId())
                        .productName(r.getProductName())
                        .quantity(r.getQuantity())
                        .unitPrice(r.getUnitPrice())
                        .build())
                .collect(Collectors.toList());
    }

    private BigDecimal calculateTotal(List<OrderItemRequest> items) {
        return items.stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
