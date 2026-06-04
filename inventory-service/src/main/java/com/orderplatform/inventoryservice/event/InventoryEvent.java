package com.orderplatform.inventoryservice.event;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InventoryEvent {
    private String eventId;
    private String eventType;   // INVENTORY_RESERVED | INVENTORY_FAILED | INVENTORY_RELEASED
    private String orderId;
    private String reason;
    private String timestamp;
}
