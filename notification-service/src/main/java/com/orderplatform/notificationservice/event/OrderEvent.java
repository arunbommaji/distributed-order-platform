package com.orderplatform.notificationservice.event;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OrderEvent {
    private String eventId;
    private String eventType;
    private String orderId;
    private String customerId;
    private String customerEmail;
    private List<OrderItemDto> items;
    private BigDecimal totalAmount;
    private String reason;
    private String timestamp;
}
