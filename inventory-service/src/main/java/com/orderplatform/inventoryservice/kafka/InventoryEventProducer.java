package com.orderplatform.inventoryservice.kafka;

import com.orderplatform.inventoryservice.event.InventoryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.inventory-events}")
    private String topic;

    public void publish(InventoryEvent event) {
        log.info("Publishing [{}] for orderId={}", event.getEventType(), event.getOrderId());
        kafkaTemplate.send(topic, event.getOrderId(), event)
                .exceptionally(ex -> {
                    log.error("Failed to publish inventory event for orderId={}", event.getOrderId(), ex);
                    return null;
                });
    }
}
