package com.techgiant.notification.repository;

import com.techgiant.notification.model.Notification;
import com.techgiant.notification.model.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // Find all un-delivered notifications for a user so we can push them when they reconnect
    List<Notification> findByTargetUserIdAndStatus(String targetUserId, NotificationStatus status);
}
