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
public class AiNotificationRequestDTO {
    private String targetUserId;
    private String event;
    private String context;
    
    // Optional channel overrides, e.g., ["WEBSOCKET", "EMAIL"]
    private List<String> channels;
}
