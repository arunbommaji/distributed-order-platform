package com.orderplatform.notificationservice.kafka;

import com.orderplatform.notificationservice.event.InventoryEvent;
import com.orderplatform.notificationservice.event.OrderEvent;
import com.orderplatform.notificationservice.event.PaymentEvent;
import com.orderplatform.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumer {

    private final NotificationService notificationService;

    @RetryableTopic(attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt")
    @KafkaListener(topics = "${kafka.topic.order-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "orderListenerFactory")
    public void onOrderEvent(OrderEvent event) {
        log.info("Notification: received order event [{}] for orderId={}", event.getEventType(), event.getOrderId());
        switch (event.getEventType()) {
            case "ORDER_CONFIRMED" ->
                    notificationService.sendOrderConfirmed(event.getOrderId(), event.getCustomerEmail());
            case "ORDER_CANCELLED" ->
                    notificationService.sendOrderCancelled(event.getOrderId(), event.getCustomerEmail(), event.getReason());
            default -> log.debug("Order event {} — no notification action", event.getEventType());
        }
    }

    @RetryableTopic(attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt")
    @KafkaListener(topics = "${kafka.topic.payment-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "paymentListenerFactory")
    public void onPaymentEvent(PaymentEvent event) {
        log.info("Notification: received payment event [{}] for orderId={}", event.getEventType(), event.getOrderId());
        if ("PAYMENT_FAILED".equals(event.getEventType())) {
            notificationService.sendPaymentFailed(event.getOrderId(), "customer@example.com", event.getReason());
        }
    }

    @RetryableTopic(attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt")
    @KafkaListener(topics = "${kafka.topic.inventory-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "inventoryListenerFactory")
    public void onInventoryEvent(InventoryEvent event) {
        log.info("Notification: received inventory event [{}] for orderId={}", event.getEventType(), event.getOrderId());
        if ("INVENTORY_FAILED".equals(event.getEventType())) {
            notificationService.sendInventoryFailed(event.getOrderId(), "customer@example.com", event.getReason());
        }
    }
}
