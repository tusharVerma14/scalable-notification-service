package com.techgiant.notification.service;

import com.techgiant.notification.config.RabbitMQConfig;
import com.techgiant.notification.dto.NotificationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationProducer {

    private final RabbitTemplate rabbitTemplate;

    public void queueNotification(NotificationDTO notificationDTO) {
        log.info("Queuing notification for user: {}", notificationDTO.getTargetUserId());
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    RabbitMQConfig.ROUTING_KEY,
                    notificationDTO
            );
        } catch (Exception e) {
            // fail open — log and move on, don't surface RabbitMQ errors to the HTTP caller
            log.warn("RabbitMQ unavailable for user '{}': {}", notificationDTO.getTargetUserId(), e.getMessage());
        }
    }
}
