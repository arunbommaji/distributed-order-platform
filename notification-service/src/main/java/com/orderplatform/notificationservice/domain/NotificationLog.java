package com.orderplatform.notificationservice.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false)
    private String notificationType;   // ORDER_CONFIRMED | ORDER_CANCELLED | PAYMENT_FAILED etc.

    @Column(nullable = false)
    private String channel;            // EMAIL | SMS

    @Column(nullable = false)
    private String recipient;

    @Column(length = 2000)
    private String message;

    @Column(nullable = false)
    private String status;             // SENT | FAILED

    @Column(nullable = false)
    private LocalDateTime sentAt;
}
