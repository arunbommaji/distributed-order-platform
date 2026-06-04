package com.orderplatform.inventoryservice.kafka;

import com.orderplatform.inventoryservice.event.OrderEvent;
import com.orderplatform.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final InventoryService inventoryService;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(
            topics = "${kafka.topic.order-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "orderKafkaListenerContainerFactory"
    )
    public void handleOrderEvent(OrderEvent event) {
        switch (event.getEventType()) {
            case "ORDER_CREATED" -> {
                log.info("Reserving inventory for orderId={}", event.getOrderId());
                inventoryService.reserveInventory(event);
            }
            case "ORDER_CANCELLED" -> {
                log.info("Releasing inventory for orderId={}", event.getOrderId());
                inventoryService.releaseInventory(event.getOrderId());
            }
            default -> log.debug("Ignoring event type {} in inventory-service", event.getEventType());
        }
    }

    @DltHandler
    public void handleDlt(OrderEvent event) {
        log.error("ORDER event in DLT — orderId={} type={}", event.getOrderId(), event.getEventType());
    }
}
