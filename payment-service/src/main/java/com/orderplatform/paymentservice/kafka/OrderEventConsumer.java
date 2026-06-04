package com.orderplatform.paymentservice.kafka;

import com.orderplatform.paymentservice.event.OrderEvent;
import com.orderplatform.paymentservice.service.PaymentService;
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

    private final PaymentService paymentService;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt",
            include = Exception.class
    )
    @KafkaListener(
            topics = "${kafka.topic.order-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "orderKafkaListenerContainerFactory"
    )
    public void handleOrderEvent(OrderEvent event) {
        if (!"ORDER_CREATED".equals(event.getEventType())) {
            log.debug("Ignoring event type {} in payment service", event.getEventType());
            return;
        }
        log.info("Processing ORDER_CREATED for orderId={} amount={}", event.getOrderId(), event.getTotalAmount());
        paymentService.processPayment(event);
    }

    @DltHandler
    public void handleDlt(OrderEvent event) {
        log.error("ORDER event in DLT — orderId={} type={}", event.getOrderId(), event.getEventType());
    }
}
