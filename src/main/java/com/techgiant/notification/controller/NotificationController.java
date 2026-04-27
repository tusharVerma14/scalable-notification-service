package com.techgiant.notification.controller;

import com.techgiant.notification.dto.NotificationDTO;
import com.techgiant.notification.dto.AiNotificationRequestDTO;
import com.techgiant.notification.service.AiNotificationService;
import com.techgiant.notification.service.NotificationProducer;
import com.techgiant.notification.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notify")
@RequiredArgsConstructor
public class NotificationController {

    private final RateLimiterService rateLimiterService;
    private final NotificationProducer notificationProducer;
    private final AiNotificationService aiNotificationService;

    @PostMapping
    public ResponseEntity<?> sendNotification(
            @RequestHeader(value = "X-API-KEY", defaultValue = "default_restaurant_key") String apiKey,
            @RequestBody NotificationDTO notificationRequest) {

        // check rate limit before doing anything
        if (!rateLimiterService.isAllowed(apiKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit exceeded. Please wait."));
        }

        // drop onto queue and return immediately — delivery happens async
        notificationProducer.queueNotification(notificationRequest);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Notification queued for instant delivery."
        ));
    }

    @PostMapping("/ai-enhance")
    public ResponseEntity<?> sendAiEnhancedNotification(
            @RequestHeader(value = "X-API-KEY", defaultValue = "default_restaurant_key") String apiKey,
            @RequestBody AiNotificationRequestDTO aiNotificationRequest) {

        // check rate limit before doing anything
        if (!rateLimiterService.isAllowed(apiKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit exceeded. Please wait."));
        }

        // Generate AI content and queue async
        aiNotificationService.generateAndQueueNotification(aiNotificationRequest);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "AI notification generation queued for delivery."
        ));
    }
}
