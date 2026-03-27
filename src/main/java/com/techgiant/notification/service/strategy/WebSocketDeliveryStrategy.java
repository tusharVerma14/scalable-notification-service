package com.techgiant.notification.service.strategy;

import com.techgiant.notification.model.Notification;
import com.techgiant.notification.model.NotificationStatus;
import com.techgiant.notification.repository.NotificationRepository;
import com.techgiant.notification.service.SessionManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketDeliveryStrategy implements DeliveryStrategy {

    private final SimpMessagingTemplate messagingTemplate;
    private final SessionManagementService sessionManagementService;
    private final NotificationRepository notificationRepository;

    @Override
    public String getChannelType() {
        return "WEBSOCKET";
    }

    @Override
    public void deliver(Notification notification) {
        String userId = notification.getTargetUserId();
        Set<String> activeSessions = sessionManagementService.getActiveSessions(userId);

        if (activeSessions == null || activeSessions.isEmpty()) {
            // user is offline — save to DB so we can push on reconnect
            log.info("User '{}' is offline. Saving notification as PENDING.", userId);
            notification.setStatus(NotificationStatus.PENDING);
            notification.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(notification);
            return;
        }

        try {
            log.info("User '{}' is online. Pushing via WebSocket.", userId);
            messagingTemplate.convertAndSend(
                    "/topic/notifications/" + userId,
                    notification
            );
            notification.setStatus(NotificationStatus.DELIVERED);
            notification.setDeliveredAt(LocalDateTime.now());
            notificationRepository.save(notification);

        } catch (Exception e) {
            // push failed — mark as PENDING so it can be retried on next reconnect
            log.error("WebSocket push failed for user '{}': {}", userId, e.getMessage());
            notification.setStatus(NotificationStatus.PENDING);
            notification.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
    }
}
