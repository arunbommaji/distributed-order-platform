package com.orderplatform.orderservice.kafka;

import com.orderplatform.orderservice.event.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.order-events}")
    private String orderEventsTopic;

    public void publishOrderEvent(OrderEvent event) {
        log.info("Publishing [{}] for orderId={}", event.getEventType(), event.getOrderId());
        kafkaTemplate.send(orderEventsTopic, event.getOrderId(), event)
                .thenAccept(result -> log.debug("Published to partition={} offset={}",
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()))
                .exceptionally(ex -> {
                    log.error("FAILED to publish [{}] for orderId={}", event.getEventType(), event.getOrderId(), ex);
                    return null;
                });
    }
}
