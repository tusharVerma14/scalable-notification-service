package com.techgiant.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private String targetUserId;
    private String title;
    private String body;
    
    // Future proofing: "WEBSOCKET", "EMAIL", "SMS"
    private List<String> channels;
    
    // RabbitMQ tracking
    private int retryCount;
}
