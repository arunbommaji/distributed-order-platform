package com.orderplatform.inventoryservice.event;

import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OrderItemDto {
    private String productId;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;
}
