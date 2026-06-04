package com.orderplatform.notificationservice.event;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InventoryEvent {
    private String eventId;
    private String eventType;
    private String orderId;
    private String reason;
    private String timestamp;
}
