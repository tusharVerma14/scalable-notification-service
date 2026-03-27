package com.techgiant.notification.controller;

import com.techgiant.notification.service.PendingNotificationService;
import com.techgiant.notification.service.SessionManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final SessionManagementService sessionManagementService;
    private final PendingNotificationService pendingNotificationService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String userId = getUserIdFromHeaders(headerAccessor);

        if (userId != null) {
            log.info("User '{}' connected with session '{}'", userId, sessionId);
            sessionManagementService.addSession(userId, sessionId);
            deliverPendingAsync(userId);
        }
    }

    // Async so the CONNECT handshake completes before we push pending notifications.
    // 500ms delay gives the browser time to subscribe to the topic first.
    @Async
    public void deliverPendingAsync(String userId) {
        try {
            Thread.sleep(500);
            pendingNotificationService.deliverPendingNotifications(userId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String userId = getUserIdFromHeaders(headerAccessor);

        if (userId != null) {
            log.info("User '{}' disconnected. Session '{}'", userId, sessionId);
            sessionManagementService.removeSession(userId, sessionId);
        }
    }

    private String getUserIdFromHeaders(StompHeaderAccessor accessor) {
        List<String> userIds = accessor.getNativeHeader("userId");
        if (userIds != null && !userIds.isEmpty()) {
            return userIds.get(0);
        }
        return null;
    }
}
