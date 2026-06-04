package com.orderplatform.paymentservice.event;

import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentEvent {
    private String eventId;
    private String eventType;   // PAYMENT_PROCESSED | PAYMENT_FAILED
    private String orderId;
    private String paymentId;
    private BigDecimal amount;
    private String reason;
    private String timestamp;
}
