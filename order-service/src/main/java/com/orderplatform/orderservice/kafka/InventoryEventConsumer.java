package com.orderplatform.orderservice.kafka;

import com.orderplatform.orderservice.event.InventoryEvent;
import com.orderplatform.orderservice.service.OrderService;
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
public class InventoryEventConsumer {

    private final OrderService orderService;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(
            topics = "${kafka.topic.inventory-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "inventoryKafkaListenerContainerFactory"
    )
    public void handleInventoryEvent(InventoryEvent event) {
        log.info("Received inventory event [{}] for orderId={}", event.getEventType(), event.getOrderId());

        switch (event.getEventType()) {
            case "INVENTORY_RESERVED" -> orderService.handleInventoryReserved(event.getOrderId());
            case "INVENTORY_FAILED"   -> orderService.handleInventoryFailed(event.getOrderId(), event.getReason());
            default -> log.warn("Unknown inventory event type: {}", event.getEventType());
        }
    }

    @DltHandler
    public void handleInventoryDlt(InventoryEvent event) {
        log.error("Inventory event sent to DLT — orderId={} eventType={}",
                event.getOrderId(), event.getEventType());
    }
}
