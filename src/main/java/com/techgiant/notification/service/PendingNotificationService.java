package com.techgiant.notification.service;

import com.techgiant.notification.model.Notification;
import com.techgiant.notification.model.NotificationStatus;
import com.techgiant.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PendingNotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // Called when a user reconnects. Finds all undelivered notifications and pushes them.
    @Transactional
    public void deliverPendingNotifications(String userId) {
        List<Notification> pending = notificationRepository
                .findByTargetUserIdAndStatus(userId, NotificationStatus.PENDING);

        if (pending.isEmpty()) {
            log.info("No pending notifications for user '{}'", userId);
            return;
        }

        log.info("User '{}' reconnected. Delivering {} pending notification(s).", userId, pending.size());

        int delivered = 0;
        int failed = 0;

        for (Notification notification : pending) {
            try {
                messagingTemplate.convertAndSend(
                        "/topic/notifications/" + userId,
                        notification
                );

                notification.setStatus(NotificationStatus.DELIVERED);
                notification.setDeliveredAt(LocalDateTime.now());
                notificationRepository.save(notification);
                delivered++;

            } catch (Exception e) {
                // don't stop — deliver the rest even if one fails
                // notification stays PENDING and will retry on next reconnect
                failed++;
                log.error("Failed to deliver notification id={} to user '{}': {}",
                        notification.getId(), userId, e.getMessage());
            }
        }

        log.info("Pending delivery done for user '{}': {} delivered, {} failed.", userId, delivered, failed);
    }
}
