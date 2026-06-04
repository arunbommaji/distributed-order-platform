package com.orderplatform.orderservice.kafka;

import com.orderplatform.orderservice.event.PaymentEvent;
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
public class PaymentEventConsumer {

    private final OrderService orderService;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(
            topics = "${kafka.topic.payment-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "paymentKafkaListenerContainerFactory"
    )
    public void handlePaymentEvent(PaymentEvent event) {
        log.info("Received payment event [{}] for orderId={}", event.getEventType(), event.getOrderId());

        switch (event.getEventType()) {
            case "PAYMENT_PROCESSED" -> orderService.handlePaymentProcessed(event.getOrderId());
            case "PAYMENT_FAILED"    -> orderService.handlePaymentFailed(event.getOrderId(), event.getReason());
            default -> log.warn("Unknown payment event type: {}", event.getEventType());
        }
    }

    @DltHandler
    public void handlePaymentDlt(PaymentEvent event) {
        log.error("Payment event sent to DLT — orderId={} eventType={} reason={}",
                event.getOrderId(), event.getEventType(), event.getReason());
        // TODO: alert on-call, store in dead_letter_log table
    }
}
