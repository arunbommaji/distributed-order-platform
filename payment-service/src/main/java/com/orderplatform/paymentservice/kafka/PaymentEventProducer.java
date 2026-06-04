package com.orderplatform.paymentservice.kafka;

import com.orderplatform.paymentservice.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.payment-events}")
    private String topic;

    public void publish(PaymentEvent event) {
        log.info("Publishing [{}] for orderId={}", event.getEventType(), event.getOrderId());
        kafkaTemplate.send(topic, event.getOrderId(), event)
                .exceptionally(ex -> {
                    log.error("Failed to publish payment event for orderId={}", event.getOrderId(), ex);
                    return null;
                });
    }
}
